package utils.concurrent.thread;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import utils.concurrent.thread.model.TaskList;

/**
 * 亲和串行化执行器池（基于逻辑哈希槽解耦与工作窃取的无锁并发调度引擎）。
 * <p>
 * <b>核心设计与工作原理：</b>
 * <ul>
 * <li><b>外部单一入口：</b> 外部业务方只需调用 {@link #serialExecute(long, Runnable)} 或
 * {@link #serialExecute(int, Runnable)}，
 * 传入业务唯一分组 ID（如 tableId、userId）和具体逻辑，无需关心内部如何包装分拣；</li>
 * <li><b>逻辑槽位与物理工人解耦：</b> 物理 Worker 线程数保持在多核友好的规模（如 32 线程），
 * 逻辑任务槽位支持高维配置（默认 1024 槽），将哈希碰撞率降至极低，彻底杜绝队头阻塞；</li>
 * <li><b>掩码级位运算路由：</b> 槽位数强制对齐为 2 的次幂，采用高低位异或扰动结合掩码寻址，纳秒级路由且消灭负数越界；</li>
 * <li><b>单槽排他性无锁消费：</b> 每个槽位在任意时刻只允许一个 Worker 物理线程持有处理权消费，
 * 业务逻辑在执行期间无需在 Java 层面加任何 {@code synchronized} 或重量级锁，实现零死锁、高性能；</li>
 * <li><b>随机偏置工作窃取（Work-Stealing）：</b> 当 Worker 线程清空自己负责的槽位后，
 * 以随机偏置起点探测空闲槽位（带界限探测），防止多个 Worker 集群锁踩踏，最大化压榨 CPU 多核吞吐量；</li>
 * <li><b>超时监控与告警：</b> 内置执行耗时监控，如果单槽位执行时间超过阈值，自动打印告警与线程调用栈，快速排查死循环或慢查询。</li>
 * </ul>
 */
