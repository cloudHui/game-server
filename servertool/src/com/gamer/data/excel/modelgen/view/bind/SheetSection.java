package com.gamer.data.excel.modelgen.view.bind;

import java.awt.FlowLayout;
import java.awt.Container;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.gamer.data.excel.modelgen.binding.ColumnBindingRow;
import com.gamer.data.excel.modelgen.binding.ColumnLengthPairDedup;
import com.gamer.data.ui.ViewUi;

/**
 * 单个 Sheet 的列长度绑定区块（全部展开展示，无需下拉切换）。
 */
public class SheetSection extends JPanel {

    /** Excel 文件名 */
    private final String excelName;

    /** Sheet 名 */
    private final String sheetName;

    /** 列名列表 */
    private final List<String> columnNames;

    /** 绑定行数据 */
    private final List<ColumnBindingRow> rows;

    /** 去重日志 */
    private final ColumnLengthPairDedup.DedupLog dedupLog;

    /** 绑定行容器 */
    private final JPanel rowsPanel = new JPanel();

    /** 行 UI 控件 */
    private final List<RowWidgets> rowWidgets = new ArrayList<>();

    /** 刷新 UI 或程序设值下拉时屏蔽 ActionListener，避免误触发去重 */
    private boolean suppressComboEvents = false;

    /**
     * 构造单个 Sheet 绑定区块。
     *
     * @param excelName
     *            Excel 文件名
     * @param sheetName
     *            Sheet 名
     * @param columnNames
     *            列名列表
     * @param rows
     *            绑定行数据（与 state 共享引用）
     * @param dedupLog
     *            去重日志回调
     */
    public SheetSection(String excelName, String sheetName, List<String> columnNames, List<ColumnBindingRow> rows,
        ColumnLengthPairDedup.DedupLog dedupLog) {
        setLayout(BindLayout.create());
        this.excelName = excelName;
        this.sheetName = sheetName;
        this.columnNames = columnNames;
        this.rows = rows;
        this.dedupLog = dedupLog;
        initHeader();
        initRowsArea();
        ColumnLengthPairDedup.dedupeRows(rows, excelName, sheetName, dedupLog);
        refreshRows();
    }

    /**
     * 初始化表名、Sheet 名与添加按钮。
     */
    private void initHeader() {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        header.setOpaque(false);
        JLabel excelLabel = ViewUi.label(excelName);
        excelLabel.setFont(ViewUi.FONT_B);
        header.add(excelLabel);
        header.add(ViewUi.label(sheetName));
        header.add(ViewUi.click("添加列对", this::addEmptyRow));
        add(header);
    }

    /**
     * 初始化绑定行区域。
     */
    private void initRowsArea() {
        rowsPanel.setLayout(BindLayout.create());
        add(rowsPanel);
    }

    /**
     * 保存 UI 编辑到数据对象并去重。
     */
    public void flushAll() {
        syncAllWidgetsToRows();
        dedupeRowsAndRefresh();
    }

    /**
     * 同步全部行 UI 到数据对象。
     */
    private void syncAllWidgetsToRows() {
        for (RowWidgets rowWidget : rowWidgets) {
            syncWidgetToRow(rowWidget);
        }
    }

    /**
     * 同步单行 UI 到数据对象。
     */
    private void syncWidgetToRow(RowWidgets widgets) {
        widgets.rowRef.colA = widgets.getColA();
        widgets.rowRef.colB = widgets.getColB();
    }

    /**
     * 去重后按需刷新 UI。
     */
    private void dedupeRowsAndRefresh() {
        syncAllWidgetsToRows();
        int removed = ColumnLengthPairDedup.dedupeRows(rows, excelName, sheetName, dedupLog);
        if (removed > 0) {
            pruneOrphanRowWidgets();
        } else {
            refreshRows();
        }
    }

    /**
     * 去重后移除已无数据引用的行控件。
     */
    private void pruneOrphanRowWidgets() {
        Iterator<RowWidgets> it = rowWidgets.iterator();
        while (it.hasNext()) {
            RowWidgets widgets = it.next();
            if (!rows.contains(widgets.rowRef)) {
                rowsPanel.remove(widgets.linePanel);
                it.remove();
            }
        }
        repaintRowsPanel();
    }

