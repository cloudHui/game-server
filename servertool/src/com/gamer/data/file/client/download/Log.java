package com.gamer.data.file.client.download;

import com.gamer.data.file.cmd.Logs;
import com.gamer.data.file.cmd.TaskLogView;

import static com.gamer.data.file.cmd.Logs.bindLogView;

/**
 * 客户端下载与定时调度共用的日志门面。
 * <p>
 * 调度器消息与 {@link JenkinsPackage} 步骤日志均写入同一「客户端下载」页签： 调度相关走 {@link #logScheduler}；Jenkins 探测/下载走 {@link #append} 进
 * JTextArea， 实时进度则由 {@link Logs#setLogPhaseDetail} 写页签顶部状态栏。
 */
public final class Log {

    /** 日志键 */
    public static final String LOG_KEY = "CLIENT_DOWNLOAD";
    /** 页签标题 */
    public static final String TAB_TITLE = "客户端下载";
    /** 定时调度器日志前缀 */
    private static final String SCHEDULER_LOG_PREFIX = "[CLIENT_DOWNLOAD_SCHEDULER] ";

    private Log() {}

    /** 获取日志页签（不切换选中）。 */
    public static TaskLogView getLogView() {
        return bindLogView(LOG_KEY, TAB_TITLE, false);
    }

    /**
     * 输出定时调度器日志（带统一前缀，便于与 Jenkins 步骤1~9 区分）。
     *
     * @param message
     *            正文（无需自带前缀）
     */
    public static void logScheduler(String message) {
        append(getLogView(), SCHEDULER_LOG_PREFIX + message);
    }

    /**
     * 追加一行 Jenkins 下载任务日志到 JTextArea（线程安全，内部 EDT 投递）。
     * <p>
     * 高频探测明细应走状态栏 {@link Logs#setLogPhaseDetail}，避免刷屏。
     *
     * @param logView
     *            目标页签，通常为 {@link #getLogView()} 返回值
     * @param line
     *            单行文本（可含 [步骤N] 前缀）
     */
    public static void append(TaskLogView logView, String line) {
        Logs.appendLog(logView, line);
    }
}
