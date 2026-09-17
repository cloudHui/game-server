package com.gamer.data.excel.ui;

import java.awt.FlowLayout;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

import com.gamer.data.ui.ViewUi;

/**
 * 通用分页控制器：负责一行分页控件的 UI 和交互。
 * 使用方：{@link PagedPreviewPanel}。
 */
public class PaginationController {

    /** 默认每页条数（构造入参） */
    public final int defaultSize;

    /** 翻页回调（参数为页码） */
    public final Consumer<Integer> pageChange;

    /** 每页条数变更回调 */
    public final Consumer<Integer> pageSizeChange;

    /** 当前分页控件允许选择的每页条数 */
    private final Integer[] pageSizes;

    /** 当前页码 */
    public int currentPage = 1;

    /** 每页条数 */
    public int pageSize;

    /** 总页数（探测模式下仅用于内部，UI 不展示） */
    public int totalPages = 1;

    public JLabel pageLabel;

    public JButton prevPageBtn;

    public JButton nextPageBtn;

    public JSpinner pageSpinner;

    public JComboBox<Integer> pageSizeComboBox;

    public JTable table;

    public DefaultTableModel tableModel;

    public JLabel rowLabel;

    public JLabel colLabel;

    public JScrollPane scrollPane;

    /** 探测模式：不显示总页数与跳转框 */
    private boolean probeOnlyMode;

    /** 跳转标签（探测模式下隐藏） */
    private JLabel jumpLabel;

    /**
     * @param defaultSize
     *            默认每页条数
     * @param pageChange
     *            翻页回调（参数为页码）
     * @param sizeChange
     *            每页条数变更回调
     */
    public PaginationController(int defaultSize, Consumer<Integer> pageChange, Consumer<Integer> sizeChange) {
        this(defaultSize, ViewPaginationConstants.PAGE_SIZES, pageChange, sizeChange);
    }

    /**
     * 创建可指定每页条数选项的分页控制器。
     *
     * @param defaultSize
     *            默认每页条数
     * @param pageSizes
     *            可选每页条数
     * @param pageChange
     *            翻页回调
     * @param sizeChange
     *            每页条数变更回调
     */
    public PaginationController(int defaultSize, Integer[] pageSizes, Consumer<Integer> pageChange,
        Consumer<Integer> sizeChange) {
        this.defaultSize = defaultSize;
        this.pageSizes = pageSizes.clone();
        this.pageChange = pageChange;
        this.pageSizeChange = sizeChange;
        this.pageSize = defaultSize;
    }

    /**
     * 设置是否为探测模式（须在 createPaginationPanel 之前调用）。
     *
     * @param probeOnly
     *            true 时不显示总页数与跳转
     */
    public void setProbeOnlyMode(boolean probeOnly) {
        this.probeOnlyMode = probeOnly;
    }

    /**
     * 创建分页控件行（每页大小、上一页、页码、下一页）。
     *
     * @return 分页控件面板
     */
    public JPanel createPaginationPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setOpaque(false);

        JLabel pageSizeLabel = ViewUi.label("每页:");
        pageSizeComboBox = new JComboBox<>(pageSizes);
        pageSizeComboBox.setSelectedItem(pageSize);
        ViewUi.compactCombo(pageSizeComboBox);
        pageSizeComboBox.addActionListener(e -> {
            Integer selected = (Integer) pageSizeComboBox.getSelectedItem();
            if (selected != null && pageSizeChange != null) {
                pageSizeChange.accept(selected);
            }
        });

        pageLabel = ViewUi.label(probeOnlyMode ? "第 1 页" : "第 1/1 页");
        prevPageBtn = ViewUi.style(new JButton("上一页"));
        prevPageBtn.setEnabled(false);
        prevPageBtn.addActionListener(e -> {
            if (currentPage > 1 && pageChange != null) {
                int newPage = currentPage - 1;
                pageChange.accept(newPage);
            }
        });

        pageSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1, 1));
        pageSpinner.addChangeListener(e -> {
            if (!probeOnlyMode && pageChange != null) {
                int page = (Integer) pageSpinner.getValue();
                pageChange.accept(page);
            }
        });

        nextPageBtn = ViewUi.style(new JButton("下一页"));
        nextPageBtn.setEnabled(false);
        nextPageBtn.addActionListener(e -> {
            if (pageChange != null) {
                int newPage = currentPage + 1;
                pageChange.accept(newPage);
            }
        });

        jumpLabel = ViewUi.label("跳转到:");
        panel.add(pageSizeLabel);
        panel.add(pageSizeComboBox);
        panel.add(Box.createHorizontalStrut(10));
        panel.add(prevPageBtn);
        panel.add(pageLabel);
        if (!probeOnlyMode) {
            panel.add(jumpLabel);
            panel.add(pageSpinner);
        }
        panel.add(nextPageBtn);
        return panel;
    }

    /**
     * 同步分页按钮、页码标签与跳转框状态（含总页数模式）。
     *
     * @param currentPage
     *            当前页码
     * @param totalPages
     *            总页数
     */
    public void updateControls(int currentPage, int totalPages) {
        this.currentPage = currentPage;
        this.totalPages = totalPages;

        pageLabel.setText(String.format("第 %d/%d 页", currentPage, totalPages));
        prevPageBtn.setEnabled(currentPage > 1);
        nextPageBtn.setEnabled(currentPage < totalPages);
        if (pageSpinner != null) {
            SpinnerNumberModel model = (SpinnerNumberModel) pageSpinner.getModel();
            model.setMaximum(totalPages);
            int old = (Integer) pageSpinner.getValue();
            if (old != currentPage) {
                pageSpinner.setValue(currentPage);
            }
        }
    }

    /**
     * 探测模式：仅根据 hasPrev / hasNext 更新按钮与页码标签。
     *
     * @param currentPage
     *            当前页码
     * @param hasPrev
     *            是否有上一页
     * @param hasNext
     *            是否有下一页
     */
    public void updateProbeControls(int currentPage, boolean hasPrev, boolean hasNext) {
        this.currentPage = currentPage;
        pageLabel.setText(String.format("第 %d 页", currentPage));
        prevPageBtn.setEnabled(hasPrev);
        nextPageBtn.setEnabled(hasNext);
    }

    /**
     * 设置每页条数并同步下拉框选中项。
     *
     * @param pageSize
     *            每页条数
     */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
        if (pageSizeComboBox != null) {
            pageSizeComboBox.setSelectedItem(pageSize);
        }
    }
}
