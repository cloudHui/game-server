package com.gamer.data.excel.diff.ui.log;

import java.awt.Color;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.gamer.data.ui.ViewUi;

/**
 * 差异日志分段着色：{@code 修改 …：旧值 → 新值} 中旧值偏橙、新值绿色。
 */
public final class DiffLogHighlighter {

    /** 箭头（中文全角） */
    private static final String ARROW = " → ";
    /** 半角箭头兼容 */
    private static final String ARROW_ASCII = " -> ";
    /** 字段分隔冒号（全角） */
    private static final String COLON = "：";
    /** 时间戳格式 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Color COLOR_NORMAL = ViewUi.TITLE;
    private static final Color COLOR_OLD = new Color(180, 90, 0);
    private static final Color COLOR_NEW = new Color(0, 128, 0);

    private DiffLogHighlighter() {}

    /**
     * 若为差异行则分段写入并返回 true；否则返回 false（由调用方走普通日志）。
     *
     * @param logArea
     *            日志区
     * @param message
     *            不含时间戳的正文
     * @return 是否已写入
     */
    public static boolean tryAppendDiffLine(JTextPane logArea, String message) {
        if (logArea == null || message == null) {
            return false;
        }
        int arrowAt = message.indexOf(ARROW);
        String arrow = ARROW;
        if (arrowAt < 0) {
            arrowAt = message.indexOf(ARROW_ASCII);
            arrow = ARROW_ASCII;
        }
        if (arrowAt < 0) {
            return false;
        }
        int colonAt = message.lastIndexOf(COLON, arrowAt);
        if (colonAt < 0) {
            return false;
        }
        // 仅处理「修改…」类行（含「修改表头」）
        String trimmed = message.trim();
        if (!trimmed.startsWith("修改") && !trimmed.contains("修改 ")) {
            return false;
        }
        String prefix = "[" + LocalTime.now().format(TIME_FMT) + "] ";
        String head = message.substring(0, colonAt + COLON.length());
        String oldVal = message.substring(colonAt + COLON.length(), arrowAt);
        String newVal = message.substring(arrowAt + arrow.length());
        StyledDocument doc = logArea.getStyledDocument();
        try {
            insert(doc, logArea, prefix + head, COLOR_NORMAL, false);
            insert(doc, logArea, oldVal, COLOR_OLD, false);
            insert(doc, logArea, arrow, COLOR_NORMAL, false);
            insert(doc, logArea, newVal + "\n", COLOR_NEW, true);
            logArea.setCaretPosition(doc.getLength());
            return true;
        } catch (BadLocationException e) {
            return false;
        }
    }

    private static void insert(StyledDocument doc, JTextPane pane, String text, Color color, boolean bold)
        throws BadLocationException {
        Style style = pane.addStyle("diff-" + color.getRGB() + "-" + bold, null);
        StyleConstants.setFontFamily(style, ViewUi.FONT.getFamily());
        StyleConstants.setFontSize(style, ViewUi.FONT.getSize());
        StyleConstants.setForeground(style, color);
        StyleConstants.setBold(style, bold);
        doc.insertString(doc.getLength(), text, style);
    }
}
