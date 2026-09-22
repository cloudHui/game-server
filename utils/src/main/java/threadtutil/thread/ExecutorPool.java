package threadtutil.thread;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import threadtutil.thread.model.TaskList;
import threadtutil.utils.TimeUtils;

/**
 * 亲和串行化执行器池（基于哈希槽与工作窃取的无锁并发调度引擎）。
 * <p>
 * <b>核心设计与工作原理：</b>
 * <ul>
 * <li><b>外部单一入口：</b> 外部业务方只需调用 {@link #serialExecute(long, Runnable)} 或
 * {@link #serialExecute(int, Runnable)}，
 * 传入业务唯一分组 ID（如 tableId、userId）和具体逻辑，无需关心内部如何包装分拣；</li>
 * <li><b>槽位哈希亲和：</b> 系统内部维护了一组大小固定的 {@link TaskList} 队列槽位。
 * 相同 groupId 的任务通过哈希散列永远映射到同一个槽位，保证该业务主体的所有任务天然严格有序（FIFO 串行化）；</li>
 * <li><b>单槽排他性无锁消费：</b> 每个槽位在任意时刻只允许一个 Worker 物理线程持有处理权消费，
 * 业务逻辑在执行期间无需在 Java 层面加任何 {@code synchronized} 或重量级锁，实现零死锁、高性能；</li>
 * <li><b>工作窃取（Work-Stealing）：</b> 当 Worker 线程清空自己负责的槽位后，不会立即睡眠或空转，
 * 而是自动环形巡视并“窃取”其他有堆积但无线程在处理的空闲槽位，最大化压榨 CPU 多核吞吐量；</li>
 * <li><b>超时监控与告警：</b> 内置执行耗时监控，如果单槽位执行时间超过阈值，自动打印告警与线程调用栈，快速排查死循环或慢查询。</li>
 * </ul>
 */
