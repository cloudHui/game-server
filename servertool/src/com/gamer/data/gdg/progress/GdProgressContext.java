package com.gamer.data.gdg.progress;

import com.gamer.data.log.Log;

import com.gamer.data.gdg.excel.GdHeartbeatRowHandler;

/**
 * GD / Excel 批量任务进度日志与文案工具（文件级、批次级共用）。
 */
public class GdProgressContext {

    public static final String TAG_WAIT = "【等待】";
    public static final String TAG_PROGRESS = "【进行中】";
    public static final String TAG_DONE = "【完成】";

    private final String prefix;
    private final Log log;

    public GdProgressContext(int fileIndex, int fileTotal, String fileName, Log log) {
        this.log = log;
        if (fileIndex > 0 && fileTotal > 0) {
            prefix = indexOf(fileIndex, fileTotal) + " " + fileName + " — ";
        } else if (fileName != null && !fileName.isEmpty()) {
            prefix = fileName + " — ";
        } else {
            prefix = "";
        }
    }

    /** 批次级（无文件前缀，仅写日志）。 */
    public static GdProgressContext batch(Log log) {
        return new GdProgressContext(0, 0, "", log);
    }

    public static String indexOf(int index, int total) {
        return "(" + index + "/" + total + ")";
    }

    public static String formatElapsed(long startMs) {
        long ms = System.currentTimeMillis() - startMs;
        if (ms < 0) {
            ms = 0;
        }
        long sec = (ms + 999L) / 1000L;
        if (sec < 60) {
            return sec + " 秒";
        }
        return (sec / 60) + " 分 " + (sec % 60) + " 秒";
    }

    public static String formatStatus(String operation, int index, int total, String itemName) {
        return "状态：" + operation + "中 " + indexOf(index, total) + " " + itemName;
    }

    public Log getLog() {
        return log;
    }

    public void logWait(String message) {
        write(TAG_WAIT + message, false);
    }

    public void logProgress(String message) {
        write(TAG_PROGRESS + prefix + message, false);
    }

    /** 批次进度（带序号、无文件前缀）。 */
    public void logIndexedProgress(int index, int total, String message) {
        write(TAG_PROGRESS + indexOf(index, total) + " " + message, false);
    }

    public void logDone(String message) {
        write(TAG_DONE + prefix + message, false);
    }

    /** 批次完成摘要，在调用时刻计算耗时（须在任务真正结束、写日志时再调）。 */
    public void logSummaryDone(long startMs, String message) {
        write(TAG_DONE + message + "，耗时 " + formatElapsed(startMs), false);
    }

    public void logError(String message) {
        write(message, true);
    }

    /** 流式读表心跳（供 {@link GdHeartbeatRowHandler} 使用）。 */
    public static void logHeartbeat(Log log, String prefix, int rowNum0) {
        if (log == null) {
            return;
        }
        log.logMessage(TAG_PROGRESS + prefix + "已扫描约 " + (rowNum0 + 1) + " 行，请稍候…", false);
    }

    private void write(String message, boolean red) {
        if (log == null) {
            System.out.println(message);
        } else {
            log.logMessage(message, red);
        }
    }
}
