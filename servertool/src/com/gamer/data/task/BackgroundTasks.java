package com.gamer.data.task;

/**
 * 后台任务：{@code work.execute()} 的成败与回调异常隔离。
 * <p>
 * 命令已跑完时，{@code onSucceeded} 抛错不得再走 {@code onFailed}。回调一律吞掉，保证 {@code onFinished} 一定执行。
 */
public final class BackgroundTasks {

    /** 后台工作。 */
    public interface Work {
        /**
         * @return 结果码；无结果时返回 0
         * @throws Exception
         *             执行失败
         */
        int execute() throws Exception;
    }

    /** 生命周期回调。 */
    public interface Listener {
        /** @param startMillis 开始时间戳 */
        void onStarted(long startMillis);

        /**
         * @param resultCode
         *            工作结果码
         * @param endMillis
         *            结束时间戳
         */
        void onSucceeded(int resultCode, long endMillis);

        /**
         * @param error
         *            执行异常
         * @param endMillis
         *            结束时间戳
         */
        void onFailed(Exception error, long endMillis);

        /** 无论成功或失败均执行。 */
        void onFinished();
    }

    private BackgroundTasks() {}

    /**
     * 启动后台线程。
     *
     * @param threadName
     *            线程名
     * @param work
     *            工作内容
     * @param listener
     *            生命周期回调
     */
    public static void start(String threadName, boolean daemon, final Work work, final Listener listener) {
        if (work == null || listener == null) {
            throw new IllegalArgumentException("work and listener must not be null");
        }
        Thread worker = new Thread(() -> {
            try {
                listener.onStarted(System.currentTimeMillis());
                int resultCode = work.execute();
                notifyQuietly(() -> listener.onSucceeded(resultCode, System.currentTimeMillis()));
            } catch (Throwable ex) {
                Exception error = ex instanceof Exception ? (Exception) ex : new Exception(ex);
                notifyQuietly(() -> listener.onFailed(error, System.currentTimeMillis()));
            } finally {
                notifyQuietly(listener::onFinished);
            }
        }, threadName);
        worker.setDaemon(daemon);
        worker.start();
    }

    public static void start(String threadName, final Work work, final Listener listener) {
        start(threadName, false, work, listener);
    }

    /**
     * 只要工作、不要生命周期回调。
     *
     * @param threadName
     *            线程名
     * @param work
     *            工作内容
     */
    public static void start(String threadName, final Work work) {
        start(threadName, work, IGNORE);
    }

    /** 空回调。 */
    private static final Listener IGNORE = new Listener() {
        @Override
        public void onStarted(long startMillis) {}

        @Override
        public void onSucceeded(int resultCode, long endMillis) {}

        @Override
        public void onFailed(Exception error, long endMillis) {
            error.printStackTrace();
        }

        @Override
        public void onFinished() {}
    };

    /** 回调异常不影响其它阶段，尤其不能跳过 {@code onFinished}。 */
    private static void notifyQuietly(Runnable notify) {
        try {
            notify.run();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