public class ExecutorPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExecutorPool.class);

    /** 底层物理线程池管理器 */
    private final ThreadPool threadPool;
    /** 槽位数组：每个槽位是一个独立的串行任务队列 */
    private final TaskList[] taskLists;

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
        // 确定物理线程数量（未指定时使用 CPU 核心数）
        int poolSize = (size < 1) ? TimeUtils.PROCESS_NUMBER : size;
        this.threadPool = new ThreadPool(executorName, poolSize, queueCapacity);

        // 初始化各个队列槽位，槽位数量与物理线程数量一致，便于负载均衡
        this.taskLists = new TaskList[this.threadPool.size()];
        for (int i = 0; i < this.threadPool.size(); ++i) {
            this.taskLists[i] = new TaskList();
        }
        LOGGER.info("[init] ExecutorPool name:{}, slots:{}, queueCapacity:{}",
                executorName, this.threadPool.size(), queueCapacity);
    }

    // =========================================================================
    // 第一部分：统一的外部调用入口（门面层）
    // =========================================================================

    /**
     * 【主入口 1】以 long 类型的 groupId 投递串行任务。
     * <p>
     * <b>使用场景：</b> 棋牌桌对局（groupId 传 tableId）、玩家独立会话（传 userId）。
     * 保证同一个 ID 的任务绝对串行先后执行，不同 ID 并行并发。
     *
     * @param groupId  业务分组 ID（如 tableId）
     * @param runnable 待执行的业务逻辑
     * @return 异步执行凭据，当业务执行完毕后完成
     */
    public CompletableFuture<Void> serialExecute(long groupId, Runnable runnable) {
        return serialExecute(Long.hashCode(groupId), runnable);
    }

    /**
     * 【主入口 2】以 int 类型的 groupId 投递串行任务。
     *
     * @param groupId  业务分组整型 ID
     * @param runnable 待执行的业务逻辑
     * @return 异步执行凭据，当业务执行完毕后完成
     */
    public CompletableFuture<Void> serialExecute(int groupId, Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        TaskNode taskNode = new TaskNode(groupId, runnable, future);
        dispatchToQueue(calculateSlotIndex(groupId), taskNode);
        return future;
    }

    /**
     * 【主入口 3】兼容旧接口：投递实现了 Task 接口的对象。
     *
     * @param task 包含 groupId 与 run 逻辑的任务对象
     * @return 包含任务对象的 CompletableFuture
     */
    public CompletableFuture<Task> serialExecute(Task task) {
        CompletableFuture<Task> future = new CompletableFuture<>();
        TaskNode taskNode = new TaskNode(task.groupId(), task, future, task);
        dispatchToQueue(calculateSlotIndex(task.groupId()), taskNode);
        return future;
    }

    /**
     * 【普通异步入口】不需要亲和串行的普通异步任务，直接扔进底层线程池。
     *
     * @param runnable 任务逻辑
     */
    public void execute(Runnable runnable) {
        this.threadPool.execute(runnable);
    }

    /**
     * 【普通异步带 Future 入口】直接投递普通异步任务并获取 CompletableFuture。
     *
     * @param runnable 任务逻辑
     * @return 异步凭据
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

    // =========================================================================
    // 第二部分：内部细化逻辑（分拣、调度、执行、工作窃取、监控）
    // =========================================================================

    /**
     * 【步骤 1：哈希路由】计算业务 ID 对应的槽位索引。
     * <p>
     * <b>为什么使用 Math.floorMod：</b>
     * 避免当 groupId 的 hashCode 恰好为 {@link Integer#MIN_VALUE} 时，
     * {@code Math.abs()} 计算结果依然为负数导致数组越界。
     */
    private int calculateSlotIndex(int groupId) {
        return Math.floorMod(groupId, this.taskLists.length);
    }

    /**
     * 【步骤 2：入队与初次触发调度】将任务推入指定槽位队列并尝试唤醒 Worker。
     *
     * @param slotIndex 目标槽位
     * @param taskNode  任务节点
     */
    private void dispatchToQueue(int slotIndex, TaskNode taskNode) {
        // 第一步：将任务放入指定槽位的无锁 FIFO 队列中
        // 第二步：通过 CAS 原子判定当前队列是否已经有在途 Worker；
        // 如果返回 true，说明本槽位此前处于静止状态，需要向物理线程池提交一个 Worker 任务去启动消费
        if (this.taskLists[slotIndex].offerAndSchedule(taskNode)) {
            this.threadPool.execute(() -> processTasks(slotIndex));
        }
    }

    /**
     * 【步骤 3：Worker 线程总调度流程】消费主入口。
     * <p>
     * <b>执行顺序（先主后辅，提高利用率）：</b>
     * <ol>
     * <li>第一阶段：优先倾倒（drain）本 Worker 负责的目标槽位（startIndex）；</li>
     * <li>第二阶段：本槽位处理完后，发起“工作窃取”（stealAndDrain），巡视其他空闲槽位。</li>
     * </ol>
     *
     * @param startIndex 触发本次调度的目标槽位
     */
    private void processTasks(int startIndex) {
        try {
            long threadId = Thread.currentThread().getId();

            // 第一阶段：消费自己的槽位
            drainSlot(startIndex, threadId);

            // 第二阶段：工作窃取，主动帮其他空闲但堆积任务的槽位干活
            stealAndDrain(startIndex, threadId);

        } catch (Exception e) {
            LOGGER.error("[ExecutorPool] Worker 异常退出, startIndex: {}", startIndex, e);
        }
    }

    /**
     * 【步骤 4：单槽消费驱动流程】针对指定槽位进行排他性消费。
     * <p>
     * <b>排他安全保障步骤：</b>
     * <ol>
     * <li>忙碌检查：若该槽位正被其他线程消费，跳过并记录耗时；</li>
     * <li>抢占权限：通过 CAS 尝试获取该槽位的 {@code processingAuthority}，抢不到直接离开；</li>
     * <li>循环消费：在持有权限的保护下，调用 {@link #executeSlotTasks(int)} 依次出队执行；</li>
     * <li>释放权限：执行完毕在 finally 中必须归还权限令牌；</li>
     * <li>边缘再调度：检查在释放瞬间是否有新任务涌入，若有则再次拉起调度，绝不丢任务。</li>
     * </ol>
     *
     * @param slotIndex 槽位索引
     * @param threadId  当前执行线程的 ID
     */
    private void drainSlot(int slotIndex, long threadId) {
        TaskList list = this.taskLists[slotIndex];

        // 步骤 4.1：如果该槽位正被其他线程处理中，本线程绝不强行插入，仅做超时检查
        if (list.isBusy() && !list.isSelf(threadId)) {
            checkAndLogTimeout(slotIndex);
            return;
        }

        // 步骤 4.2：尝试以 CAS 原子方式抢占该槽位的处理权令牌
        // 这一步是达成“单槽串行绝对无锁安全”的核心门禁！抢不到则立即放弃退出。
        if (!list.getProcessingAuthority(threadId)) {
            return;
        }

        try {
            // 步骤 4.3：已成功独占当前槽位，开始排他消费队列中的全部任务
            executeSlotTasks(slotIndex);
        } finally {
            // 步骤 4.4：无论任务执行成功还是抛出异常，必须且仅由持有该令牌的线程释放权限
            list.releaseProcessingAuthority(threadId);

            // 步骤 4.5：边缘检查——如果在消费结束与释放权限的临界点又进来了新任务，
            // finishAndRescheduleIfNeeded 会原子重置调度标记并返回 true，
            // 本线程立即再次向线程池提交 Worker，杜绝新任务卡死在队头无人消费
            if (list.finishAndRescheduleIfNeeded()) {
                this.threadPool.execute(() -> processTasks(slotIndex));
            }
        }
    }

    /**
     * 【步骤 5：任务循环出队与执行】在独占保护下执行本槽位的所有任务。
     *
     * @param slotIndex 槽位索引
     */
    private void executeSlotTasks(int slotIndex) {
        TaskList list = this.taskLists[slotIndex];
        TaskNode task;

        // 循环从并发无锁队列中弹出任务，直到队列彻底排空
        while ((task = (TaskNode) list.poll()) != null) {
            // 每次成功弹出一个任务，更新活跃时间戳，防止单槽大批量正常任务被误判为卡死
            list.updateTime();

            try {
                // 真正执行业务任务（例如：桌内出牌逻辑、状态机轮转）
                task.run();
                // 标记异步 Future 成功完成
                task.completeSuccess();
            } catch (Throwable e) {
                // 一级异常隔离：单个任务的运行时异常绝不能打崩 Worker 循环，否则后续排队任务将全部饿死
                LOGGER.error("[ExecutorPool] 业务任务执行异常, slot: {}, task: {}", slotIndex, task, e);
                try {
                    // 通知调用方异常完成
                    task.completeExceptionally(e);
                } catch (Throwable e2) {
                    // 二级异常隔离：防止 CompletableFuture 回调代码自身又抛出异常破坏循环
                    LOGGER.error("[ExecutorPool] 异常通知回调失败, slot: {}, task: {}", slotIndex, task, e2);
                }
            }
        }
    }

    /**
     * 【步骤 6：工作窃取（Work-Stealing）】巡视并分担其他空闲队列。
     * <p>
     * <b>窃取算法与安全准则：</b>
     * <ul>
     * <li>从当前槽位的下一个槽位开始，环形扫描一周；</li>
     * <li><b>只窃取安全目标：</b> 目标队列必须同时满足【队列非空 {@code isNotEmpty()}】且【当前无人在处理
     * {@code !isBusy()}】；</li>
     * <li>只要有线程正在处理该槽，本 Worker 绝不插手，从而<b>百分之百保证不会破坏目标槽位的单线程串行化</b>。</li>
     * </ul>
     *
     * @param startIndex 当前已处理完毕的槽位起始位置
     * @param threadId   当前 Worker 线程 ID
     */
    private void stealAndDrain(int startIndex, long threadId) {
        int totalSlots = this.size();

        // 环形扫描：从 startIndex + 1 一直扫描到 startIndex + totalSlots - 1
        for (int step = 1; step < totalSlots; ++step) {
            int slotIdx = startIndex + step;
            if (slotIdx >= totalSlots) {
                slotIdx -= totalSlots; // 环形取模
            }

            TaskList candidateList = this.taskLists[slotIdx];

            // 准则一：目标队列有活可干（非空）且现在没有其他裁判在处理（!isBusy）
            if (candidateList.isNotEmpty() && !candidateList.isBusy()) {
                // 顺手帮忙把该槽位的任务清空（drainSlot 内部会再次 CAS 抢占令牌进行双重校验保护）
                drainSlot(slotIdx, threadId);
            } else if (candidateList.isBusy() && !candidateList.isSelf(threadId)) {
                // 准则二：如果目标队列正被别人占用，顺便检查一下对方有没有超时卡死
                checkAndLogTimeout(slotIdx);
            }
        }
    }

    /**
     * 【步骤 7：卡死检测与慢任务告警】
     *
     * @param slotIndex 槽位索引
     */
    private void checkAndLogTimeout(int slotIndex) {
        long busySince = this.taskLists[slotIndex].getBusySinceMs();
        if (busySince <= 0L) {
            return;
        }
        long elapsed = System.currentTimeMillis() - busySince;

        // 超过 5 秒未释放，打印慢任务警告日志
        if (elapsed <= PROCESS_TIMEOUT_MS) {
            return;
        }

        long processorId = this.taskLists[slotIndex].getProcessorId();
        String processorName = this.threadPool.getThreadName(processorId);
        LOGGER.error("[ExecutorPool 慢任务警告] 槽位:{} 处理时间过长! 持有线程:[{}:{}] 已持续耗时:{}ms",
                slotIndex, processorId, processorName, elapsed);

        // 超过 60 秒极可能发生了死锁或死循环，直接把持有线程的堆栈 dump 出来
        if (elapsed > STACK_TRACE_TIMEOUT_MS) {
            logStackTrace(processorId);
        }
    }

    /**
     * 【步骤 8：现场快照】输出卡死线程的调用栈。
     */
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

    // =========================================================================
    // 第三部分：基础设施与包装节点
    // =========================================================================

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
     * 内部任务包装节点：将 Runnable 或 Task 与其对应的 CompletableFuture 聚合绑定。
     */
    private static class TaskNode implements Task {
        private final int groupId;
        private final Runnable runnable;
        private final CompletableFuture<?> completableFuture;
        private final Object originalTask;

        public TaskNode(int groupId, Runnable runnable, CompletableFuture<Void> future) {
            this.groupId = groupId;
            this.runnable = runnable;
            this.completableFuture = future;
            this.originalTask = runnable;
        }

        @SuppressWarnings("unchecked")
        public TaskNode(int groupId, Runnable runnable, CompletableFuture<Task> future, Task originalTask) {
            this.groupId = groupId;
            this.runnable = runnable;
            this.completableFuture = future;
            this.originalTask = originalTask;
        }

        @Override
        public int groupId() {
            return this.groupId;
        }

        @Override
        public void run() {
            this.runnable.run();
        }

        @SuppressWarnings("unchecked")
        public void completeSuccess() {
            if (completableFuture != null) {
                if (originalTask instanceof Task) {
                    ((CompletableFuture<Task>) completableFuture).complete((Task) originalTask);
                } else {
                    ((CompletableFuture<Void>) completableFuture).complete(null);
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
