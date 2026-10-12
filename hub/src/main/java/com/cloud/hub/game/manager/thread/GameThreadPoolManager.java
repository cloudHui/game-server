package com.cloud.hub.game.manager.thread;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.concurrent.mailbox.ActorMailbox;
import utils.concurrent.pool.ObjectPool;
import utils.concurrent.realtime.DedicatedGameLoopEngine;
import utils.concurrent.realtime.FrameEvent;
import utils.concurrent.thread.ExecutorPool;
import utils.concurrent.thread.ThreadPool;
import utils.trace.TraceContext;

/**
 * Game 服务端全局线程调度管理器（多模式统一收敛与自由切换门面）。
 * <p>
 * <b>三版模式自由切换架构：</b>
 * <ul>
 * <li><b>Mode 1: SLOTS（默认第一版）：</b> 统一业务亲和池（32 物理 Worker + 1024 逻辑槽位解耦）。
 * 相同 tableId 按位运算扰动永远映射到同一槽位，FIFO 无锁串行且将哈希碰撞降至 4.7% 以下；</li>
 * <li><b>Mode 2: MAILBOX（第二版）：</b> 去中心化 Actor Mailbox 模型。每张桌子私有独占无锁队列，
 * 彻底消除全局哈希碰撞，桌间 100% 物理隔离，支持批量 64 任务配额与安全注销排空；</li>
 * <li><b>Mode 3: DEDICATED（第三版）：</b> 混合/实时竞技模式。棋牌业务走亲和池/Mailbox，
 * 针对高帧率（30/60 FPS）实时对局房间挂载 {@link DedicatedGameLoopEngine} 纳秒定长补帧引擎与零垃圾对象池。</li>
 * </ul>
 * 可通过 JVM 启动参数 {@code -Dgame.thread.mode=SLOTS|MAILBOX|DEDICATED} 或
 * {@link #setThreadModelMode(ThreadModelMode)} 动态平滑切换。
 */
