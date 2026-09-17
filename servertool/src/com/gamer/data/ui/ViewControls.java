package com.gamer.data.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.plaf.basic.BasicButtonUI;

/**
 * 按钮、标签、勾选、下拉、输入框。
 */
final class ViewControls {

    private ViewControls() {}

    /**
     * 统一按钮：不抢焦点、手型、留白一致。
     *
     * @param button
     *            按钮
     * @return 原按钮
     */
    static JButton style(JButton button) {
        button.setFont(ViewPalette.FONT);
        button.setFocusable(false);
        button.setMargin(new Insets(4, 10, 4, 10));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /**
     * 带点击动作的按钮。
     *
     * @param text
     *            文案
     * @param action
     *            点击
     * @return 按钮
     */
    static JButton click(String text, final Runnable action) {
        JButton button = style(new JButton(text));
        button.addActionListener(e -> action.run());
        return button;
    }

    /**
     * 实心底按钮。Windows 系统外观会盖住 JButton 背景，改用 BasicButtonUI。
     *
     * @param button
     *            已 style 的按钮
     * @param background
     *            底色
     * @param foreground
     *            字色
     * @return 原按钮
     */
    static JButton fill(JButton button, Color background, Color foreground) {
        button.setUI(new BasicButtonUI());
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFont(ViewPalette.FONT_B);
        button.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        return button;
    }

    /**
     * 主操作按钮：蓝底白字。
     *
     * @param button
     *            已 style 的按钮
     * @return 原按钮
     */
    static JButton primary(JButton button) {
        return fill(button, ViewPalette.LINK, Color.WHITE);
    }

    /**
     * 次要实心按钮：琥珀底白字。
     *
     * @param button
     *            已 style 的按钮
     * @return 原按钮
     */
    static JButton warn(JButton button) {
        return fill(button, ViewPalette.WARN, Color.WHITE);
    }

    /**
     * 结束实心按钮：红底白字。
     *
     * @param button
     *            已 style 的按钮
     * @return 原按钮
     */
    static JButton halt(JButton button) {
        return fill(button, ViewPalette.HALT, Color.WHITE);
    }

    /**
     * 危险操作按钮。
     *
     * @param button
     *            已 style 的按钮
     * @return 原按钮
     */
    static JButton danger(JButton button) {
        button.setForeground(ViewPalette.DANGER);
        return button;
    }

    /**
     * 勾选框。
     *
     * @param text
     *            文案
     * @param on
     *            初始
     * @return 勾选框
     */
    static JCheckBox check(String text, boolean on) {
        JCheckBox box = new JCheckBox(text, on);
        box.setFont(ViewPalette.FONT);
        box.setOpaque(false);
        box.setForeground(ViewPalette.TITLE);
        return box;
    }

    /**
     * 勾选框，切换时回调。
     *
     * @param text
     *            文案
     * @param on
     *            初始
     * @param set
     *            切换
     * @return 勾选框
     */
    static JCheckBox check(String text, boolean on, Consumer<Boolean> set) {
        JCheckBox box = check(text, on);
        box.addActionListener(e -> set.accept(box.isSelected()));
        return box;
    }

    /**
     * 分组标签。
     *
     * @param text
     *            文案
     * @return 标签
     */
    static JLabel label(String text) {
        return label(new JLabel(text));
    }

    /**
     * 已有标签：正文字体与标题色。
     *
     * @param label
     *            标签
     * @return 原标签
     */
    static JLabel label(JLabel label) {
        label.setFont(ViewPalette.FONT);
        label.setForeground(ViewPalette.TITLE);
        return label;
    }

    /**
     * 底部提示。
     *
     * @param text
     *            文案
     * @return 标签
     */
    static JLabel hint(String text) {
        JLabel label = new JLabel(text);
        label.setFont(ViewPalette.FONT);
        label.setForeground(ViewPalette.HINT);
        return label;
    }

    /**
     * 点击可编辑下拉框任意区域时展开列表。
     *
     * @param combo
     *            下拉框
     */
    static void popupOnClick(final JComboBox<?> combo) {
        MouseAdapter opener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (combo.isEnabled() && !combo.isPopupVisible()) {
                    combo.showPopup();
                }
            }
        };
        combo.addMouseListener(opener);
        combo.getEditor().getEditorComponent().addMouseListener(opener);
    }

    /**
     * BoxLayout 行里让控件吃掉剩余宽度。
     *
     * @param field
     *            输入框
     * @return 原控件
     */
    static JTextField fillX(JTextField field) {
        Dimension pref = field.getPreferredSize();
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(pref.height, 26)));
        return field;
    }

    /**
     * 下拉框与筛选行对齐。
     *
     * @param combo
     *            下拉框
     */
    static void compactCombo(JComboBox<?> combo) {
        combo.setFont(ViewPalette.FONT);
        combo.setPreferredSize(new Dimension(Math.max(combo.getPreferredSize().width, 72), 26));
    }

    /**
     * 输入框统一高度，避免和按钮错位。
     *
     * @param field
     *            输入框
     */
    static void compactField(JTextField field) {
        field.setFont(ViewPalette.FONT);
        field.setPreferredSize(new Dimension(Math.max(field.getPreferredSize().width, 48), 26));
    }

    /**
     * BoxLayout 下未 fillX 的控件锁死首选宽，避免互相抢宽。
     *
     * @param jc
     *            控件
     */
    static void capBox(JComponent jc) {
        if (jc.getMaximumSize().width == Integer.MAX_VALUE) {
            return;
        }
        Dimension pref = jc.getPreferredSize();
        jc.setMaximumSize(new Dimension(pref.width, Math.max(pref.height, 26)));
    }
}
