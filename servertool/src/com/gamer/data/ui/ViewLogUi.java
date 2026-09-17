package com.gamer.data.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

/**
 * 日志区、状态条、EDT 同步。
 */
final class ViewLogUi {

    private ViewLogUi() {}

    /**
     * 日志区上方状态条。
     *
     * @param label
     *            标签
     */
    static void statusBar(JLabel label) {
        label.setFont(ViewPalette.FONT);
        label.setOpaque(true);
        label.setBackground(ViewPalette.STATUS);
        label.setForeground(ViewPalette.TITLE);
        label.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
    }

    /**
     * 只读日志文本区。
     *
     * @param area
     *            文本区
     */
    static void log(JTextArea area) {
        area.setFont(ViewPalette.FONT);
        area.setBackground(ViewPalette.LOG);
        area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
    }

    /**
     * 只读富文本日志（StrategyTool / CodeTool 带颜色时间戳）。
     *
     * @param pane
     *            文本窗格
     */
    static void logPane(JTextPane pane) {
        pane.setEditable(false);
        pane.setFont(ViewPalette.FONT);
        pane.setBackground(ViewPalette.LOG);
        pane.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
    }

    /**
     * 在 EDT 同步执行 Swing 控件读写。
     *
     * @param runnable
     *            动作
     */
    static void edtWait(Runnable runnable) {
        if (SwingUtilities.isEventDispatchThread()) {
            runnable.run();
            return;
        }
        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (Exception e) {
            throw new IllegalStateException("EDT 执行失败", e);
        }
    }
}
