package com.cloud.hub.game.manager.thread;

import threadtutil.thread.ExecutorPool;
import threadtutil.utils.TimeUtils;
import utils.trace.TraceContext;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Game 服务端全局线程调度管理器（统一收敛架构）。
 * <p>
 * <b>线程池架构收敛演进说明：</b>
 * 系统收敛为 3 个核心执行器，兼顾吞吐量、CPU 利用率与系统稳定性：
 * <ol>
 * <li><b>业务亲和执行池 ({@code businessPool})：</b> 统一整合原有的牌桌 (Table)、玩家会话 (Player) 与牌桌管理 (TableManager)。
 * 基于底层哈希槽位 (Hash Slots) 与 Mailbox 机制，相同 {@code tableId} 或 {@code userId} 天然串行无锁，
 * 避免原先多池导致 60+ 线程空耗与高频上下文切换开销；</li>
 * <li><b>数据库落盘池 ({@code databasePool})：</b> 物理隔离的阻塞 IO 专用线程池，专门处理 SQLite / MySQL 异步落盘，
 * 绝不允许慢查询或磁盘 IO 阻塞业务 Worker 线程；</li>
 * <li><b>定时脉冲源 ({@code tableScheduler})：</b> 物理隔离的单线程调度器，仅负责产生微秒级准时的周期心跳脉冲，
 * 产生后立即投回业务队列，保证全服牌桌倒计时与超时判定永不漂移。</li>
 * </ol>
 */
public final class GameThreadPoolManager {

    /**
     * 统一业务亲和执行器池：承载对局状态机、玩家个人会话及房间全局管理。
     */
    private final ExecutorPool businessPool;

    /**
     * 数据库异步写入线程池：专门跑阻塞 IO（SQLite、MySQL、战绩写入）。
     */
    private final ExecutorService databasePool;

    /**
     * 牌桌心跳周期时钟源：单线程纯脉冲，严禁执行任何业务逻辑。
     */
    private final ScheduledExecutorService tableScheduler;

    /**
     * 当前处于激活状态的桌号集合：建桌注册，删桌移除，用于调度准入校验。
     */
    private final Set<Long> activeTables = ConcurrentHashMap.newKeySet();

    /**
     * 调度防堆积标记集合：记录当前正在执行周期心跳的桌号，上一拍未结束跳过本拍。
     */
    private final Set<Long> tickBusy = ConcurrentHashMap.newKeySet();

    /**
     * 全局桌子管理串行分组 ID，用于 submitTableManager 串行化防冲突。
     */
    private static final long MANAGER_GROUP_ID = 0L;

