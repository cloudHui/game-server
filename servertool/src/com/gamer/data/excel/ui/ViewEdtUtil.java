package com.gamer.data.excel.ui;

import javax.swing.SwingUtilities;

/**
 * Swing EDT 调度工具：当前线程为 EDT 时同步执行，否则投递到 EDT。
 */
public final class ViewEdtUtil {

    private ViewEdtUtil() {
    }

    /**
     * 在 EDT 上执行 runnable。
     *
     * @param runnable 待执行任务
     */
    public static void run(final Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
        } else {
            SwingUtilities.invokeLater(runnable);
        }
    }

    /**
     * 在 EDT 上同步执行 runnable（后台线程调用时阻塞至完成）。
     *
     * @param runnable 待执行任务
     */
    public static void runAndWait(final Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (Exception ex) {
            throw new IllegalStateException("EDT 同步执行失败: " + ex.getMessage(), ex);
        }
    }
}
