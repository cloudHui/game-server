package com.gamer.data.file.db;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * 数据库 Tab 界面日志（仅展示，不写文件）。
 */
final class UiLog {

    /** 时间格式 */
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    /** 日志文本区 */
    private final JTextArea area;

    /** 最大保留行数 */
    private static final int MAX_LINES = 200;

    /**
     * @param area
     *            日志展示文本区
     */
    UiLog(JTextArea area) {
        this.area = area;
        area.setEditable(false);
        area.setFont(area.getFont().deriveFont(11f));
    }

    /**
     * 追加一条日志（线程安全，自动切 EDT）。
     *
     * @param message
     *            日志内容
     */
    void info(String message) {
        append("INFO", message);
    }

    /**
     * 追加一条错误日志。
     *
     * @param message
     *            日志内容
     */
    void error(String message) {
        append("ERROR", message);
    }

    /**
     * 写入日志行。
     */
    private void append(String level, String message) {
        String line = TIME_FMT.format(new Date()) + " [" + level + "] " + message + "\n";
        Runnable task = () -> {
            area.append(line);
            trimLines();
            area.setCaretPosition(area.getDocument().getLength());
        };
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }

    /**
     * 超出上限时删除头部行。
     */
    private void trimLines() {
        int lines = area.getLineCount();
        if (lines <= MAX_LINES) {
            return;
        }
        try {
            int start = area.getLineStartOffset(lines - MAX_LINES);
            area.replaceRange("", 0, start);
        } catch (Exception ignored) {
            // 截断失败忽略
        }
    }
}
