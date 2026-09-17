package com.gamer.data.file.client.gd;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.File;
import java.util.function.Predicate;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import com.gamer.data.ui.ViewUi;
import com.gamer.data.file.config.PathConfig;
import com.gamer.data.task.BackgroundTasks;
import com.gamer.data.file.tree.Reveal;

/**
 * WindowsTools「客户端使用GD」页：逐步按钮，做完灭、下一步亮。
 */
public final class ClientGdPanel {

    public static final String TAB_TITLE = "客户端使用GD"; // 页签标题

    private static final String[] STEP_TITLES = {"打开 Excel", "生成 GD", "覆盖客户端"};

    private final JPanel view = new JPanel(new BorderLayout(6, 6)); // 根面板
    private final JButton[] stepButtons = new JButton[STEP_TITLES.length]; // 逐步按钮
    private final JButton resetButton = new JButton("重新开始");
    private final JTextArea logArea = new JTextArea();
    private final JLabel statusLabel = new JLabel("请点「打开 Excel」");
    private final Reveal dirReveal; // 根目录树展开
    private JButton packZipButton; // 整包覆盖 zip，busy 时禁用
    private JButton deleteLocalLowButton; // 删除 LocalLow GD，busy 时禁用
    private int currentStep; // 当前可点步骤
    private boolean busy; // 后台任务进行中

    /**
     * @param dirReveal 在目录树展开目录，可为 null
     */
    public ClientGdPanel(Reveal dirReveal) {
        // 打开 GD 预览
        this.dirReveal = dirReveal;
        buildView();
        refreshButtons();
    }

    /** @return 可装入页签的面板 */
    public JPanel getView() {
        return view;
    }

    private void buildView() {
        ViewUi.page(view);
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        buttonRow.setOpaque(false);
        for (int i = 0; i < STEP_TITLES.length; i++) {
            final int step = i;
            JButton button = ViewUi.style(new JButton(STEP_TITLES[i]));
            button.setForeground(Color.BLACK);
            button.addActionListener(e -> runStep(step));
            stepButtons[i] = button;
            buttonRow.add(button);
        }
        ViewUi.style(resetButton);
        resetButton.addActionListener(e -> resetFlow());
        buttonRow.add(resetButton);
        JPanel viewRow = ViewUi.row(ViewUi.click("打开 LocalLow", this::revealLocalLow),
            ViewUi.click("打开 zip 目录", this::revealZipDir),
            packZipButton = ViewUi.click("整包覆盖 zip",
                () -> runShortcut("整包覆盖 zip", ClientGd::packZip)),
            deleteLocalLowButton = ViewUi.click("删除 LocalLow GD",
                () -> runShortcut("删除 LocalLow GD", ClientGd::deleteLocalLow)));
        ViewUi.statusBar(statusLabel);
        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.setOpaque(false);
        north.add(ViewUi.card("步骤", buttonRow), BorderLayout.NORTH);
        north.add(ViewUi.card("查看", viewRow), BorderLayout.CENTER);
        north.add(statusLabel, BorderLayout.SOUTH);
        logArea.setEditable(false);
        ViewUi.log(logArea);
        view.add(north, BorderLayout.NORTH);
        view.add(ViewUi.card("日志", ViewUi.scroll(logArea)), BorderLayout.CENTER);
    }

    private void runStep(final int step) {
        if (step != currentStep) {
            return;
        }
        runAsync(STEP_TITLES[step], log -> executeStep(step, log), true);
    }

    /**
     * 查看区异步任务：不推进步骤。
     */
    private void runShortcut(String title, Predicate<ClientGdLog.Ui> work) {
        runAsync(title, work, false);
    }

    /**
     * 后台跑一步或快捷任务。advanceStep 仅步骤成功时 +1。
     */
    private void runAsync(final String title, final Predicate<ClientGdLog.Ui> work, final boolean advanceStep) {
        if (busy) {
            appendLog("任务进行中，请稍候");
            return;
        }
        busy = true;
        refreshButtons();
        final ClientGdLog.Ui uiLog = uiLog();
        BackgroundTasks.start("client-gd-" + title, () -> work.test(uiLog) ? 0 : 1,
            new BackgroundTasks.Listener() {
                @Override
                public void onStarted(long startMillis) {
                    appendLog("---- " + title + " ----");
                }

                @Override
                public void onSucceeded(int resultCode, long endMillis) {
                    finishWork(title, resultCode, null, advanceStep);
                }

                @Override
                public void onFailed(Exception error, long endMillis) {
                    finishWork(title, 1, error, false);
                }

                @Override
                public void onFinished() {
                }
            });
    }

