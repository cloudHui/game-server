package threadtutil.thread.model;

import threadtutil.thread.Task;

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
     * <p>
     * 使用 CAS 进行 0L -> threadId 的原子抢占，充当轻量级排他锁。
     */
    private final AtomicLong processor = new AtomicLong(0L);

    /**
     * 调度标记：是否已经向底层线程池提交过处理本队列的 Worker 任务。
     * <p>
     * <b>状态说明：</b>
     * <ul>
     *   <li>{@code false}：当前没有在途或正在运行的 Worker 针对本队列进行消费；</li>
     *   <li>{@code true}：已经有一个 Worker 正在运行或已在线程池就绪队列中，无需重复提交。</li>
     * </ul>
     */
    private final AtomicBoolean scheduled = new AtomicBoolean(false);

    /**
     * 当前处理轮次开始的时间戳（毫秒），队列空闲时为 0L。
     * <p>
     * 用于外部超时检测与卡死报警（如果一个线程持有处理权超过阈值，会被告警监控捕获）。
     */
    private volatile long busySinceMs;

    /**
     * 存放具体待执行任务的无锁并发 FIFO 队列。
     */
    private final ConcurrentLinkedQueue<Task> tasks = new ConcurrentLinkedQueue<>();

    /**
     * 检查当前队列是否正处于繁忙状态（即是否有线程正在消费本队列）。
     *
     * @return true 表示有线程正在处理
     */
    public boolean isBusy() {
        return processor.get() != 0L;
    }

    /**
     * 检查当前持有处理权的线程是否就是指定的线程（通常为当前正在执行的线程自身）。
     *
     * @param processorId 待检查的线程 ID
     * @return true 表示该队列由该线程持有
     */
    public boolean notSelf(long processorId) {
        return getProcessorId() != processorId;
    }

    /**
     * 尝试抢占该队列的处理权（加轻量级独占令牌）。
     * <p>
     * <b>执行逻辑：</b>
     * <ol>
     *   <li>第一步：尝试通过 CAS 将 {@link #processor} 从 0L 原子替换为 {@code processorId}；</li>
     *   <li>第二步：如果替换成功，说明抢占成功，记录开始处理时间戳 {@link #busySinceMs} 并返回 true；</li>
     *   <li>第三步：如果 CAS 失败，检查当前持有者是否就是本线程（可重入支持），是则返回 true，否则返回 false。</li>
     * </ol>
     *
     * @param processorId 尝试抢占的线程 ID
     * @return true 表示成功获取处理权，允许执行消费；false 表示已被其他线程抢占，必须放弃
     */
    public boolean getProcessingAuthority(long processorId) {
        // 第一步：CAS 从 0L 抢占为当前线程 ID
        if (processor.compareAndSet(0L, processorId)) {
            // 抢占成功，打上开始处理的时间戳，供超时检测使用
            busySinceMs = System.currentTimeMillis();
            return true;
        }
        // 第二步：CAS 失败时，判断是否本线程已持有（支持同线程内可重入）
        return processor.get() == processorId;
    }

    /**
     * 释放当前队列的处理权（归还独占令牌）。
     * <p>
     * <b>执行逻辑：</b>
     * 只有当前持有处理权的线程才能释放，将 {@link #processor} 原子恢复为 0L，并清空开始时间。
     *
     * @param processorId 期望释放的线程 ID
     */
    public void releaseProcessingAuthority(long processorId) {
        // 原子恢复为 0L，代表无线程占用
        if (processor.compareAndSet(processorId, 0L)) {
            busySinceMs = 0L;
        }
    }

    /**
     * 获取当前持有处理权的线程 ID。
     *
     * @return 持有者线程 ID，0L 表示空闲
     */
    public long getProcessorId() {
        return processor.get();
    }

    /**
     * 刷新当前队列的处理时间戳。
     * <p>
     * 每消费完一个任务后调用一次，表明当前线程仍在健康推进任务，避免单队列批量任务较多时被误判为长任务卡死。
     */
    public void updateTime() {
        busySinceMs = System.currentTimeMillis();
    }

    /**
     * 获取当前处理批次开始的时间戳（毫秒）。
     *
     * @return 开始时间戳，0L 表示空闲
     */
    public long getBusySinceMs() {
        return busySinceMs;
    }

    /**
     * 任务入队并尝试触发调度。
     * <p>
     * <b>核心并发防重复机制：</b>
     * <ol>
     *   <li>任务先安全放入 {@link #tasks} 队列；</li>
     *   <li>通过 {@code scheduled.compareAndSet(false, true)} 原子判定：
     *       若此前为 false，说明当前队列没有挂载在线程池执行，本次成功置为 true，返回 true 通知外部向底层线程池派发 Worker；</li>
     *   <li>若此前已是 true，说明已有 Worker 正在路上或正在消费，本任务只需排在队列中等待即可，返回 false，绝不向线程池重复提交。</li>
     * </ol>
     *
     * @param task 待入队的任务节点
     * @return true 表示需要外部向线程池提交新 Worker 消费；false 表示已有在途 Worker，无需额外提交
     */
    public boolean offerAndSchedule(Task task) {
        // 第一步：无锁入队，保证 FIFO
        tasks.offer(task);
        // 第二步：CAS 检查是否需要唤醒新的 Worker
        return scheduled.compareAndSet(false, true);
    }

    /**
     * 从队列头弹出一个待执行任务。
     *
     * @return 待执行任务，若队列为空则返回 null
     */
    public Task poll() {
        Task task = tasks.poll();
        if (task != null) {
            // 每次成功弹出一个任务，更新活跃时间，防止误报超时
            busySinceMs = System.currentTimeMillis();
        }
        return task;
    }

    /**
     * 队列是否包含待执行任务。
     *
     * @return true 表示非空
     */
    public boolean isNotEmpty() {
        return !tasks.isEmpty();
    }

    /**
     * 消费批次完成后的重调度检查（防止边界漏处理）。
     * <p>
     * <b>解决的并发边界问题：</b>
     * 当 Worker 线程刚刚把队里的所有任务执行完毕准备退出时，恰好有外部线程又投递了一个新任务。
     * <ul>
     *   <li>第一步：先将 {@link #scheduled} 标记重置为 false；</li>
     *   <li>第二步：立即再次检查队列是否非空（{@code !tasks.isEmpty()}）；</li>
     *   <li>第三步：如果非空，立刻尝试再次 CAS 置为 true，若成功则返回 true，要求外部继续向线程池提交 Worker 重新消费，
     *       彻底杜绝“在 Worker 退出瞬间进来的任务永远卡在队列中无人消费”的并发遗漏 Bug。</li>
     * </ul>
     *
     * @return true 表示退出期间又有新任务涌入且已成功重置调度标记，需再次拉起 Worker
     */
    public boolean finishAndRescheduleIfNeeded() {
        // 1. 解除调度状态
        scheduled.set(false);
        // 2. 边缘判定：若又有新任务进来且能再次抢下调度权，返回 true 触发重调度
        return !tasks.isEmpty() && scheduled.compareAndSet(false, true);
    }
}
