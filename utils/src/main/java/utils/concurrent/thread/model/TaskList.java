package utils.concurrent.thread.model;

import utils.concurrent.thread.Task;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 串行无锁任务队列（哈希槽执行单元）。
 * <p>
 * <b>核心设计理念：</b>
 * <ul>
 *   <li><b>单槽排他处理：</b> 通过 {@link #processor} 的 CAS 操作维护唯一的处理权（Authority），
 *       确保同一时刻仅有一个物理 Worker 线程在消费本队列中的任务，达成绝对的“同组串行无锁”；</li>
 *   <li><b>防惊群/防重复调度：</b> 通过 {@link #scheduled} 标记，只有在未被调度时入队才触发底层线程池提交任务，
 *       避免每来一个任务就向底层线程池提交一个 Runnable 造成大量空跑和上下文切换；</li>
 *   <li><b>无锁高并发：</b> 任务存储使用 {@link ConcurrentLinkedQueue}，消除传统锁竞争与复制开销。</li>
 * </ul>
 */
public class TaskList {

    /**
     * 当前持有该队列处理权的线程 ID（0L 表示当前队列空闲，无任何线程在处理）。
     */
    private final AtomicLong processor = new AtomicLong(0L);

    /**
     * 调度标记：是否已经向底层线程池提交过处理本队列的 Worker 任务。
     */
    private final AtomicBoolean scheduled = new AtomicBoolean(false);

    /**
     * 当前处理轮次开始的时间戳（毫秒），队列空闲时为 0L。
     */
    private volatile long busySinceMs;

    /**
     * 存放具体待执行任务的无锁并发 FIFO 队列。
     */
    private final ConcurrentLinkedQueue<Task> tasks = new ConcurrentLinkedQueue<>();

    public boolean isBusy() {
        return processor.get() != 0L;
    }

    public boolean notSelf(long processorId) {
        return getProcessorId() != processorId;
    }

    public boolean getProcessingAuthority(long processorId) {
        if (processor.compareAndSet(0L, processorId)) {
            busySinceMs = System.currentTimeMillis();
            return true;
        }
        return processor.get() == processorId;
    }

    public void releaseProcessingAuthority(long processorId) {
        if (processor.compareAndSet(processorId, 0L)) {
            busySinceMs = 0L;
        }
    }

    public long getProcessorId() {
        return processor.get();
    }

    public void updateTime() {
        busySinceMs = System.currentTimeMillis();
    }

    public long getBusySinceMs() {
        return busySinceMs;
    }

    public boolean offerAndSchedule(Task task) {
        tasks.offer(task);
        return scheduled.compareAndSet(false, true);
    }

    public Task poll() {
        Task task = tasks.poll();
        if (task != null) {
            busySinceMs = System.currentTimeMillis();
        }
        return task;
    }

    public boolean isNotEmpty() {
        return !tasks.isEmpty();
    }

    public boolean finishAndRescheduleIfNeeded() {
        scheduled.set(false);
        return !tasks.isEmpty() && scheduled.compareAndSet(false, true);
    }
}
