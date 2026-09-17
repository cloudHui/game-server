package com.gamer.data.excel.ui;

import com.gamer.data.task.BackgroundTasks;

/**
 * Swing 后台任务 Module：统一占用、线程启动、异常通知和最终释放顺序。
 *
 * @param <C>
 *            单次任务上下文类型
 */
public final class ViewTasks<C> {

    /** 后台任务 Interface。 */
    public interface Task<C> {
        /**
         * @param context
         *            单次任务上下文
         * @throws Exception
         *             后台任务失败
         */
        void run(C context) throws Exception;
    }

    /** 页面状态 Adapter。 */
    public interface Lifecycle<C> {
        /**
         * 尝试占用页面后台操作槽位。
         *
         * @param operationName
         *            操作名称
         * @return 是否允许启动
         */
        boolean tryBegin(String operationName);

        /**
         * 创建单次任务上下文。
         *
         * @param waitMessage
         *            初始等待文案
         * @return 任务上下文
         */
        C createContext(String waitMessage);

        /**
         * 记录未被业务任务处理的异常。
         *
         * @param error
         *            后台异常
         */
        void onFailure(Exception error);

        /** 释放页面后台操作槽位。 */
        void finish();
    }

    /** 页面状态 Adapter。 */
    private final Lifecycle<C> lifecycle;

    /**
     * @param lifecycle
     *            页面状态 Adapter
     */
    public ViewTasks(Lifecycle<C> lifecycle) {
        if (lifecycle == null) {
            throw new IllegalArgumentException("lifecycle must not be null");
        }
        this.lifecycle = lifecycle;
    }

    /**
     * 占用操作槽位并启动守护线程。
     *
     * @param operationName
     *            操作名称
     * @param threadName
     *            线程名称
     * @param waitMessage
     *            初始等待文案
     * @param task
     *            后台任务
     */
    public void start(String operationName, String threadName, String waitMessage, final Task<C> task) {
        if (!lifecycle.tryBegin(operationName)) {
            return;
        }
        final C context = lifecycle.createContext(waitMessage);

        BackgroundTasks.start(threadName, true, () -> {
            task.run(context);
            return 0;
        }, new BackgroundTasks.Listener() {
            @Override
            public void onStarted(long startMillis) {}

            @Override
            public void onSucceeded(int resultCode, long endMillis) {}

            @Override
            public void onFailed(Exception error, long endMillis) {
                lifecycle.onFailure(error);
            }

            @Override
            public void onFinished() {
                lifecycle.finish();
            }
        });
    }
}
