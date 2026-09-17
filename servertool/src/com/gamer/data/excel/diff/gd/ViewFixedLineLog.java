package com.gamer.data.excel.diff.gd;

import javax.swing.JTextPane;
import javax.swing.text.StyledDocument;

import com.gamer.data.log.Log;

/**
 * 日志区进度块：在文档中维护一块可整体替换的 GD 进度文本，避免按行 offset 错位。
 */
public class ViewFixedLineLog {

    private final JTextPane logArea;
    private final Log fallbackLog;
    /** 进度块起始 offset（含） */
    private int blockStart = -1;
    /** 进度块结束 offset（不含） */
    private int blockEnd = -1;

    /**
     * @param logArea
     *            日志文本区
     * @param fallbackLog
     *            写入失败时的回退日志
     */
    public ViewFixedLineLog(JTextPane logArea, Log fallbackLog) {
        this.logArea = logArea;
        this.fallbackLog = fallbackLog;
    }

    /**
     * 整体替换进度块文本（无时间戳；多行以换行分隔）。
     *
     * @param text
     *            进度块全文
     */
    public void rewriteProgressBlock(String text) {
        if (logArea == null) {
            return;
        }
        String block = text == null ? "" : text;
        if (!block.isEmpty() && block.charAt(block.length() - 1) != '\n') {
            block = block + "\n";
        }
        try {
            StyledDocument doc = logArea.getStyledDocument();
            if (blockStart >= 0 && blockEnd > blockStart && blockEnd <= doc.getLength()) {
                doc.remove(blockStart, blockEnd - blockStart);
            } else {
                blockStart = doc.getLength();
            }
            doc.insertString(blockStart, block, null);
            blockEnd = blockStart + block.length();
            logArea.setCaretPosition(doc.getLength());
        } catch (Exception ex) {
            if (fallbackLog != null) {
                fallbackLog.logMessage("刷新进度块失败: " + ex.getMessage(), true);
            }
        }
    }
}
