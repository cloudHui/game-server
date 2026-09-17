package com.gamer.data.file.poll;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 全局单线程调度：上一轮跑完再计间隔；取消只停续期。
 */
final class PollCmdEngine {

    private static final ScheduledExecutorService EXEC = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "poll-cmd");
        t.setDaemon(true);
        return t;
    });

    private PollCmdEngine() {}

    /** 立即执行第一轮，之后按间隔重复直至命中或取消。 */
    static void start(PollCmdTask task) {
        PollCmdUi.setStatus(task, "排队/执行中");
        EXEC.execute(() -> tick(task));
    }

    private static void tick(PollCmdTask task) {
        if (task.cancelled.get()) {
            PollCmdUi.setStatus(task, "已取消");
            return;
        }
        PollCmdUi.setStatus(task, "执行中");
        PollCmdUi.append(task, "---- run ----");
        try {
            if (PollCmdRunner.runOnce(task)) {
                if (!task.cancelled.get()) {
                    onHit(task);
                } else {
                    PollCmdUi.setStatus(task, "已取消");
                }
                return;
            }
        } catch (Exception e) {
            PollCmdUi.append(task, "[error] " + e.getMessage());
        }
        if (task.cancelled.get()) {
            PollCmdUi.setStatus(task, "已取消");
            return;
        }
        PollCmdUi.setStatus(task, "等待 " + task.intervalSec + "s");
        EXEC.schedule(() -> tick(task), task.intervalSec, TimeUnit.SECONDS);
    }

    private static void onHit(PollCmdTask task) {
        PollCmdUi.setStatus(task, "已完成（命中关键字）");
        PollCmdUi.notifyHit(task);
    }
}
