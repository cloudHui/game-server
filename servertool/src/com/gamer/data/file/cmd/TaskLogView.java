package com.gamer.data.file.cmd;

import javax.swing.*;

import static com.gamer.data.file.cmd.CmdPanel.isJarLaunchCommand;

/**
 * 日志视图：顶部状态栏与日志文本区。
 */
public final class TaskLogView {
    /** 日志键。 */
    final String logKey;
    /** 双行页签标签。 */
    final TaskTabLabel tabLabel;
    /** 顶部状态标签。 */
    final JLabel statusLabel;
    /** 日志文本区。 */
    final JTextArea logArea;
    /** 页签容器。 */
    final JPanel container;
    /** 所在日志页签栏（CMD 或任务页）。 */
    final JTabbedPane tabs;
    /**
     * 是否参与任务耗时体系（当前/上次成功展示与 100ms 刷新）。 java -jar 等长驻 GUI 为 false：结束时间不可预期，任何耗时均无参考价值。
     */
    public boolean trackLastSuccessDuration;
    /** 复用日志累计执行次数。 */
    public int runCount;
    /** 上一轮成功任务耗时（秒），默认 0；失败不更新；jar 命令不更新。 */
    double lastDurationSec;
    /** 最近一次成功完成的时间戳。 */
    long lastSuccessMillis;
    /** 当前任务开始时间戳，0 表示未在执行。 */
    public long currentStartMillis;
    /** 附加阶段说明（解压进度、完成、失败等）。 */
    public String phaseDetail;
    /** 底部「执行」复跑该页签；自定义页读路径/命令框。 */
    Runnable executeAction;

    TaskLogView(String logKey, TaskTabLabel tabLabel, JLabel statusLabel, JTextArea logArea, JPanel container,
        JTabbedPane tabs) {
        this.logKey = logKey;
        this.tabLabel = tabLabel;
        this.statusLabel = statusLabel;
        this.logArea = logArea;
        this.container = container;
        this.tabs = tabs;
        this.trackLastSuccessDuration = !isJarLaunchCommand(extractCommandFromLogKey(logKey));
        this.runCount = 0;
        this.lastDurationSec = 0.0;
        this.lastSuccessMillis = 0L;
        this.currentStartMillis = 0L;
        this.phaseDetail = null;
    }

    /**
     * 从 logKey 解析命令部分（格式：目录路径||命令）。
     */
    private static String extractCommandFromLogKey(String logKey) {
        if (logKey == null) {
            return "";
        }
        int sep = logKey.indexOf("||");
        if (sep < 0) {
            return "";
        }
        return logKey.substring(sep + 2);
    }

}