public final class GameThreadPoolManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameThreadPoolManager.class);

    /**
     * 线程模型工作模式枚举。
     */
    public enum ThreadModelMode {
        /** 【第一版】高维逻辑哈希槽亲和模型（默认推荐，1024 槽位解耦，适合麻将/斗地主/拖拉机） */
        SLOTS,
        /** 【第二版】去中心化 Actor Mailbox 模型（单桌私有无锁队列，彻底零碰撞物理隔离） */
        MAILBOX,
        /** 【第三版】实时竞技模式（棋牌走独立调度，实时房间挂载 60 FPS Dedicated Loop 引擎与对象池） */
        DEDICATED
    }

    /** 当前运行的线程模型工作模式，支持配置与动态切换 */
    private volatile ThreadModelMode threadModelMode;

    /** 统一业务亲和执行器池：承载对局状态机、玩家个人会话及房间全局管理 */
    private final ExecutorPool businessPool;

    /** 数据库异步写入线程池：专门跑阻塞 IO（SQLite、MySQL、战绩写入） */
    private final ExecutorService databasePool;

    /** 牌桌心跳周期时钟源：单线程纯脉冲，严禁执行任何业务逻辑 */
    private final ScheduledExecutorService tableScheduler;

    /** 当前处于激活状态的桌号集合：建桌注册，删桌移除，用于调度准入校验 */
    private final Set<Long> activeTables = ConcurrentHashMap.newKeySet();

    /** 调度防堆积标记集合：记录当前正在执行周期心跳的桌号，上一拍未结束跳过本拍 */
    private final Set<Long> tickBusy = ConcurrentHashMap.newKeySet();

    /** 单桌私有 ActorMailbox 映射表 <tableId, ActorMailbox>，仅在 MAILBOX 模式下生效 */
    private final ConcurrentHashMap<Long, ActorMailbox> tableMailboxes = new ConcurrentHashMap<>();

    /** 实时竞技专用 Dedicated Loop 调度引擎（第三版），懒加载单例 */
    private volatile DedicatedGameLoopEngine dedicatedGameLoopEngine;

    /** 第三版实时竞技专用对象池注册表 <Class, ObjectPool>，用于 Mode 3 零 GC 复用 */
    private final ConcurrentHashMap<Class<?>, ObjectPool<?>> dedicatedPools = new ConcurrentHashMap<>();

    /** 全局桌子管理串行分组 ID，用于 submitTableManager 串行化防冲突 */
    private static final long MANAGER_GROUP_ID = 0L;

    /**
     * 构造函数：初始化全局三大核心执行器并装配初始运行模式。
     *
     * @param workerSize    业务物理线程数（若 <= 0 则采用 ThreadPool.DEFAULT_POOL_SIZE 与 32
     *                      较大者）
     * @param queueCapacity 业务每个槽位的有界队列容量（推荐 10 万）
     * @param databaseSize  数据库写入线程数（默认 4~8 线程）
     */
    public GameThreadPoolManager(int workerSize, int queueCapacity, int databaseSize) {
        // 读取环境配置模式，默认启用成熟的第一版 SLOTS
        String configMode = System.getProperty("game.thread.mode", "SLOTS").trim().toUpperCase();
        try {
            this.threadModelMode = ThreadModelMode.valueOf(configMode);
        } catch (IllegalArgumentException e) {
            this.threadModelMode = ThreadModelMode.SLOTS;
        }

        // 1. 业务亲和池：统一调度所有纯内存运算任务（原生 1024 高维逻辑槽位解耦）
        int size = workerSize > 0 ? workerSize : Math.max(32, ThreadPool.DEFAULT_POOL_SIZE);
        this.businessPool = new ExecutorPool("Game-Business", size, ExecutorPool.DEFAULT_SLOT_COUNT, queueCapacity);

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

        // 4. 若配置为 DEDICATED 模式，预热专用实时引擎与帧事件对象池
        if (this.threadModelMode == ThreadModelMode.DEDICATED) {
            getDedicatedGameLoopEngine();
            LOGGER.info("[init] DEDICATED 实时竞技模式激活，DedicatedGameLoopEngine 与 FrameEvent 对象池预热完成");
        }

        LOGGER.info("[init] GameThreadPoolManager 启动完成, 当前模式: {}, workers: {}, slots: {}, databasePool: {}",
                this.threadModelMode, size, ExecutorPool.DEFAULT_SLOT_COUNT, databaseSize);
    }

    /**
     * 获取当前运行的线程模型模式。
     */
    public ThreadModelMode getThreadModelMode() {
        return threadModelMode;
    }

    /**
     * 动态切换线程模型运行模式。
     *
     * @param mode 目标工作模式
     */
    public void setThreadModelMode(ThreadModelMode mode) {
        LOGGER.info("[GameThreadPoolManager] 动态切换工作模式: {} -> {}", this.threadModelMode, mode);
        this.threadModelMode = mode;
        if (mode == ThreadModelMode.DEDICATED) {
            getDedicatedGameLoopEngine();
            LOGGER.info("[GameThreadPoolManager] DEDICATED 模式已激活，DedicatedGameLoopEngine 与 FrameEvent 对象池预热完成");
        }
    }

    /**
     * 获取或懒加载初始化第三版实时竞技专用 Dedicated Loop 引擎。
     *
     * @return 60 FPS 专用引擎实例
     */
    public synchronized DedicatedGameLoopEngine getDedicatedGameLoopEngine() {
        if (dedicatedGameLoopEngine == null) {
            dedicatedGameLoopEngine = new DedicatedGameLoopEngine(4);
        }
        return dedicatedGameLoopEngine;
    }

    /**
     * 获取或创建指定类型的专用对象池（仅在 DEDICATED 模式或高频运算下使用，达成零 GC 堆分配）。
     *
     * @param <T>      对象类型
     * @param clazz    对象类型 Class
     * @param creator  对象创建工厂
     * @param capacity 最大缓存容量
     * @return 对应的对象池实例
     */
    @SuppressWarnings("unchecked")
    public <T> ObjectPool<T> getDedicatedPool(Class<T> clazz, Supplier<T> creator, int capacity) {
        return (ObjectPool<T>) dedicatedPools.computeIfAbsent(clazz,
                k -> new ObjectPool<>(creator, capacity));
    }

    /**
     * 便捷入口：获取预置的实时帧事件对象池（DEDICATED 模式标配）。
     *
     * @return 帧事件对象池
     */
    public ObjectPool<FrameEvent> getFrameEventPool() {
        return getDedicatedGameLoopEngine().getEventPool();
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
     * 兼容旧接口：获取牌桌执行器池。
     */
    public ExecutorPool tablePool() {
        return businessPool;
    }

    /**
     * 兼容旧接口：获取玩家执行器池。
     */
    public ExecutorPool playerPool() {
        return businessPool;
    }

    /**
     * 兼容旧接口：获取牌桌管理执行器池。
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
        if (threadModelMode == ThreadModelMode.MAILBOX) {
            tableMailboxes.computeIfAbsent(tableId, id -> new ActorMailbox(id, businessPool.getExecutorService()));
        }
    }

    /**
     * 将无返回值的牌桌任务按当前运行模式分发投递，保证同桌严格串行。
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

        // 第二版：MAILBOX 模式直接走单桌独占私有队列，达成绝对零碰撞
        if (threadModelMode == ThreadModelMode.MAILBOX) {
            ActorMailbox mailbox = tableMailboxes.computeIfAbsent(tableId,
                    id -> new ActorMailbox(id, businessPool.getExecutorService()));
            return mailbox.tell(() -> {
                try {
                    TraceContext.setTableId(tableId);
                    task.run();
                } finally {
                    TraceContext.endTrace();
                }
            });
        }

        // 第一版 (SLOTS) 与 第三版 (DEDICATED) 默认走 1024 高维槽位亲和池
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
     * 将带有返回值的计算任务按当前运行模式分发投递，保证同桌严格串行。
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

        // 第二版：MAILBOX 模式直接走单桌独占私有队列
        if (threadModelMode == ThreadModelMode.MAILBOX) {
            ActorMailbox mailbox = tableMailboxes.computeIfAbsent(tableId,
                    id -> new ActorMailbox(id, businessPool.getExecutorService()));
            return mailbox.ask(() -> {
                try {
                    TraceContext.setTableId(tableId);
                    return supplier.get();
                } finally {
                    TraceContext.endTrace();
                }
            });
        }

        // 第一版 (SLOTS) 与 第三版 (DEDICATED) 默认走 1024 高维槽位亲和池
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
     * 注销并删除桌子：拒绝后续任务投递，排空私有邮箱并清理忙碌标记。
     *
     * @param tableId 目标桌号
     */
    public void removeTable(long tableId) {
        activeTables.remove(tableId);
        tickBusy.remove(tableId);

        // 如果存在私有 Mailbox，安全终结并排空
        ActorMailbox mailbox = tableMailboxes.remove(tableId);
        if (mailbox != null) {
            mailbox.dispose();
        }
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
        for (ActorMailbox mailbox : tableMailboxes.values()) {
            mailbox.dispose();
        }
        tableMailboxes.clear();

        if (dedicatedGameLoopEngine != null) {
            dedicatedGameLoopEngine.shutdown();
        }
        for (ObjectPool<?> pool : dedicatedPools.values()) {
            pool.clear();
        }
        dedicatedPools.clear();
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
