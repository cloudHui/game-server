package com.gamer.data.file.db;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * 列勾选对话框：默认全选，控制表格可见列。
 */
final class ColumnSelectDialog {

    private ColumnSelectDialog() {
    }

    /**
     * 弹出列选择框。
     *
     * @param owner
     *            父组件
     * @param allColumns
     *            全部列名
     * @param visibleColumns
     *            当前可见列（null 或空表示全选）
     * @return 用户确认的可见列列表，取消时 null
     */
    static List<String> show(Component owner, List<String> allColumns, List<String> visibleColumns) {
        Map<String, JCheckBox> boxes = new LinkedHashMap<>();
        JPanel checkPanel = new JPanel();
        checkPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 8, 4));
        for (String col : allColumns) {
            JCheckBox box = new JCheckBox(col, isChecked(col, visibleColumns, allColumns));
            boxes.put(col, box);
            checkPanel.add(box);
        }
        JDialog dialog = new JDialog(javax.swing.JOptionPane.getFrameForComponent(owner), "选择展示列", true);
        dialog.setLayout(new BorderLayout(6, 6));
        dialog.add(new JScrollPane(checkPanel), BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton allBtn = new JButton("全选");
        allBtn.addActionListener(e -> setAll(boxes, true));
        JButton noneBtn = new JButton("全不选");
        noneBtn.addActionListener(e -> setAll(boxes, false));
        JButton okBtn = new JButton("确定");
        JButton cancelBtn = new JButton("取消");
        final List<String>[] result = new List[1];
        okBtn.addActionListener(e -> {
            result[0] = collectSelected(boxes, allColumns);
            dialog.dispose();
        });
        cancelBtn.addActionListener(e -> {
            result[0] = null;
            dialog.dispose();
        });
        south.add(allBtn);
        south.add(noneBtn);
        south.add(okBtn);
        south.add(cancelBtn);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.setSize(480, 320);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return result[0];
    }

    /**
     * 默认全选；visible 非空时按 visible 勾选。
     */
    private static boolean isChecked(String col, List<String> visible, List<String> all) {
        if (visible == null || visible.isEmpty() || visible.size() >= all.size()) {
            return true;
        }
        return visible.contains(col);
    }

    private static void setAll(Map<String, JCheckBox> boxes, boolean selected) {
        for (JCheckBox box : boxes.values()) {
            box.setSelected(selected);
        }
    }

    private static List<String> collectSelected(Map<String, JCheckBox> boxes, List<String> order) {
        List<String> selected = new ArrayList<>();
        for (String col : order) {
            JCheckBox box = boxes.get(col);
            if (box != null && box.isSelected()) {
                selected.add(col);
            }
        }
        return selected;
    }
}
