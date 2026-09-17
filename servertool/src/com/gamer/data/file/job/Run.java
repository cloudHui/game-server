package com.gamer.data.file.job;

import javax.swing.SwingUtilities;

import com.gamer.data.file.cmd.Logs;
import com.gamer.data.file.cmd.TaskLogView;
import com.gamer.data.task.BackgroundTasks;

/**
 * 页签任务：互斥、异步、状态栏耗时。
 */
public final class Run {

    /** 是否正在跑。 */
    private boolean busy;

    /**
     * 抢锁并开始计时。
     *
     * @param view
     *            页签
     * @param skip
     *            已在跑时的日志
     * @return 拿到锁
     */
    public boolean begin(TaskLogView view, String skip) {
        synchronized (this) {
            if (busy) {
                Logs.appendLog(view, skip);
                return false;
            }
            busy = true;
        }
        // Swing Timer / 状态栏必须回 EDT，避免调度线程卡 UI。
        final long start = System.currentTimeMillis();
        Runnable ui = () -> {
            view.runCount++;
            view.currentStartMillis = start;
            view.phaseDetail = "执行中";
            Logs.ensureStatusTickTimer();
            Logs.refreshLogStatus();
        };
        if (SwingUtilities.isEventDispatchThread()) {
            ui.run();
        } else {
            SwingUtilities.invokeLater(ui);
        }
        return true;
    }

    /**
     * 抢锁后后台执行。
     *
     * @param skip
     *            已在跑时的日志
     * @param thread
     *            线程名
     * @param view
     *            页签
     * @param work
     *            工作
     * @return 拿到锁并已提交
     */
    public boolean start(String skip, String thread, TaskLogView view, BackgroundTasks.Work work) {
        if (!begin(view, skip)) {
            return false;
        }
        async(thread, view, work);
        return true;
    }

    /**
     * 后台执行；结束时放锁。
     *
     * @param thread
     *            线程名
     * @param view
     *            页签
     * @param work
     *            工作
     */
    public void async(String thread, final TaskLogView view, final BackgroundTasks.Work work) {
        BackgroundTasks.start(thread, work, new BackgroundTasks.Listener() {
            @Override
            public void onStarted(long startMillis) {}

            @Override
            public void onSucceeded(int resultCode, long endMillis) {
                Logs.finishTaskTiming(view, endMillis, resultCode == 0);
                Logs.setLogPhaseDetail(view, resultCode == 0 ? "完成" : "失败");
            }

            @Override
            public void onFailed(Exception error, long endMillis) {
                Logs.appendLog(view, "[错误] " + error.getMessage());
                Logs.finishTaskTiming(view, endMillis, false);
                Logs.setLogPhaseDetail(view, "失败");
            }

            @Override
            public void onFinished() {
                synchronized (Run.this) {
                    busy = false;
                }
                Logs.stopStatusTickTimerIfIdle();
                Logs.refreshLogStatus();
            }
        });
    }
}
