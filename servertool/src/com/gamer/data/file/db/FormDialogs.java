package com.gamer.data.file.db;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;

import com.gamer.data.ui.ViewUi;

/**
 * 数据库 Tab 详情对话框。
 */
final class FormDialogs {

    private FormDialogs() {
    }

    /**
     * 展示 BLOB / 单元格详情（只读可复制）。
     *
     * @param owner
     *            父组件
     * @param title
     *            标题
     * @param text
     *            详情文本
     */
    static void showDetailDialog(Component owner, String title, String text) {
        JDialog dialog = new JDialog(JOptionPane.getFrameForComponent(owner), title, false);
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        ViewUi.log(area);
        ViewUi.enableTextCopy(area);
        JPanel root = new JPanel(new BorderLayout(8, 8));
        ViewUi.page(root);
        root.add(ViewUi.scroll(area), BorderLayout.CENTER);
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        south.setOpaque(false);
        south.add(ViewUi.click("关闭", dialog::dispose));
        root.add(south, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.setSize(new Dimension(720, 480));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }
}
