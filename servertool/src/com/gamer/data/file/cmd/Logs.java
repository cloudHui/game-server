package com.gamer.data.file.cmd;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.gamer.data.file.zip.ZipArchiveModule;
import com.gamer.data.ui.ViewUi;

/**
 * CMD / 任务页共用的日志页签：绑定、写日志、状态栏耗时。
 */
public final class Logs {

    /** 与 CMD 队列共用，避免页签与排队状态各锁各的。 */
    static final Object LOCK = new Object();

    /** 日志键 → 视图。 */
    private static final Map<String, TaskLogView> logViewMap = new LinkedHashMap<>();
    /** 页签容器上挂 TaskLogView。 */
    private static final Object LOG_VIEW_PROP = new Object();
    /** 页签宽。 */
    private static final int TAB_W = 68;
    /** 页签高。 */
    private static final int TAB_H = 26;

    /** 未指定 extra 时的目标栏（CMD）。 */
    private static JTabbedPane defaultTabs;
    /** 新建页签时的临时目标栏（任务页）。 */
    private static JTabbedPane extraTabs;
    /** 弹窗父控件。 */
    private static Component dialogOwner;
    /** 状态栏 100ms 刷新。 */
    private static Timer statusTickTimer;
    /** 固定页签短名。 */
    private static final Map<String, String> SHORT = new LinkedHashMap<>();

    static {
        SHORT.put("协议生成", "协议");
        SHORT.put("自定义CMD", "自定义");
        SHORT.put("解压并部署", "部署");
        SHORT.put("客户端下载", "下载");
        SHORT.put("清理以前日志", "清日志");
        SHORT.put("测服下载", "拉日志");
    }

    private Logs() {}

    /**
     * CMD 页创建后登记默认日志栏与弹窗父控件。
     *
     * @param tabs
     *            CMD 日志栏
     * @param owner
     *            弹窗父控件
     */
    static void attachCmd(JTabbedPane tabs, Component owner) {
        defaultTabs = tabs;
        dialogOwner = owner;
    }

    /**
     * 随后 {@link #bindLogView} 建出的页签装到 {@code tabs}。
     *
     * @param tabs
     *            目标栏
     * @param action
     *            绑定动作
     */
    public static void bindTo(JTabbedPane tabs, Runnable action) {
        extraTabs = tabs;
        try {
            action.run();
        } finally {
            extraTabs = null;
        }
    }

    /**
     * 绑定日志视图，可选是否选中页签（定时任务传 {@code false}）。
     *
     * @param logKey
     *            日志键
     * @param fixedTitle
     *            固定页签标题
     * @param selectTab
     *            是否选中
     * @return 日志视图
     */
    public static TaskLogView bindLogView(String logKey, String fixedTitle, boolean selectTab) {
        return bindLogView(logKey, fixedLabel(fixedTitle), selectTab);
    }

    /**
     * 绑定页签执行动作；初始化不切页。
     *
     * @param logKey
     *            日志键
     * @param title
     *            固定页签标题
     * @param action
     *            底部「执行」与快捷按钮复跑的动作
     */
    public static void bindExecutable(String logKey, String title, Runnable action) {
        TaskLogView view = bindLogView(logKey, title, false);
        view.executeAction = action;
    }

    /**
     * 绑定日志视图：同 logKey 复用同一页签。
     *
     * @param logKey
     *            日志键
     * @param tabLabel
     *            页签标签
     * @param selectTab
     *            是否选中
     * @return 日志视图
     */
    static TaskLogView bindLogView(String logKey, TaskTabLabel tabLabel, boolean selectTab) {
        TaskLogView logView;
        synchronized (LOCK) {
            logView = logViewMap.get(logKey);
            if (logView == null) {
                logView = createView(logKey, tabLabel);
                logViewMap.put(logKey, logView);
            }
        }
        if (selectTab) {
            select(logView);
        }
        return logView;
    }

    /**
     * 快捷按钮：切到对应页签并复跑。
     *
     * @param text
     *            文案
     * @param logKey
     *            日志键
     * @return 按钮
     */
    public static JButton boundButton(String text, String logKey) {
        return ViewUi.click(text, () -> runExecute(get(logKey), true));
    }

    /**
     * 执行指定栏当前页。
     *
     * @param tabs
     *            日志栏
     */
    public static void executeSelected(JTabbedPane tabs) {
        runExecute(selected(tabs), false);
    }

    /**
     * 底栏：执行 / 清空当前页。
     *
     * @param tabs
     *            日志栏
     * @param hint
     *            左侧提示
     * @param clearNote
     *            清空后写入的一行，null 则只清空
     * @return 底栏
     */
    public static JPanel execBar(JTabbedPane tabs, String hint, String clearNote) {
        return ViewUi.bar(hint, ViewUi.click("执行", () -> executeSelected(tabs)),
            ViewUi.click("清空日志", () -> clearSelected(tabs, clearNote)));
    }

