package com.gamer.data.map.ui;

import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTextField;

/**
 * 地图查看器 UI 通用工具。
 */
public final class MapUiUtils {

    /** 世界坐标输入提示：x,z */
    public static final String POINT_FIELD_TOOLTIP = "格式: x,z 例: 125,445";

    private MapUiUtils() {
    }

    /**
     * 统一左侧工具区的小按钮边距。
     *
     * @param button
     *            按钮
     */
    public static void compactButton(JButton button) {
        button.setMargin(new Insets(2, 8, 2, 8));
    }

    /**
     * 聚焦时全选文本。
     *
     * @param field
     *            文本框
     */
    public static void addSelectAllOnFocus(final JTextField field) {
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.selectAll();
            }
        });
    }

    /**
     * 创建世界坐标输入框（格式 x,z）。
     *
     * @param columns
     *            列宽
     * @return 文本框
     */
    public static JTextField createPointField(int columns) {
        JTextField field = new JTextField(columns);
        field.setToolTipText(POINT_FIELD_TOOLTIP);
        addSelectAllOnFocus(field);
        return field;
    }

    /**
     * 格式化为 x,z。
     *
     * @param worldX
     *            世界 X
     * @param worldZ
     *            世界 Z
     * @return 文本
     */
    public static String formatPoint(int worldX, int worldZ) {
        return worldX + "," + worldZ;
    }

    /**
     * 解析 x,z；失败返回 null。
     *
     * @param text
     *            输入文本
     * @return [x, z]，失败为 null
     */
    public static int[] parsePoint(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] parts = trimmed.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new int[] {Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析字符串为整数，失败或空串返回 0。
     *
     * @param s
     *            字符串
     * @return 整数
     */
    public static int parseInt(String s) {
        if (s == null) {
            return 0;
        }
        s = s.trim();
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 左对齐 FlowLayout 行的对齐方式。
     *
     * @param row
     *            行面板
     */
    public static void alignRowLeft(JComponent row) {
        row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
    }
}
