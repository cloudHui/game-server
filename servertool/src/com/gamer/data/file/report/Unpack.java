package com.gamer.data.file.report;

import java.io.IOException;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import com.gamer.data.file.cmd.Logs;
import com.gamer.data.file.cmd.TaskLogView;
import com.gamer.data.file.zip.ZipArchiveModule;
import com.gamer.data.ui.ViewUi;

/**
 * 测服日志解压闸门：暂停用 wait，结束抛错。按钮仅解压阶段可点。
 */
public final class Unpack {

    /** 暂停/结束互斥。 */
    private static final Object LOCK = new Object();
    /** 暂停中。 */
    private static boolean paused;
    /** 用户点了结束。 */
    private static boolean stopped;
    /** 暂停/继续。 */
    private static JButton pauseBtn;
    /** 结束。 */
    private static JButton haltBtn;

    private Unpack() {}

    /**
     * 琥珀实心「暂停」，解压中切成「继续」。
     *
     * @return 按钮
     */
    public static JButton pauseButton() {
        if (pauseBtn == null) {
            pauseBtn = ViewUi.warn(ViewUi.click("暂停", Unpack::togglePause));
            pauseBtn.setEnabled(false);
            pauseBtn.setToolTipText("仅解压阶段：暂停或继续");
        }
        return pauseBtn;
    }

    /**
     * 红底「结束」。
     *
     * @return 按钮
     */
    public static JButton haltButton() {
        if (haltBtn == null) {
            haltBtn = ViewUi.halt(ViewUi.click("结束", Unpack::halt));
            haltBtn.setEnabled(false);
            haltBtn.setToolTipText("仅解压阶段：停止并保留压缩包");
        }
        return haltBtn;
    }

    /**
     * 页签日志 + 暂停/结束检查点。
     *
     * @param view
     *            测服下载页签
     * @return 解压监听
     */
    static ZipArchiveModule.Listener listen(final TaskLogView view) {
        final ZipArchiveModule.Listener inner = Logs.zipListener(view);
        return new ZipArchiveModule.Listener() {
            @Override
            public void log(String line) {
                inner.log(line);
            }

            @Override
            public void progress(String action, int current, int total) {
                inner.progress(action, current, total);
            }

            @Override
            public void checkpoint() throws IOException {
                Unpack.checkpoint();
            }
        };
    }

    /** 解压开始：复位并点亮按钮。 */
    static void begin() {
        synchronized (LOCK) {
            paused = false;
            stopped = false;
        }
        setBusy(true);
    }

    /** 解压结束：熄灭按钮。 */
    static void end() {
        synchronized (LOCK) {
            paused = false;
            stopped = false;
        }
        setBusy(false);
    }

    /**
     * @return 本次是否点了结束
     */
    static boolean stopped() {
        synchronized (LOCK) {
            return stopped;
        }
    }

    /**
     * 暂停时 wait；结束则抛错。
     *
     * @throws IOException
     *             用户结束解压
     */
    static void checkpoint() throws IOException {
        synchronized (LOCK) {
            while (paused && !stopped) {
                try {
                    LOCK.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("解压被中断", e);
                }
            }
            if (stopped) {
                throw new IOException("已结束解压");
            }
        }
    }

    private static void togglePause() {
        synchronized (LOCK) {
            if (pauseBtn == null || !pauseBtn.isEnabled()) {
                return;
            }
            paused = !paused;
            pauseBtn.setText(paused ? "继续" : "暂停");
            if (!paused) {
                LOCK.notifyAll();
            }
        }
    }

    private static void halt() {
        synchronized (LOCK) {
            if (haltBtn == null || !haltBtn.isEnabled()) {
                return;
            }
            stopped = true;
            paused = false;
            LOCK.notifyAll();
        }
    }

    private static void setBusy(final boolean on) {
        Runnable ui = () -> {
            if (pauseBtn != null) {
                pauseBtn.setText("暂停");
                pauseBtn.setEnabled(on);
            }
            if (haltBtn != null) {
                haltBtn.setEnabled(on);
            }
        };
        if (SwingUtilities.isEventDispatchThread()) {
            ui.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(ui);
        } catch (Exception e) {
            SwingUtilities.invokeLater(ui);
        }
    }
}