    /**
     * 清空指定栏当前页；可选再写一行（须与清空同一次 EDT，避免被后到的 setText 冲掉）。
     *
     * @param tabs
     *            日志栏
     * @param note
     *            清空后写入的一行，null 则只清空
     */
    public static void clearSelected(JTabbedPane tabs, String note) {
        TaskLogView view = selected(tabs);
        if (view == null) {
            JOptionPane.showMessageDialog(dialogOwner, "请先选择任务日志页签", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            view.logArea.setText("");
            if (note != null) {
                appendLine(view.logArea, note);
            }
        });
    }

    /**
     * 读指定栏当前选中视图。
     *
     * @param tabs
     *            日志栏
     * @return 视图，可能为 null
     */
    static TaskLogView selected(JTabbedPane tabs) {
        if (tabs == null) {
            return null;
        }
        Component selected = tabs.getSelectedComponent();
        if (!(selected instanceof JPanel)) {
            return null;
        }
        Object view = ((JPanel)selected).getClientProperty(LOG_VIEW_PROP);
        return view instanceof TaskLogView ? (TaskLogView)view : null;
    }

    /**
     * 按日志键取已绑定视图。
     *
     * @param logKey
     *            日志键
     * @return 视图，可能为 null
     */
    static TaskLogView get(String logKey) {
        synchronized (LOCK) {
            return logViewMap.get(logKey);
        }
    }

