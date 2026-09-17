package com.gamer.data.excel.diff.ui.busy;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

import com.gamer.data.log.Log;
import com.gamer.data.excel.ui.ViewEdtUtil;
import com.gamer.data.ui.ViewUi;
import com.gamer.data.gdg.progress.GdProgressContext;

/**
 * 后台操作忙碌态：状态文案、进度条、按钮禁用、窗口标题后缀。
 * <p>
 * 与业务逻辑解耦；由 View 在创建状态栏时挂载控件。
 * </p>
 */
public final class DiffBusyUi {

    /** 所属窗口（改标题） */
    private final JFrame frame;
    /** 空闲时窗口标题 */
    private final String baseTitle;
    /** 忙碌时写日志（占用失败提示） */
    private final Log log;
    /** 忙碌期间禁用的按钮 */
    private final List<JButton> lockButtons = new ArrayList<>();

    /** 状态标签 */
    private JLabel statusLabel;
    /** 进度条 */
    private JProgressBar progressBar;
    /** 当前操作名，null 表示空闲 */
    private volatile String runningOperation;
    /** 当前操作开始时间 */
    private long operationStartMs;

    /**
     * @param frame
     *            主窗口
     * @param baseTitle
     *            空闲标题
     * @param log
     *            日志（占用失败时提示）
     */
    public DiffBusyUi(JFrame frame, String baseTitle, Log log) {
        this.frame = frame;
        this.baseTitle = baseTitle;
        this.log = log;
    }

    /**
     * 创建顶部状态条（状态文案 + 进度条），并绑定内部控件引用。
     *
     * @return 状态条面板
     */
    public JPanel createStatusTopBar() {
        JPanel topBar = new JPanel(new BorderLayout(8, 0));
        topBar.setOpaque(true);
        topBar.setBackground(ViewUi.STATUS);
        topBar.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel = new JLabel("状态：就绪");
        ViewUi.label(statusLabel);
        topBar.add(statusLabel, BorderLayout.CENTER);

        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setPreferredSize(new Dimension(160, 22));
        progressBar.setVisible(false);
        topBar.add(progressBar, BorderLayout.EAST);
        return topBar;
    }

    /**
     * 登记忙碌时需禁用的按钮。
     *
     * @param button
     *            工具按钮
     */
    public void registerLockButton(JButton button) {
        if (button != null) {
            lockButtons.add(button);
        }
    }

    /**
     * @return 当前后台操作开始时间（毫秒）
     */
    public long getOperationStartMs() {
        return operationStartMs;
    }

    /**
     * 尝试占用后台槽位。
     *
     * @param operationName
     *            操作名
     * @return 是否占用成功
     */
    public synchronized boolean tryBegin(String operationName) {
        if (runningOperation != null) {
            if (log != null) {
                log.logMessage(runningOperation + " 功能正在进行中，请等待操作完成", true);
            }
            return false;
        }
        runningOperation = operationName;
        operationStartMs = System.currentTimeMillis();
        setBusy(true, "状态：" + operationName + "中，请稍候…");
        return true;
    }

    /** 释放后台槽位。 */
    public synchronized void end() {
        runningOperation = null;
        setBusy(false, "状态：就绪");
    }

    /**
     * 更新状态文案（可后台线程调用）。
     *
     * @param statusText
     *            文案
     */
    public void setStatus(final String statusText) {
        ViewEdtUtil.run(() -> {
            if (statusLabel != null) {
                statusLabel.setText(statusText);
            }
        });
    }

    /**
     * 更新确定性进度（可后台线程调用）。
     *
     * @param current
     *            当前序号
     * @param total
     *            总数
     * @param itemName
     *            当前项名
     */
    public void setProgress(final int current, final int total, final String itemName) {
        final String statusText =
            total > 0 ? GdProgressContext.formatStatus("读取 Excel", current, total, itemName) : itemName;
        ViewEdtUtil.run(() -> applyProgressOnEdt(current, total, itemName, statusText));
    }

    private void setBusy(final boolean busy, final String statusText) {
        ViewEdtUtil.run(() -> applyBusyOnEdt(busy, statusText));
    }

    private void applyBusyOnEdt(boolean busy, String statusText) {
        if (statusLabel != null) {
            statusLabel.setText(statusText);
        }
        if (progressBar != null) {
            if (busy) {
                progressBar.setIndeterminate(true);
                progressBar.setString("处理中…");
                progressBar.setStringPainted(true);
                progressBar.setVisible(true);
            } else {
                progressBar.setVisible(false);
                progressBar.setIndeterminate(false);
            }
        }
        for (JButton button : lockButtons) {
            button.setEnabled(!busy);
        }
        if (frame != null) {
            if (busy && runningOperation != null) {
                frame.setTitle(baseTitle + " - " + runningOperation + "中");
            } else {
                frame.setTitle(baseTitle);
            }
        }
    }

    private void applyProgressOnEdt(int current, int total, String itemName, String statusText) {
        if (statusLabel != null) {
            statusLabel.setText(statusText);
        }
        if (progressBar == null || total <= 0) {
            return;
        }
        progressBar.setIndeterminate(false);
        progressBar.setMaximum(total);
        int value = current;
        if (value < 0) {
            value = 0;
        }
        if (value > total) {
            value = total;
        }
        progressBar.setValue(value);
        progressBar.setString(current + "/" + total + " " + itemName);
        progressBar.setStringPainted(true);
        progressBar.setVisible(true);
    }
}