public class ExecutorPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorPool.class);

    /** 默认逻辑槽位数量（必须为 2 的整数次幂） */
    public static final int DEFAULT_SLOT_COUNT = 1024;
    /** 底层物理线程池管理器 */
    private final ThreadPool threadPool;
    /** 高维逻辑槽位数组：每个槽位是一个独立的串行任务队列 */
    private final TaskList[] taskLists;
    /** 槽位哈希掩码（slotCount - 1），用于纳秒级位运算寻址 */
    private final int slotMask;

    /** 单槽处理耗时警告阈值（毫秒） */
    private static final long PROCESS_TIMEOUT_MS = 5000L;
    /** 超时打印调用栈阈值（毫秒），超过一分钟打印现场堆栈 */
    private static final long STACK_TRACE_TIMEOUT_MS = 60000L;
    /** 默认有界队列容量 */
    private static final int INIT_SIZE = 1000;

    public ExecutorPool(String executorName) {
        this(executorName, 0);
    }

    public ExecutorPool(String executorName, int size) {
        this(executorName, size, INIT_SIZE);
    }

    public ExecutorPool(String executorName, int size, int queueCapacity) {
        // 单线程串行执行器只需要 1 个槽位，多线程默认启用 1024 逻辑槽位防碰撞
        this(executorName, size, (size == 1) ? 1 : DEFAULT_SLOT_COUNT, queueCapacity);
    }

    /**
     * 完整构造函数：支持物理 Worker 规模与逻辑槽位规模全面解耦配置。
     *
     * @param executorName  执行器名称标识
     * @param size          底层物理 Worker 线程数（<=0 则采用 ThreadPool.DEFAULT_POOL_SIZE）
     * @param slotCount     逻辑槽位数（自动对齐至最近的 2 的幂次方）
     * @param queueCapacity 线程池全局派工缓冲队列容量
     */
    public ExecutorPool(String executorName, int size, int slotCount, int queueCapacity) {
        int poolSize = (size < 1) ? ThreadPool.DEFAULT_POOL_SIZE : size;
        this.threadPool = new ThreadPool(executorName, poolSize, queueCapacity);

        // 规整 slotCount 为大于等于指定值的最小 2 的次幂，便于位运算寻址
        int normalizedSlots = tableSizeFor(slotCount);
        this.taskLists = new TaskList[normalizedSlots];
        for (int i = 0; i < normalizedSlots; ++i) {
            this.taskLists[i] = new TaskList();
        }
        this.slotMask = normalizedSlots - 1;

        LOGGER.info("[init] ExecutorPool name:{}, workers:{}, slots:{}, queueCapacity:{}",
                executorName, poolSize, normalizedSlots, queueCapacity);
    }

    /**
     * 将任意整数向上对齐至最近的 2 的次幂（类似 HashMap 机制）。
     */
    private static int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= (1 << 30)) ? (1 << 30) : n + 1;
    }

    // =========================================================================
    // 第一部分：统一的外部调用入口（门面层）
    // =========================================================================

    /**
     * 【主入口 1】以 long 类型的 groupId 投递串行任务。
     *
     * @param groupId  业务分组 ID（如 tableId、userId）
     * @param runnable 待执行的业务逻辑
     * @return 异步执行凭据
     */
    public CompletableFuture<Void> serialExecute(long groupId, Runnable runnable) {
        return serialExecute(Long.hashCode(groupId), runnable);
    }

    /**
     * 【主入口 1 泛型版】以 long 类型的 groupId 投递带有返回值的串行计算任务。
     */
    public <T> CompletableFuture<T> serialExecute(long groupId, Supplier<T> supplier) {
        return serialExecute(Long.hashCode(groupId), supplier);
    }

    /**
     * 【主入口 2】以 int 类型的 groupId 投递串行任务。
     */
    public CompletableFuture<Void> serialExecute(int groupId, Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        TaskNode taskNode = new TaskNode(groupId, runnable, future);
        dispatchToQueue(calculateSlotIndex(groupId), taskNode);
        return future;
    }

    /**
     * 【主入口 2 泛型版】以 int 类型的 groupId 投递带有返回值的串行计算任务。
     */
    public <T> CompletableFuture<T> serialExecute(int groupId, Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        TaskNode taskNode = new TaskNode(groupId, supplier, future);
        dispatchToQueue(calculateSlotIndex(groupId), taskNode);
        return future;
    }

    /**
     * 【主入口 3】兼容旧接口：投递实现了 Task 接口的对象。
     */
    public CompletableFuture<Task> serialExecute(Task task) {
        CompletableFuture<Task> future = new CompletableFuture<>();
        TaskNode taskNode = new TaskNode(task.groupId(), task, future, task);
        dispatchToQueue(calculateSlotIndex(task.groupId()), taskNode);
        return future;
    }

    /**
     * 【普通异步入口】不需要亲和串行的普通异步任务，直接扔进底层线程池。
     */
    public void execute(Runnable runnable) {
        this.threadPool.execute(runnable);
    }

    /**
     * 【普通异步带 Future 入口】直接投递普通异步任务并获取 CompletableFuture。
     */
    public CompletableFuture<Void> run(Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        this.threadPool.execute(() -> {
            try {
                runnable.run();
                future.complete(null);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    /**
     * 【普通异步带返回值入口】直接投递普通异步计算任务并获取 CompletableFuture。
     */
    public <T> CompletableFuture<T> run(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        this.threadPool.execute(() -> {
            try {
                future.complete(supplier.get());
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future;
    }

    // =========================================================================
    // 第二部分：内部细化逻辑（分拣、调度、执行、工作窃取、监控）
    // =========================================================================

    /**
     * 高性能位运算哈希路由。
     * <p>
     * 通过高 16 位与低 16 位异或扰动后与掩码相与，避免低位哈希碰撞，彻底消灭负数取模越界问题。
     */
    private int calculateSlotIndex(int groupId) {
        int h = groupId;
        h = h ^ (h >>> 16);
        return h & this.slotMask;
    }

    private void dispatchToQueue(int slotIndex, TaskNode taskNode) {
        if (this.taskLists[slotIndex].offerAndSchedule(taskNode)) {
            this.threadPool.execute(() -> processTasks(slotIndex));
        }
    }

    private void processTasks(int startIndex) {
        try {
            long threadId = Thread.currentThread().getId();
            drainSlot(startIndex, threadId);
            stealAndDrain(startIndex, threadId);
        } catch (Exception e) {
            LOGGER.error("[ExecutorPool] Worker 异常退出, startIndex: {}", startIndex, e);
        }
    }

    private void drainSlot(int slotIndex, long threadId) {
        TaskList list = this.taskLists[slotIndex];

        if (list.isBusy() && list.notSelf(threadId)) {
            checkAndLogTimeout(slotIndex);
            return;
        }

        if (!list.getProcessingAuthority(threadId)) {
            return;
        }

        try {
            executeSlotTasks(slotIndex);
        } finally {
            list.releaseProcessingAuthority(threadId);

            if (list.finishAndRescheduleIfNeeded()) {
                this.threadPool.execute(() -> processTasks(slotIndex));
            }
        }
    }

    private void executeSlotTasks(int slotIndex) {
        TaskList list = this.taskLists[slotIndex];
        TaskNode task;

        while ((task = (TaskNode) list.poll()) != null) {
            list.updateTime();

            try {
                task.run();
                task.completeSuccess();
            } catch (Throwable e) {
                LOGGER.error("[ExecutorPool] 业务任务执行异常, slot: {}, task: {}", slotIndex, task, e);
                try {
                    task.completeExceptionally(e);
                } catch (Throwable e2) {
                    LOGGER.error("[ExecutorPool] 异常通知回调失败, slot: {}, task: {}", slotIndex, task, e2);
                }
            }
        }
    }

    /**
     * 带界限的随机偏置环形工作窃取算法。
     * <p>
     * <b>改进策略：</b>
     * 逻辑槽扩容至 1024 槽后，全量线性扫描会造成 CPU 缓存未命中和多个空闲 Worker 对低位槽的锁竞争。
     * 本算法生成随机偏置起点，且单次最多巡视 64 个槽位（Bounded Probing），
     * 既保障了多槽负载均衡，又杜绝了 Worker 无谓的空耗扫描。
     */
    private void stealAndDrain(int startIndex, long threadId) {
        int totalSlots = this.taskLists.length;
        int maxProbes = Math.min(totalSlots, 64);
        int probeStart = ThreadLocalRandom.current().nextInt(totalSlots);

        for (int i = 0; i < maxProbes; ++i) {
            int slotIdx = (probeStart + i) & this.slotMask;
            if (slotIdx == startIndex) {
                continue;
            }

            TaskList candidateList = this.taskLists[slotIdx];
            if (candidateList.isNotEmpty() && !candidateList.isBusy()) {
                drainSlot(slotIdx, threadId);
                // 成功窃取并消费一个空闲槽位后即返回，避免单个 Worker 过度贪婪巡视
                break;
            } else if (candidateList.isBusy() && candidateList.notSelf(threadId)) {
                checkAndLogTimeout(slotIdx);
            }
        }
    }

    private void checkAndLogTimeout(int slotIndex) {
        long busySince = this.taskLists[slotIndex].getBusySinceMs();
        if (busySince <= 0L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - busySince;

        if (elapsed <= PROCESS_TIMEOUT_MS) {
            return;
        }

        long processorId = this.taskLists[slotIndex].getProcessorId();
        String processorName = this.threadPool.getThreadName(processorId);
        LOGGER.error("[ExecutorPool 慢任务警告] 槽位:{} 处理时间过长! 持有线程:[{}:{}] 已持续耗时:{}ms",
                slotIndex, processorId, processorName, elapsed);

        if (elapsed > STACK_TRACE_TIMEOUT_MS) {
            logStackTrace(processorId);
        }
    }

    private void logStackTrace(long processorId) {
        Thread thread = ThreadPool.getThread(processorId);
        if (thread == null) {
            return;
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append("卡死线程堆栈 Dump [ID:").append(thread.getId()).append(" Name:").append(thread.getName()).append("]:");
        for (StackTraceElement element : thread.getStackTrace()) {
            sb.append("\n  at ").append(element.getClassName())
                    .append('.').append(element.getMethodName())
                    .append('(').append(element.getFileName())
                    .append(':').append(element.getLineNumber()).append(')');
        }
        LOGGER.error(sb.toString());
    }

    public int slotCount() {
        return this.taskLists.length;
    }

    public int size() {
        return this.threadPool.size();
    }

    public ExecutorService getExecutorService() {
        return this.threadPool.getPools();
    }

    public void shutdown() {
        this.threadPool.close();
    }

    /**
     * 内部任务包装节点：将 Runnable、Supplier 或 Task 与其对应的 CompletableFuture 聚合绑定。
     */
    private static class TaskNode implements Task {
        private final int groupId;
        private final Runnable runnable;
        private final Supplier<?> supplier;
        private final CompletableFuture<?> completableFuture;
        private final Object originalTask;
        private Object result;

        public TaskNode(int groupId, Runnable runnable, CompletableFuture<Void> future) {
            this.groupId = groupId;
            this.runnable = runnable;
            this.supplier = null;
            this.completableFuture = future;
            this.originalTask = runnable;
        }

        public <T> TaskNode(int groupId, Supplier<T> supplier, CompletableFuture<T> future) {
            this.groupId = groupId;
            this.runnable = null;
            this.supplier = supplier;
            this.completableFuture = future;
            this.originalTask = supplier;
        }

        public TaskNode(int groupId, Runnable runnable, CompletableFuture<Task> future, Task originalTask) {
            this.groupId = groupId;
            this.runnable = runnable;
            this.supplier = null;
            this.completableFuture = future;
            this.originalTask = originalTask;
        }

        @Override
        public int groupId() {
            return this.groupId;
        }

        @Override
        public void run() {
            if (this.supplier != null) {
                this.result = this.supplier.get();
            } else if (this.runnable != null) {
                this.runnable.run();
            }
        }

        @SuppressWarnings("unchecked")
        public void completeSuccess() {
            if (completableFuture != null) {
                if (this.supplier != null) {
                    ((CompletableFuture<Object>) completableFuture).complete(this.result);
                } else if (originalTask instanceof Task) {
                    ((CompletableFuture<Task>) completableFuture).complete((Task) originalTask);
                } else {
                    completableFuture.complete(null);
                }
            }
        }

        public void completeExceptionally(Throwable e) {
            if (completableFuture != null) {
                completableFuture.completeExceptionally(e);
            }
        }

        @Override
        public String toString() {
            return "TaskNode[groupId=" + groupId + ", task=" + originalTask + "]";
        }
    }
}