    /**
     * 选中日志页签。视图为空或已在当前页则直接返回。
     *
     * @param logView
     *            视图，允许为 null
     */
    static void select(TaskLogView logView) {
        if (logView == null) {
            return;
        }
        final JTabbedPane tabs = logView.tabs;
        if (tabs.getSelectedComponent() == logView.container) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (tabs.indexOfComponent(logView.container) < 0 || tabs.getSelectedComponent() == logView.container) {
                return;
            }
            tabs.setSelectedComponent(logView.container);
        });
    }

    /**
     * 跑页签绑定动作。
     *
     * @param view
     *            日志视图
     * @param selectTab
     *            快捷按钮为 true
     */
    static void runExecute(TaskLogView view, boolean selectTab) {
        if (view == null || view.executeAction == null) {
            JOptionPane.showMessageDialog(dialogOwner, "当前页签无法执行", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selectTab) {
            select(view);
        }
        view.executeAction.run();
    }

    /**
     * 刷新日志状态栏：排队/执行中计数显示在每个日志框顶部。
     */
    public static void refreshLogStatus() {
        Map<String, Integer> waitingMap;
        Map<String, Integer> runningMap;
        synchronized (LOCK) {
            waitingMap = CmdPanel.waitingByKey();
            runningMap = CmdPanel.runningByKey();
        }
        SwingUtilities.invokeLater(() -> applyStatus(waitingMap, runningMap));
    }

    /**
     * 标记任务开始执行：短命令启动当前耗时计时；java -jar 不计时。
     *
     * @param logView
     *            视图
     * @param startMillis
     *            开始时间
     */
    static void markRunning(TaskLogView logView, long startMillis) {
        boolean trackTiming = false;
        synchronized (LOCK) {
            if (logView != null) {
                logView.phaseDetail = null;
                if (logView.trackLastSuccessDuration) {
                    logView.currentStartMillis = startMillis;
                    trackTiming = true;
                }
            }
        }
        if (trackTiming) {
            ensureStatusTickTimer();
        }
        refreshLogStatus();
    }

    /**
     * 标记任务结束：停止当前计时；仅短命令且成功时写入上次成功耗时。
     *
     * @param logView
     *            视图
     * @param endMillis
     *            结束时间
     * @param recordLastDuration
     *            本次是否成功
     */
    public static void finishTaskTiming(TaskLogView logView, long endMillis, boolean recordLastDuration) {
        synchronized (LOCK) {
            if (logView != null && logView.currentStartMillis > 0) {
                if (recordLastDuration && logView.trackLastSuccessDuration) {
                    logView.lastDurationSec = (endMillis - logView.currentStartMillis) / 1000.0;
                    logView.lastSuccessMillis = endMillis;
                }
                logView.currentStartMillis = 0;
            }
        }
        stopStatusTickTimerIfIdle();
    }

    /**
     * 设置附加阶段说明并刷新状态栏。
     *
     * @param logView
     *            视图
     * @param phaseDetail
     *            阶段文案
     */
    public static void setLogPhaseDetail(TaskLogView logView, String phaseDetail) {
        synchronized (LOCK) {
            if (logView != null) {
                logView.phaseDetail = phaseDetail;
            }
        }
        refreshLogStatus();
    }

    /**
     * 启动状态栏耗时刷新定时器。
     */
    public static void ensureStatusTickTimer() {
        onEdt(() -> {
            if (statusTickTimer == null) {
                statusTickTimer = new Timer(100, e -> refreshLogStatus());
                statusTickTimer.setRepeats(true);
            }
            if (!statusTickTimer.isRunning()) {
                statusTickTimer.start();
            }
        });
    }

    /**
     * 无短命令在计时时，停止状态栏耗时刷新定时器。
     */
    public static void stopStatusTickTimerIfIdle() {
        onEdt(() -> {
            if (statusTickTimer == null || !statusTickTimer.isRunning()) {
                return;
            }
            if (needsTimingRefresh()) {
                return;
            }
            statusTickTimer.stop();
        });
    }

    private static void onEdt(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }

    /**
     * 解压/删目录进度接到页签。
     *
     * @param view
     *            页签
     * @return 监听
     */
    public static ZipArchiveModule.Listener zipListener(final TaskLogView view) {
        final ZipArchiveModule.ProgressThrottle throttle = new ZipArchiveModule.ProgressThrottle();
        return new ZipArchiveModule.Listener() {
            @Override
            public void log(String line) {
                appendLog(view, line);
            }

            @Override
            public void progress(String action, int current, int total) {
                if (throttle.shouldUpdate(current, total)) {
                    setLogPhaseDetail(view, action + " " + current + "/" + total);
                }
            }
        };
    }

    /**
     * 线程安全地追加日志行。
     *
     * @param logView
     *            页签
     * @param line
     *            单行
     */
    public static void appendLog(TaskLogView logView, String line) {
        if (logView == null || logView.logArea == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> appendLine(logView.logArea, line));
    }

    /**
     * 追加异常消息与 cause 链堆栈（Error 被包成 Exception 时只打包装栈会看不到根因）。
     *
     * @param logView
     *            页签
     * @param prefix
     *            首行前缀
     * @param error
     *            异常
     */
    public static void appendThrowable(TaskLogView logView, String prefix, Throwable error) {
        if (error == null) {
            appendLog(logView, prefix + "null");
            return;
        }
        appendLog(logView, prefix + error.toString());
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 8) {
            if (depth > 0) {
                appendLog(logView, "Caused by: " + current.toString());
            }
            StackTraceElement[] stack = current.getStackTrace();
            for (int i = 0; i < stack.length; i++) {
                appendLog(logView, "  at " + stack[i].toString());
            }
            current = current.getCause();
            depth++;
        }
    }

    /**
     * 一次追加多行（已含换行），只投递一次 EDT。
     *
     * @param logView
     *            页签
     * @param text
     *            文本块
     */
    public static void appendLogChunk(TaskLogView logView, String text) {
        if (logView == null || logView.logArea == null || text == null || text.isEmpty()) {
            return;
        }
        final JTextArea area = logView.logArea;
        final String chunk = text;
        SwingUtilities.invokeLater(() -> {
            area.append(chunk);
            if (chunk.charAt(chunk.length() - 1) != '\n') {
                area.append("\n");
            }
            area.setCaretPosition(area.getDocument().getLength());
        });
    }

    /**
     * 格式化时间文本。
     *
     * @param millis
     *            时间戳
     * @return yyyy-MM-dd HH:mm:ss
     */
    public static String formatTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(millis));
    }

    /**
     * 追加文本到日志框末尾并滚动到最新。须在 EDT 调用。
     *
     * @param textArea
     *            文本区
     * @param line
     *            单行
     */
    static void appendLine(JTextArea textArea, String line) {
        textArea.append(line + "\n");
        textArea.setCaretPosition(textArea.getDocument().getLength());
    }

    /**
     * 格式化耗时秒数（保留一位小数）。
     *
     * @param seconds
     *            秒
     * @return 如 1.2s
     */
    static String formatDurationSec(double seconds) {
        return String.format(Locale.ROOT, "%.1fs", seconds);
    }

    private static boolean needsTimingRefresh() {
        synchronized (LOCK) {
            for (TaskLogView logView : logViewMap.values()) {
                if (logView.currentStartMillis > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void applyStatus(Map<String, Integer> waitingMap, Map<String, Integer> runningMap) {
        List<TaskLogView> views;
        synchronized (LOCK) {
            views = new ArrayList<>(logViewMap.values());
        }
        for (TaskLogView logView : views) {
            int waiting = waitingMap.getOrDefault(logView.logKey, 0);
            int running = runningMap.getOrDefault(logView.logKey, 0);
            logView.statusLabel.setText(buildStatusText(logView, waiting, running));
        }
    }

    private static double currentDurationSec(long currentStartMillis) {
        if (currentStartMillis <= 0) {
            return 0.0;
        }
        return (System.currentTimeMillis() - currentStartMillis) / 1000.0;
    }

    private static String buildTimingPart(TaskLogView logView, long currentStartMillis, double lastDurationSec) {
        if (!logView.trackLastSuccessDuration) {
            return "";
        }
        return "当前 " + formatDurationSec(currentDurationSec(currentStartMillis)) + "，上次成功 "
            + formatDurationSec(lastDurationSec);
    }

    private static String formatStatus(String statusPrefix, String timingPart, int runCount) {
        if (timingPart == null || timingPart.isEmpty()) {
            return String.format(Locale.ROOT, "%s，累计执行 %d 次", statusPrefix, runCount);
        }
        return String.format(Locale.ROOT, "%s，%s，累计执行 %d 次", statusPrefix, timingPart, runCount);
    }

    private static String buildStatusText(TaskLogView logView, int waiting, int running) {
        double lastSec;
        long currentStart;
        String phase;
        int runCount;
        synchronized (LOCK) {
            lastSec = logView.lastDurationSec;
            currentStart = logView.currentStartMillis;
            phase = logView.phaseDetail;
            runCount = logView.runCount;
        }
        String timingPart = buildTimingPart(logView, currentStart, lastSec);
        if (CmdPanel.PROTOCOL_LOG_KEY.equals(logView.logKey)) {
            return CmdPanel.protocolBar(waiting, running, timingPart, runCount);
        }
        if (phase != null && !phase.isEmpty()) {
            return formatStatus("状态：" + phase, timingPart, runCount);
        }
        if (running > 0) {
            return formatStatus(String.format(Locale.ROOT, "状态：执行中 %d，排队 %d", running, waiting), timingPart, runCount);
        }
        if (waiting > 0) {
            return formatStatus(String.format(Locale.ROOT, "状态：等待执行（排队 %d）", waiting), timingPart, runCount);
        }
        return formatStatus("状态：空闲", timingPart, runCount);
    }

    private static TaskLogView createView(String logKey, TaskTabLabel tabLabel) {
        JTextArea logArea = new JTextArea();
        logArea.setEditable(false);
        ViewUi.log(logArea);
        JLabel statusLabel = new JLabel("状态：空闲，当前 0.0s，上次成功 0.0s，累计执行 0 次");
        ViewUi.statusBar(statusLabel);
        JPanel container = new JPanel(new BorderLayout());
        container.add(statusLabel, BorderLayout.NORTH);
        container.add(ViewUi.scroll(logArea), BorderLayout.CENTER);
        JTabbedPane tabs = extraTabs != null ? extraTabs : defaultTabs;
        TaskLogView logView = new TaskLogView(logKey, tabLabel, statusLabel, logArea, container, tabs);
        container.putClientProperty(LOG_VIEW_PROP, logView);
        SwingUtilities.invokeLater(() -> installTab(logView));
        return logView;
    }

    private static void installTab(TaskLogView logView) {
        JTabbedPane tabs = logView.tabs;
        tabs.addTab(" ", logView.container);
        int index = tabs.indexOfComponent(logView.container);
        JPanel tabComponent = tabHead(logView.tabLabel);
        wireTabClick(tabComponent, logView);
        tabs.setTabComponentAt(index, tabComponent);
    }

    private static void wireTabClick(Component component, final TaskLogView logView) {
        component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                JTabbedPane tabs = logView.tabs;
                int index = tabs.indexOfComponent(logView.container);
                if (index >= 0) {
                    tabs.setSelectedIndex(index);
                }
            }
        });
        if (component instanceof Container) {
            Container container = (Container)component;
            for (int i = 0; i < container.getComponentCount(); i++) {
                wireTabClick(container.getComponent(i), logView);
            }
        }
    }

    private static JPanel tabHead(TaskTabLabel tabLabel) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setToolTipText(tabLabel.tooltip);
        panel.setPreferredSize(new Dimension(TAB_W, TAB_H));
        JLabel line1Label = new JLabel(tabLabel.line1, SwingConstants.CENTER);
        line1Label.setFont(ViewUi.FONT_B);
        line1Label.setToolTipText(tabLabel.tooltip);
        panel.add(line1Label, BorderLayout.CENTER);
        return panel;
    }

    static TaskTabLabel fixedLabel(String fixedTitle) {
        String shortName = SHORT.getOrDefault(fixedTitle, fixedTitle);
        return new TaskTabLabel(shortName, "", fixedTitle, fixedTitle);
    }
}