    private boolean executeStep(int step, ClientGdLog.Ui log) {
        switch (step) {
            case 0:
                return openExcel(log);
            case 1:
                return ClientGd.generate(log);
            case 2:
                return ClientGd.packZip(log);
            default:
                return false;
        }
    }

    /** 切到根目录树展开 Excel 文件夹；单击打开文件、双击打开目录走树现有逻辑。 */
    private boolean openExcel(ClientGdLog.Ui log) {
        return revealDir(PathConfig.excelDir(), "Excel 目录", log);
    }

    /** 展开客户端 LocalLow 缓存目录，目录不存在不创建。 */
    private void revealLocalLow() {
        appendLog("---- 打开 LocalLow ----");
        ClientGdLog.Ui log = uiLog();
        if (!revealDir(PathConfig.clientLocalLowDataDir(), "LocalLow", log)) {
            log.line("请先跑一次客户端");
        }
    }

    /** 展开 gddata.zip 所在 StreamingAssets 目录。 */
    private void revealZipDir() {
        appendLog("---- 打开 zip 目录 ----");
        revealDir(PathConfig.clientGdZipDir(), "zip 目录", uiLog());
    }

    /**
     * 在根目录树展开目标文件夹。
     *
     * @param dir
     *            目标目录
     * @param label
     *            日志用名称
     * @param log
     *            步骤或快捷日志
     * @return 已展开
     */
    private boolean revealDir(File dir, String label, ClientGdLog.Ui log) {
        if (dir == null || !dir.isDirectory()) {
            log.line(label + " 不存在: " + (dir != null ? dir.getAbsolutePath() : "null"));
            log.status(label + " 不存在");
            return false;
        }
        if (dirReveal == null) {
            log.line("未接入目录树，无法展开: " + dir.getAbsolutePath());
            return false;
        }
        dirReveal.open(dir);
        log.line("已在「根目录」展开: " + dir.getAbsolutePath());
        log.line("单击打开文件，双击用资源管理器打开目录");
        log.status("已展开 " + label);
        return true;
    }

    private void finishWork(final String title, final int resultCode, final Exception error,
        final boolean advanceStep) {
        SwingUtilities.invokeLater(() -> {
            busy = false;
            if (error != null) {
                appendLog("异常: " + error.getMessage());
            }
            if (resultCode == 0) {
                if (advanceStep) {
                    currentStep = currentStep < STEP_TITLES.length - 1 ? currentStep + 1 : STEP_TITLES.length;
                }
                setStatus(title + " 完成");
            } else {
                setStatus(title + " 失败，可重试");
            }
            refreshButtons();
        });
    }

    private void resetFlow() {
        if (busy) {
            return;
        }
        currentStep = 0;
        logArea.setText("");
        setStatus("请点「打开 Excel」");
        refreshButtons();
    }

    /** 当前步用蓝框+加粗，不用白字填色（Windows 外观下背景色不生效会变成空白按钮）。 */
    private void refreshButtons() {
        for (int i = 0; i < STEP_TITLES.length; i++) {
            boolean active = !busy && i == currentStep;
            JButton button = stepButtons[i];
            button.setEnabled(active);
            button.setFont(active ? ViewUi.FONT_B : ViewUi.FONT);
            button.setForeground(Color.BLACK);
            int line = active ? 2 : 1;
            Color lineColor = active ? ViewUi.LINK : ViewUi.LINE;
            int pad = active ? 3 : 4;
            button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(lineColor, line),
                BorderFactory.createEmptyBorder(pad, 10, pad, 10)));
        }
        resetButton.setEnabled(!busy);
        resetButton.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ViewUi.LINE),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        packZipButton.setEnabled(!busy);
        deleteLocalLowButton.setEnabled(!busy);
    }

    private ClientGdLog.Ui uiLog() {
        return new ClientGdLog.Ui() {
            @Override
            public void line(String line) {
                appendLog(line);
            }

            @Override
            public void status(String text) {
                setStatus(text);
            }
        };
    }

    private void appendLog(final String line) {
        if (line == null) {
            return;
        }
        runOnEdt(() -> {
            logArea.append(line);
            logArea.append("\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void setStatus(final String text) {
        runOnEdt(() -> statusLabel.setText(text == null ? "" : text));
    }

    private static void runOnEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
