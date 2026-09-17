package com.gamer.data.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

/**
 * 表格、列表、树、复制菜单。
 */
final class ViewTables {

    private ViewTables() {}

    /**
     * 表格字体、网格与选中色。
     *
     * @param table
     *            表格
     */
    static void table(JTable table) {
        table.setFont(ViewPalette.FONT);
        table.setRowHeight(22);
        table.setShowGrid(true);
        table.setGridColor(ViewPalette.LINE);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setSelectionBackground(ViewPalette.SELECT);
        table.setSelectionForeground(ViewPalette.TITLE);
        table.setBackground(Color.WHITE);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(ViewPalette.FONT_B);
        table.getTableHeader().setForeground(ViewPalette.TITLE);
        table.getTableHeader().setBackground(ViewPalette.STATUS);
    }

    /**
     * 只读表格：整行选中，Ctrl+C / 右键复制当前行。
     *
     * @param table
     *            表格
     */
    static void enableRowCopy(JTable table) {
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        table.setCellSelectionEnabled(false);
        bindCopy(table);
        selectOnRightPress(table, false);
    }

    /**
     * 只读表格：按格选中，Ctrl+C / 右键复制，不写模型。
     *
     * @param table
     *            表格
     */
    static void enableCellCopy(JTable table) {
        table.setCellSelectionEnabled(true);
        bindCopy(table);
        selectOnRightPress(table, true);
    }

    /**
     * 右键按下时选中目标格或行，避免菜单弹出时仍是上一处选区。
     *
     * @param table
     *            表格
     * @param cell
     *            true 按格，false 按行
     */
    private static void selectOnRightPress(final JTable table, final boolean cell) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isRightMouseButton(e)) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                if (cell) {
                    int col = table.columnAtPoint(e.getPoint());
                    if (row >= 0 && col >= 0 && !table.isCellSelected(row, col)) {
                        table.changeSelection(row, col, false, false);
                    }
                } else if (row >= 0 && !table.isRowSelected(row)) {
                    table.setRowSelectionInterval(row, row);
                }
            }
        });
    }

    /**
     * 绑定 Ctrl+C 与右键「复制」到系统 TransferHandler。
     *
     * @param table
     *            表格
     */
    private static void bindCopy(final JTable table) {
        table.getInputMap().put(KeyStroke.getKeyStroke("ctrl C"), "copy");
        table.getActionMap().put("copy", TransferHandler.getCopyAction());
        popupCopy(table, () -> TransferHandler.getCopyAction()
            .actionPerformed(new ActionEvent(table, ActionEvent.ACTION_PERFORMED, "copy")));
    }

    /**
     * 文本区右键复制：有选区则复制选区，否则全选复制。
     *
     * @param area
     *            文本区
     */
    static void enableTextCopy(final JTextArea area) {
        popupCopy(area, () -> {
            if (area.getSelectedText() == null || area.getSelectedText().isEmpty()) {
                area.selectAll();
                area.copy();
                area.setCaretPosition(0);
            } else {
                area.copy();
            }
        });
    }

    /**
     * 右键「复制」菜单。
     *
     * @param target
     *            控件
     * @param copy
     *            复制动作
     */
    static void popupCopy(JComponent target, final Runnable copy) {
        JMenuItem item = new JMenuItem("复制");
        item.setFont(ViewPalette.FONT);
        item.addActionListener(e -> copy.run());
        JPopupMenu menu = new JPopupMenu();
        menu.add(item);
        target.setComponentPopupMenu(menu);
    }

    /**
     * 树字体、行高、白底。
     *
     * @param tree
     *            树
     */
    static void tree(JTree tree) {
        tree.setFont(ViewPalette.FONT);
        tree.setRowHeight(22);
        tree.setBackground(Color.WHITE);
        tree.setOpaque(true);
    }

    /**
     * 列表字体、行高、白底与选中色。
     *
     * @param list
     *            列表
     */
    static void list(JList<?> list) {
        list.setFont(ViewPalette.FONT);
        list.setFixedCellHeight(22);
        list.setBackground(Color.WHITE);
        list.setOpaque(true);
        list.setSelectionBackground(ViewPalette.SELECT);
        list.setSelectionForeground(ViewPalette.TITLE);
    }
}
