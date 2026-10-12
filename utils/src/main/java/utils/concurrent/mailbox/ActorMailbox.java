package utils.concurrent.mailbox;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 去中心化 Actor 串行邮箱（单实体独占无锁队列）。
 * <p>
 * <b>核心设计理念：</b>
 * <ul>
 * <li><b>单实体绝对物理隔离：</b> 每个牌桌或业务实体独立聚合一个 Mailbox 实例，从数学拓扑上根除全局哈希槽碰撞与队头阻塞（Head-of-Line Blocking）；</li>
 * <li><b>轻量 CAS 四态流转：</b> 基于 {@link #state} 原子维护 {@code IDLE(0) -> SCHEDULED(1) -> RUNNING(2) -> DISPOSED(3)}，彻底防惊群且零多余派工；</li>
 * <li><b>防饥饿配额（Batch Quota）：</b> 单次调度最多连续消费指定任务数（默认 64），超额主动让渡并重新提交，防止单实体狂刷任务饿死线程池；</li>
 * <li><b>优雅终结与排空：</b> 实体注销时调用 {@link #dispose()}，拒绝后续投递，排空剩余任务并快速通知调用端，杜绝异步悬挂。</li>
 * </ul>
 *
 * @author cloud
 */
public class ActorMailbox implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ActorMailbox.class);

    /** 空闲休眠态：无任务且无 Worker 在途 */
    public static final int STATE_IDLE = 0;
    /** 已就绪调度态：已向线程池提交派工单，Worker 尚未进场 */
    public static final int STATE_SCHEDULED = 1;
    /** 正在运行态：某个 Worker 线程正在消费当前邮箱任务 */
    public static final int STATE_RUNNING = 2;
    /** 已销毁终结态：实体已注销，拒绝后续任务并排空 */
    public static final int STATE_DISPOSED = 3;

    /** 默认单批次处理上限 */
    public static final int DEFAULT_BATCH_QUOTA = 64;

    /** 业务实体唯一标识（如 tableId 或 userId） */
    private final long entityId;
    /** 底层物理 Worker 线程池执行器 */
    private final Executor executor;
    /** 单批次最大消费任务配额（防止极端高频任务独占 Worker 导致其他实体饥饿） */
    private final int batchQuota;
    /** 存放待执行任务节点的并发无锁 FIFO 队列 */
    private final ConcurrentLinkedQueue<MailboxTask<?>> queue = new ConcurrentLinkedQueue<>();
    /** 原子状态机：维护 IDLE, SCHEDULED, RUNNING, DISPOSED 状态 */
    private final AtomicInteger state = new AtomicInteger(STATE_IDLE);
    /** 当前批次开始执行时间戳（毫秒），用于外部监控长任务 */
    private volatile long runStartTimeMs = 0L;

    public ActorMailbox(long entityId, Executor executor) {
        this(entityId, executor, DEFAULT_BATCH_QUOTA);
    }

    public ActorMailbox(long entityId, Executor executor, int batchQuota) {
        this.entityId = entityId;
        this.executor = executor;
        this.batchQuota = Math.max(1, batchQuota);
    }

    /**
     * 向本邮箱投递一个无返回值的异步串行任务。
     *
     * @param runnable 具体业务逻辑
     * @return 异步执行凭据
     */
    public CompletableFuture<Void> tell(Runnable runnable) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        if (state.get() == STATE_DISPOSED) {
            future.completeExceptionally(new IllegalStateException("Mailbox已注销, entityId: " + entityId));
            return future;
        }
        queue.offer(new MailboxTask<>(runnable, null, future));
        scheduleIfNeeded();
        return future;
    }

    /**
     * 向本邮箱投递一个带有返回值的异步串行计算任务。
     *
     * @param <T>      返回值类型
     * @param supplier 具有返回值的计算逻辑
     * @return 异步执行凭据
     */
    public <T> CompletableFuture<T> ask(Supplier<T> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        if (state.get() == STATE_DISPOSED) {
            future.completeExceptionally(new IllegalStateException("Mailbox已注销, entityId: " + entityId));
            return future;
        }
        queue.offer(new MailboxTask<>(null, supplier, future));
        scheduleIfNeeded();
        return future;
    }

    /**
     * 调度判定门禁：如果处于空闲状态，则原子切换为就绪态并向线程池提交自身。
     */
    private void scheduleIfNeeded() {
        while (true) {
            int current = state.get();
            if (current == STATE_DISPOSED) {
                return;
            }
            if (current == STATE_RUNNING || current == STATE_SCHEDULED) {
                // 已有 Worker 正在路上或正在消费，本任务安心排队即可，绝不重复拉起
                return;
            }
            if (state.compareAndSet(STATE_IDLE, STATE_SCHEDULED)) {
                // 成功抢下调度权，向物理线程池派单
                executor.execute(this);
                return;
            }
        }
    }

    @Override
    public void run() {
        // 第一步：状态由 SCHEDULED 跃迁为 RUNNING，确立排他执行权
        if (!state.compareAndSet(STATE_SCHEDULED, STATE_RUNNING)) {
            return;
        }

        this.runStartTimeMs = System.currentTimeMillis();
        int processedCount = 0;

        try {
            // 第二步：在配额限制内循环出队消费
            MailboxTask<?> task;
            while (processedCount < batchQuota && (task = queue.poll()) != null) {
                try {
                    task.execute();
                } catch (Throwable t) {
                    LOGGER.error("[ActorMailbox] 业务任务执行异常, entityId: {}", entityId, t);
                    task.completeExceptionally(t);
                }
                processedCount++;
            }
        } finally {
            this.runStartTimeMs = 0L;
            // 第三步：退出检查与边缘再调度
            completeBatchAndReschedule();
        }
    }

    /**
     * 批次执行结束后的原子状态重置与边缘竞态检查。
     */
    private void completeBatchAndReschedule() {
        while (true) {
            int current = state.get();
            if (current == STATE_DISPOSED) {
                return;
            }
            if (!queue.isEmpty()) {
                // 队列中仍有剩余任务（超额或新进入），重新挂载至线程池尾部排队
                if (state.compareAndSet(STATE_RUNNING, STATE_SCHEDULED)) {
                    executor.execute(this);
                    return;
                }
            } else {
                // 队列已空，恢复为静止 IDLE
                if (state.compareAndSet(STATE_RUNNING, STATE_IDLE)) {
                    // 双重检查：防止在刚置为 IDLE 的临界瞬间外部又入队新任务
                    if (!queue.isEmpty() && state.compareAndSet(STATE_IDLE, STATE_SCHEDULED)) {
                        executor.execute(this);
                    }
                    return;
                }
            }
        }
    }

    /**
     * 显式注销并安全排空当前 Mailbox。
     * <p>
     * 阻断后续所有任务投递，并快速令队列中积压的 Future 异常完成，通知客户端。
     */
    public void dispose() {
        state.set(STATE_DISPOSED);
        MailboxTask<?> task;
        while ((task = queue.poll()) != null) {
            task.completeExceptionally(new IllegalStateException("桌子已解散, 任务作废, entityId: " + entityId));
        }
    }

    public long getEntityId() {
        return entityId;
    }

    public boolean isDisposed() {
        return state.get() == STATE_DISPOSED;
    }

    public long getRunStartTimeMs() {
        return runStartTimeMs;
    }

    public int getQueueSize() {
        return queue.size();
    }

    /**
     * 内部通用包装任务节点。
     */
    private static class MailboxTask<T> {
        private final Runnable runnable;
        private final Supplier<T> supplier;
        private final CompletableFuture<T> future;

        @SuppressWarnings("unchecked")
        public MailboxTask(Runnable runnable, Supplier<T> supplier, CompletableFuture<?> future) {
            this.runnable = runnable;
            this.supplier = supplier;
            this.future = (CompletableFuture<T>) future;
        }

        public void execute() {
            if (supplier != null) {
                T res = supplier.get();
                future.complete(res);
            } else if (runnable != null) {
                runnable.run();
                future.complete(null);
            }
        }

        public void completeExceptionally(Throwable t) {
            future.completeExceptionally(t);
        }
    }
}
