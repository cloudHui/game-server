package utils.concurrent.timer;

import utils.concurrent.timer.model.TimeNode;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * 无序定时器实现
 * 管理多个时间节点的执行，支持延迟执行、间隔执行和有限次执行
 */
public class DisorderTimer extends AbstractTimer<DisorderTimer.Runners<Runnable>> {

    public DisorderTimer() {
        super(new ArrayList<>());
    }

    @Override
    public DisorderTimer setRunners(Runners<Runnable> runners) {
        exit();
        this.runners = runners;
        this.runners.run(this);
        return this;
    }

    public <T> void register(int delay, int interval, int count, Runner<T> runner, T param) {
        register(delay * 1000L, interval * 1000L, count, runner, param);
    }

    public <T> void registerSerial(int groupId, int delay, int interval, int count,
                                   Runner<T> runner, T param) {
        registerSerial(groupId, delay * 1000L, interval * 1000L, count, runner, param);
    }

    @Override
    protected CompletableFuture<?> executeTimeNode(TimeNode<?> timeNode) {
        return runners.run(timeNode);
    }

    @Override
    protected void rescheduleNode(TimeNode<?> node) {
        runners.run(() -> addNode(node));
    }

    @FunctionalInterface
    public interface Runners<T extends Runnable> {
        CompletableFuture<T> run(T task);
    }
}
