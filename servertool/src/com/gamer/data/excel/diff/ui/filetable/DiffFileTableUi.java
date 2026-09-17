package com.gamer.data.excel.diff.ui.filetable;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.TableColumnModel;

import com.gamer.data.ui.ViewUi;

/**
 * 文件台账表格外观与双击。
 */
public final class DiffFileTableUi {

    private DiffFileTableUi() {}

    public static void configure(JTable table, DiffFileTableModel model) {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ViewUi.table(table);
        table.setAutoCreateRowSorter(false);
        // 文件名列吃满剩余宽度，去掉右侧空白
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        TableColumnModel cols = table.getColumnModel();
        if (model.getColumnCount() == 3) {
            cols.getColumn(0).setPreferredWidth(260);
            cols.getColumn(1).setPreferredWidth(48);
            cols.getColumn(1).setMaxWidth(64);
            cols.getColumn(2).setPreferredWidth(100);
            cols.getColumn(2).setMaxWidth(120);
        } else {
            cols.getColumn(0).setPreferredWidth(280);
            cols.getColumn(1).setPreferredWidth(100);
            cols.getColumn(1).setMaxWidth(120);
        }
    }

    public static void installDoubleClick(JTable table, DiffFileTableModel model, Consumer<String> onOpen) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2 || onOpen == null) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                String name = model.getFileName(row);
                if (name != null) {
                    onOpen.accept(name);
                }
            }
        });
    }

    public static JLabel createEmptyHintLabel(String text) {
        JLabel label = ViewUi.hint(text);
        label.setHorizontalAlignment(JLabel.CENTER);
        return label;
    }
}
