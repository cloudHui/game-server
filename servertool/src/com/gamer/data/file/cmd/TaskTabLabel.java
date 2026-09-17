package com.gamer.data.file.cmd;

/**
 * 任务日志页签标签：页签显示短名，悬停提示保留完整信息。
 */
final class TaskTabLabel {
    /** 页签显示短名。 */
    final String line1;
    /** 保留字段，兼容旧双行构造。 */
    final String line2;
    /** 悬停提示（完整路径与命令）。 */
    final String tooltip;
    /** 日志输出使用的单行标题。 */
    final String logTitle;

    TaskTabLabel(String line1, String line2, String tooltip, String logTitle) {
        this.line1 = line1;
        this.line2 = line2;
        this.tooltip = tooltip;
        this.logTitle = logTitle;
    }
}
