package com.gamer.data.file.mcp;

import com.gamer.data.file.cmd.Logs;
import com.gamer.data.file.cmd.TaskLogView;

/**
 * MCP 日志清理任务日志门面：调度与清理步骤写入「清理以前日志」页签。
 */
public final class CleanLog {

    /** 日志键 */
    public static final String LOG_KEY = "MCP_LOG_CLEAN";
    /** 页签标题 */
    public static final String TAB_TITLE = "清理以前日志";
    /** 调度日志前缀 */
    private static final String SCHEDULER_PREFIX = "[MCP_LOG_CLEAN_SCHEDULER] ";

    private CleanLog() {}

    /** 获取日志页签（不切换选中）。 */
    static TaskLogView ensureLogView() {
        return Logs.bindLogView(LOG_KEY, TAB_TITLE, false);
    }

    /** 调度器日志。 */
    static void logScheduler(String message) {
        append(SCHEDULER_PREFIX + message);
    }

    /** 追加一行任务日志。 */
    static void append(String line) {
        Logs.appendLog(ensureLogView(), line);
    }
}