    /**
     * 刷新全部绑定行 UI。
     */
    private void refreshRows() {
        suppressComboEvents = true;
        try {
            rowsPanel.removeAll();
            rowWidgets.clear();
            for (ColumnBindingRow row : rows) {
                addRowWidget(row);
            }
            repaintRowsPanel();
        } finally {
            suppressComboEvents = false;
        }
    }

    /**
     * 触发行区域重绘，并向上刷新父容器高度。
     */
    private void repaintRowsPanel() {
        rowsPanel.revalidate();
        rowsPanel.repaint();
        revalidate();
        repaint();
        revalidateParentChain(getParent());
    }

    private static void revalidateParentChain(Container parent) {
        Container current = parent;
        while (current != null) {
            current.revalidate();
            current = current.getParent();
        }
    }

    /**
     * 添加一行空绑定。
     */
    private void addEmptyRow() {
        syncAllWidgetsToRows();
        ColumnBindingRow row = buildDefaultRow();
        rows.add(row);
        int removed = ColumnLengthPairDedup.dedupeRows(rows, excelName, sheetName, dedupLog);
        if (removed > 0) {
            refreshRows();
            return;
        }
        addRowWidget(row);
        repaintRowsPanel();
    }

    private ColumnBindingRow buildDefaultRow() {
        return new ColumnBindingRow("", "");
    }

    private void addRowWidget(ColumnBindingRow row) {
        rowsPanel.add(buildRowPanel(row));
    }

    private JPanel buildRowPanel(ColumnBindingRow row) {
        JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        String[] colArray = columnNames.toArray(new String[0]);
        JComboBox<String> colA = new JComboBox<>(new DefaultComboBoxModel<>(colArray));
        JComboBox<String> colB = new JComboBox<>(new DefaultComboBoxModel<>(colArray));
        selectComboValue(colA, row.colA);
        selectComboValue(colB, row.colB);
        RowWidgets widgets = new RowWidgets(row, colA, colB, line);
        rowWidgets.add(widgets);
        attachDedupListener(colA);
        attachDedupListener(colB);
        line.add(colA);
        line.add(colB);
        line.add(buildRemoveButton(widgets));
        return line;
    }

    private void attachDedupListener(JComboBox<String> combo) {
        combo.addActionListener(e -> {
            if (suppressComboEvents) {
                return;
            }
            dedupeRowsAndRefresh();
        });
    }

    private JButton buildRemoveButton(final RowWidgets target) {
        JButton removeBtn = ViewUi.style(new JButton("删"));
        removeBtn.addActionListener(e -> removeRowWidget(target));
        return removeBtn;
    }

    /**
     * 删除指定行（局部移除 UI，不重建全部下拉）。
     */
    private void removeRowWidget(RowWidgets target) {
        syncWidgetToRow(target);
        rows.remove(target.rowRef);
        rowsPanel.remove(target.linePanel);
        rowWidgets.remove(target);
        repaintRowsPanel();
    }

    private static void selectComboValue(JComboBox<String> combo, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        combo.setSelectedItem(value);
        if (combo.getSelectedItem() == null) {
            combo.addItem(value);
            combo.setSelectedItem(value);
        }
    }

    /**
     * 单行 UI 控件。
     */
    private static final class RowWidgets {

        private final ColumnBindingRow rowRef;
        private final JComboBox<String> colA;
        private final JComboBox<String> colB;
        private final JPanel linePanel;

        private RowWidgets(ColumnBindingRow rowRef, JComboBox<String> colA, JComboBox<String> colB, JPanel linePanel) {
            this.rowRef = rowRef;
            this.colA = colA;
            this.colB = colB;
            this.linePanel = linePanel;
        }

        private String getColA() {
            Object v = colA.getSelectedItem();
            return v != null ? v.toString() : "";
        }

        private String getColB() {
            Object v = colB.getSelectedItem();
            return v != null ? v.toString() : "";
        }
    }
}
