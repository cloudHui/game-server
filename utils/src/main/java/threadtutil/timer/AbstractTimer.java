package threadtutil.timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import threadtutil.lock.TimeSignal;
import threadtutil.timer.model.SerialTimeNode;
import threadtutil.timer.model.TimeNode;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 抽象定时器驱动引擎
 * <p>支持延迟执行、间隔循环、有限次执行与基于分组的串行时间节点调度。</p>
 *
 * @param <T> 任务执行器类型
 * @author cloud
 */
public abstract class AbstractTimer<T> implements Runnable {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractTimer.class);
    protected static final long WAIT_TIME = 180000L;

    /** 节点唯一自增 ID 生成器 */
    protected final AtomicInteger idGenerator = new AtomicInteger(0);

    /** 时间节点链表 */
    protected final List<TimeNode<?>> nodes;

    /** 保护节点列表的独占锁 */
    protected final Lock lock = new ReentrantLock(false);

    /** 线程唤醒信号量 */
    protected final TimeSignal timeSignal = new TimeSignal();

    /** 外部关联执行器 */
    protected T runners;

    /** 退出标志位计数 */
    protected volatile int loops = 0;

    protected AbstractTimer(List<TimeNode<?>> nodes) {
        this.nodes = nodes;
    }

    public abstract AbstractTimer<T> setRunners(T runners);

    /**
     * 注册普通时间节点
     */
    public <P> void register(long delay, long interval, int count, Runner<P> runner, P param) {
        addNode(new TimeNode<>(idGenerator.incrementAndGet(), runner, param, delay, interval, count));
    }

    /**
     * 注册串行时间节点（同一组内的节点串行有序执行）
     */
    public <P> void registerSerial(int groupId, long delay, long interval, int count, Runner<P> runner, P param) {
        addNode(new SerialTimeNode<>(groupId, idGenerator.incrementAndGet(), runner, param, delay, interval, count));
    }

    /**
     * 注册串行时间节点并返回唯一节点 ID
     */
    public <P> int registerSerialWithId(int groupId, long delay, long interval, int count, Runner<P> runner, P param) {
        int id = idGenerator.incrementAndGet();
        addNode(new SerialTimeNode<>(groupId, id, runner, param, delay, interval, count));
        return id;
    }

    /**
     * 注销指定 ID 的时间节点
     */
    public void unregister(int nodeId) {
        lock.lock();
        try {
            Iterator<TimeNode<?>> it = nodes.iterator();
            while (it.hasNext()) {
                TimeNode<?> node = it.next();
                if (node != null && node.getId() == nodeId) {
                    it.remove();
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 添加节点并通知工作线程
     */
    protected void addNode(TimeNode<?> timeNode) {
        lock.lock();
        try {
            nodes.add(timeNode);
        } finally {
            lock.unlock();
        }
        timeSignal.notifySignal();
    }

    /**
     * 终止定时器循环
     */
    public void exit() {
        loops++;
        timeSignal.notifySignal();
    }

    protected abstract CompletableFuture<?> executeTimeNode(TimeNode<?> timeNode);

    protected abstract void rescheduleNode(TimeNode<?> node);

    @Override
    public void run() {
        long waitTime = WAIT_TIME;
        int currentLoop = loops;
        while (currentLoop == loops) {
            waitTime = processNodes();
            timeSignal.waitSignal(waitTime);
        }
    }

    /**
     * 扫描节点列表并调度到期节点，返回下次需要等待的最小毫秒数
     */
    private long processNodes() {
        long waitTime = WAIT_TIME;
        lock.lock();
        try {
            long now = System.currentTimeMillis();
            Iterator<TimeNode<?>> it = nodes.iterator();
            while (it.hasNext()) {
                TimeNode<?> timeNode = it.next();
                if (timeNode == null) {
                    it.remove();
                    continue;
                }
                long diff = timeNode.timeDifference(now);
                if (diff > 0L) {
                    waitTime = Math.min(diff, waitTime);
                } else {
                    it.remove();
                    dispatchNode(timeNode);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Timer 周期扫描执行异常", e);
        } finally {
            lock.unlock();
        }
        return waitTime;
    }

    /**
     * 触发节点异步执行并注册后续重新调度回调
     */
    private void dispatchNode(TimeNode<?> timeNode) {
        CompletableFuture<?> future = executeTimeNode(timeNode);
        if (future != null) {
            future.whenComplete((result, throwable) -> {
                if (result instanceof TimeNode) {
                    TimeNode<?> node = (TimeNode<?>) result;
                    if (node.unFinished()) {
                        node.refreshTriggerTime();
                        rescheduleNode(node);
                    }
                }
            });
        }
    }
}