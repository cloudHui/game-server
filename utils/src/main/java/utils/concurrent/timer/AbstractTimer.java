package utils.concurrent.timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.concurrent.lock.TimeSignal;
import utils.concurrent.timer.model.SerialTimeNode;
import utils.concurrent.timer.model.TimeNode;

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
     * 注册普通时间节点。
     */
    public <P> void register(long delay, long interval, int count, Runner<P> runner, P param) {
        int id = idGenerator.incrementAndGet();
        LOGGER.debug("[register] 注册普通定时节点: id={}, delay={}ms, interval={}ms, count={}", id, delay, interval, count);
        addNode(new TimeNode<>(id, runner, param, delay, interval, count));
    }

    /**
     * 注册串行时间节点（同一组内的节点串行有序执行）。
     */
    public <P> void registerSerial(int groupId, long delay, long interval, int count, Runner<P> runner, P param) {
        int id = idGenerator.incrementAndGet();
        LOGGER.debug("[registerSerial] 注册串行定时节点: id={}, groupId={}, delay={}ms, interval={}ms, count={}",
                id, groupId, delay, interval, count);
        addNode(new SerialTimeNode<>(groupId, id, runner, param, delay, interval, count));
    }

    /**
     * 注册串行时间节点并返回唯一节点 ID。
     */
    public <P> int registerSerialWithId(int groupId, long delay, long interval, int count, Runner<P> runner, P param) {
        int id = idGenerator.incrementAndGet();
        LOGGER.debug("[registerSerialWithId] 注册串行节点: id={}, groupId={}, delay={}ms, interval={}ms, count={}",
                id, groupId, delay, interval, count);
        addNode(new SerialTimeNode<>(groupId, id, runner, param, delay, interval, count));
        return id;
    }

    /**
     * 注销指定 ID 的时间节点。
     */
    public void unregister(int nodeId) {
        boolean removed = false;
        lock.lock();
        try {
            Iterator<TimeNode<?>> it = nodes.iterator();
            while (it.hasNext()) {
                TimeNode<?> node = it.next();
                if (node != null && node.getId() == nodeId) {
                    it.remove();
                    removed = true;
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
        LOGGER.debug("[unregister] 注销定时节点: id={}, removed={}", nodeId, removed);
    }

    /**
     * 添加节点并通知工作线程。
     */
    protected void addNode(TimeNode<?> timeNode) {
        lock.lock();
        try {
            nodes.add(timeNode);
        } finally {
            lock.unlock();
        }
        LOGGER.trace("[addNode] 添加定时节点入队并唤醒调度器: id={}, totalNodes={}", timeNode.getId(), nodes.size());
        timeSignal.notifySignal();
    }

    /**
     * 终止定时器循环。
     */
    public void exit() {
        loops++;
        LOGGER.info("[exit] 触发定时器循环退出信号: loops={}", loops);
        timeSignal.notifySignal();
    }

    protected abstract CompletableFuture<?> executeTimeNode(TimeNode<?> timeNode);

    protected abstract void rescheduleNode(TimeNode<?> node);

    @Override
    public void run() {
        long waitTime = WAIT_TIME;
        int currentLoop = loops;
        LOGGER.info("[run] 定时器驱动引擎主循环已启动");
        while (currentLoop == loops) {
            waitTime = processNodes();
            timeSignal.waitSignal(waitTime);
        }
        LOGGER.info("[run] 定时器驱动引擎主循环正常退出: loops={}", loops);
    }

    /**
     * 扫描节点列表并调度到期节点，返回下次需要等待的最小毫秒数。
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
                    LOGGER.trace("[processNodes] 节点到期触发派工: id={}", timeNode.getId());
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
     * 触发节点异步执行并注册后续重新调度回调。
     */
    private void dispatchNode(TimeNode<?> timeNode) {
        CompletableFuture<?> future = executeTimeNode(timeNode);
        if (future != null) {
            future.whenComplete((result, throwable) -> {
                if (throwable != null) {
                    LOGGER.error("[dispatchNode] 节点执行异常: id={}", timeNode.getId(), throwable);
                }
                if (result instanceof TimeNode) {
                    TimeNode<?> node = (TimeNode<?>) result;
                    if (node.unFinished()) {
                        node.refreshTriggerTime();
                        LOGGER.trace("[dispatchNode] 周期性节点重新排期: id={}", node.getId());
                        rescheduleNode(node);
                    }
                }
            });
        }
    }
}