    /**
     * 构造函数：初始化全局三大核心执行器。
     *
     * @param workerSize    业务物理线程数（若 <= 0 则默认采用 CPU 核心数与 32 的较大者）
     * @param queueCapacity 业务每个槽位的有界队列容量（推荐 10 万）
     * @param databaseSize  数据库写入线程数（默认 4~8 线程）
     */
    public GameThreadPoolManager(int workerSize, int queueCapacity, int databaseSize) {
        // 1. 业务亲和池：统一调度所有纯内存运算任务
        int size = workerSize > 0 ? workerSize : Math.max(32, TimeUtils.PROCESS_NUMBER);
        this.businessPool = new ExecutorPool("Game-Business", size, queueCapacity);

        // 2. 数据库阻塞池：独立线程池，防止慢 SQL 拖慢主业务
        this.databasePool = Executors.newFixedThreadPool(Math.max(1, databaseSize), r -> {
            Thread t = new Thread(r, "Game-Database");
            t.setDaemon(false);
            return t;
        });

        // 3. 定时时钟源：单线程守护时钟，保证心跳节奏绝对精准
        this.tableScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Game-TableScheduler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 获取统一业务亲和执行器池。
     *
     * @return 业务执行器池实例
     */
    public ExecutorPool businessPool() {
        return businessPool;
    }

    /**
     * 兼容旧接口：获取牌桌执行器池（现已统一路由至 businessPool）。
     *
     * @return 业务执行器池实例
     */
    public ExecutorPool tablePool() {
        return businessPool;
    }

    /**
     * 兼容旧接口：获取玩家执行器池（现已统一路由至 businessPool）。
     *
     * @return 业务执行器池实例
     */
    public ExecutorPool playerPool() {
        return businessPool;
    }

    /**
     * 兼容旧接口：获取牌桌管理执行器池（现已统一路由至 businessPool）。
     *
     * @return 业务执行器池实例
     */
    public ExecutorPool tableManagerPool() {
        return businessPool;
    }

    /**
     * 获取数据库异步线程池。
     *
     * @return 数据库线程池实例
     */
    public ExecutorService databasePool() {
        return databasePool;
    }

    /**
     * 注册激活桌子：建桌时登记，允许后续投递任务与定时心跳。
     *
     * @param tableId 牌桌唯一标识
     */
    public void registerTable(long tableId) {
        activeTables.add(tableId);
    }

    /**
     * 将无返回值的牌桌任务按 tableId 亲和投递，保证同桌严格串行。
     *
     * @param tableId 目标桌号
     * @param task    具体业务逻辑
     * @return 异步执行凭据
     */
    public CompletableFuture<Void> submitTable(long tableId, Runnable task) {
        if (!activeTables.contains(tableId)) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("桌子执行器不存在: " + tableId));
            return future;
        }
        // 在执行前后自动装配与清理全链路 Trace 上下文
        return businessPool.serialExecute(tableId, () -> {
            try {
                TraceContext.setTableId(tableId);
                task.run();
            } finally {
                TraceContext.endTrace();
            }
        });
    }

    /**
     * 将带有返回值的计算任务按 tableId 亲和投递，保证同桌严格串行。
     *
     * @param <T>      计算结果类型
     * @param tableId  目标桌号
     * @param supplier 具有返回值的计算逻辑
     * @return 带有计算结果的异步凭据
     */
    public <T> CompletableFuture<T> submitTable(long tableId, Supplier<T> supplier) {
        if (!activeTables.contains(tableId)) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("桌子执行器不存在: " + tableId));
            return future;
        }
        // 在执行前后自动装配与清理全链路 Trace 上下文
        return businessPool.serialExecute(tableId, () -> {
            try {
                TraceContext.setTableId(tableId);
                return supplier.get();
            } finally {
                TraceContext.endTrace();
            }
        });
    }

    /**
     * 注册并启动牌桌周期心跳调度。
     * <p>
     * 调度线程只负责定时发出脉冲，真正执行状态逻辑仍投回业务串行队列；
     * 若上一拍仍在队列积压或执行中，则自动跳过本拍，杜绝雪崩堆积。
     *
     * @param tableId    目标桌号
     * @param task       周期心跳执行体
     * @param delayMs    初次延迟时间（毫秒）
     * @param intervalMs 周期心跳间隔（毫秒）
     * @return 定时任务句柄，用于停止心跳
     */
    public ScheduledFuture<?> scheduleTable(long tableId, Runnable task, long delayMs, long intervalMs) {
        registerTable(tableId);
        return tableScheduler.scheduleAtFixedRate(
                () -> dispatchTick(tableId, task), delayMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 取消牌桌心跳调度。
     *
     * @param future 定时任务句柄
     */
    public void cancelTableSchedule(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    /**
     * 注销并删除桌子：拒绝后续任务投递，并清理忙碌标记。
     *
     * @param tableId 目标桌号
     */
    public void removeTable(long tableId) {
        activeTables.remove(tableId);
        tickBusy.remove(tableId);
    }

    /**
     * 桌子全局生命周期与房间索引操作（如建桌、解散、跨桌匹配）。
     * <p>
     * 通过固定分组 ID（{@code MANAGER_GROUP_ID}）投递至业务亲和池，
     * 保证全局管理任务严格单线程串行执行，无需加锁，亦无需独立开辟物理线程池。
     *
     * @param <T>  返回结果类型
     * @param task 待执行的管理任务
     * @return 异步执行凭据
     */
    public <T> CompletableFuture<T> submitTableManager(Callable<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        businessPool.serialExecute(MANAGER_GROUP_ID, () -> runManagerTask(task, future));
        return future;
    }

    /**
     * 优雅关闭所有执行器与线程池。
     */
    public void shutdown() {
        activeTables.clear();
        tickBusy.clear();
        tableScheduler.shutdownNow();
        businessPool.getExecutorService().shutdownNow();
        databasePool.shutdownNow();
    }

    /**
     * 心跳脉冲分发器：执行防堆积检查并向业务队列投递。
     *
     * @param tableId 目标桌号
     * @param task    心跳任务体
     */
    private void dispatchTick(long tableId, Runnable task) {
        if (!activeTables.contains(tableId)) {
            return;
        }
        // 原子标记防堆积：若上一拍尚未执行完毕，则放弃本拍脉冲
        if (!tickBusy.add(tableId)) {
            return;
        }
        submitTable(tableId, () -> {
            try {
                task.run();
            } finally {
                // 本拍执行完毕，释放忙碌标记
                tickBusy.remove(tableId);
            }
        }).exceptionally(error -> {
            tickBusy.remove(tableId);
            return null;
        });
    }

    private <T> void runManagerTask(Callable<T> task, CompletableFuture<T> future) {
        try {
            future.complete(task.call());
        } catch (Throwable error) {
            future.completeExceptionally(error);
        }
    }
}
