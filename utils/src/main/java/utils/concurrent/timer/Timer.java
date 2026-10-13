package utils.concurrent.timer;

import utils.concurrent.thread.ExecutorPool;
import utils.concurrent.timer.model.SerialTimeNode;
import utils.concurrent.timer.model.TimeNode;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 定时器：在独立调度线程上检查到期节点，实际业务投递到 ExecutorPool。
 */
public class Timer extends AbstractTimer<ExecutorPool> {
    private static final AtomicInteger TIMER_SEQ = new AtomicInteger();
    private volatile Thread schedulerThread;

    public Timer() {
        super(new ArrayList<>());
    }

    @Override
    public Timer setRunners(ExecutorPool runners) {
        exit();
        timeSignal.notifySignal();
        this.runners = runners;
        String name = "ThreadUtil-Timer-" + TIMER_SEQ.incrementAndGet();
        Thread thread = new Thread(this, name);
        thread.setDaemon(true);
        this.schedulerThread = thread;
        thread.start();
        LOGGER.info("[setRunners] 成功绑定 ExecutorPool 并拉起后台定时调度线程: threadName='{}'", name);
        return this;
    }

    @Override
    protected CompletableFuture<?> executeTimeNode(TimeNode<?> timeNode) {
        if (timeNode instanceof SerialTimeNode) {
            return runners.serialExecute((SerialTimeNode<?>) timeNode);
        }
        return runners.run(timeNode);
    }

    @Override
    protected void rescheduleNode(TimeNode<?> node) {
        runners.run(() -> addNode(node));
    }

    /**
     * 停止调度线程（通过 exit 打断循环）。
     */
    public void stop() {
        LOGGER.info("[stop] 正在停止 Timer 调度线程...");
        exit();
        timeSignal.notifySignal();
        Thread thread = schedulerThread;
        if (thread != null) {
            thread.interrupt();
            LOGGER.info("[stop] Timer 调度线程中断信号已发送: threadName='{}'", thread.getName());
        }
    }
}
