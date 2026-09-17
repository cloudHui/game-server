package com.gamer.data.excel.ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.Box;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import com.gamer.data.excel.core.PagedTablePage;
import com.gamer.data.excel.core.PagedTableSource;
import com.gamer.data.ui.ViewUi;

/**
 * 流式分页预览面板：JTable + 分页条 + 按页读取展示。
 * 使用方：CodeTool {@link com.gamer.data.excel.browse.ExcelViewer}、{@link com.gamer.data.excel.browse.GdViewerFrame}。
 */
public class PagedPreviewPanel {

    /** 预览数据状态 */
    private final PagedPreviewState state = new PagedPreviewState();

    /** 分页 UI 控制器 */
    private final PaginationController pagination;

    /** 面板标题 */
    private final String panelTitle;

    /** 分页展示模式 */
    private final PagedPreviewPanelMode mode;

    /** 读取失败等日志回调 */
    private final Consumer<String> logConsumer;

    /** 根面板 */
    private JPanel rootPanel;

    /**
     * @param panelTitle
     *            面板标题
     * @param logConsumer
     *            日志输出回调，可为 null
     * @param mode
     *            分页模式
     */
    public PagedPreviewPanel(String panelTitle, Consumer<String> logConsumer, PagedPreviewPanelMode mode) {
        this.panelTitle = panelTitle;
        this.logConsumer = logConsumer;
        this.mode = mode == null ? PagedPreviewPanelMode.WITH_TOTAL : mode;
        this.pagination = new PaginationController(ViewPaginationConstants.DEFAULT_PAGE_SIZE, this::showPage,
            this::changePageSize);
        this.pagination.setProbeOnlyMode(this.mode == PagedPreviewPanelMode.PROBE_ONLY);
    }

    /**
     * 创建带标题、行数/列数标签、分页条的完整 JPanel。
     *
     * @return 可嵌入容器的面板
     */
    public JPanel createView() {
        pagination.tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return String.class;
            }
        };

        JTable table = new JTable(pagination.tableModel);
        ViewUi.table(table);
        ViewUi.enableRowCopy(table);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        pagination.table = table;

        JScrollPane scrollPane = ViewUi.scroll(table);
        pagination.scrollPane = scrollPane;

        JLabel rowLabel = ViewUi.label("行数: 0");
        JLabel colLabel = ViewUi.label("列数: 0");
        pagination.rowLabel = rowLabel;
        pagination.colLabel = colLabel;

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        statsPanel.setOpaque(false);
        statsPanel.add(rowLabel);
        statsPanel.add(Box.createHorizontalStrut(20));
        statsPanel.add(colLabel);
        headerPanel.add(statsPanel, BorderLayout.WEST);
        headerPanel.add(pagination.createPaginationPanel(), BorderLayout.EAST);

        JPanel body = new JPanel(new BorderLayout(5, 5));
        body.setOpaque(false);
        body.add(headerPanel, BorderLayout.NORTH);
        body.add(scrollPane, BorderLayout.CENTER);
        rootPanel = ViewUi.card(panelTitle, body);
        return rootPanel;
    }

    /**
     * 面板是否已创建。
     *
     * @return createView 已调用
     */
    public boolean isViewCreated() {
        return rootPanel != null && pagination.tableModel != null;
    }

    /**
     * 绑定数据源并显示第 1 页。
     *
     * @param columnNames
     *            列名
     * @param source
     *            流式分页源
     */
    public void bindSource(String[] columnNames, PagedTableSource source) {
        state.bind(columnNames, source);
        resetPaginationControls();
        showPage(1);
    }

    /**
     * 清空表格与分页状态。
     */
    public void clear() {
        state.clear();
        if (pagination.tableModel != null) {
            pagination.tableModel.setRowCount(0);
            pagination.tableModel.setColumnCount(0);
        }
        if (pagination.rowLabel != null) {
            pagination.rowLabel.setText("行数: 0");
        }
        if (pagination.colLabel != null) {
            pagination.colLabel.setText("列数: 0");
        }
        resetPaginationControls();
    }

    /**
     * 显示指定页。
     *
     * @param page
     *            页码（从 1 开始）
     */
    public void showPage(int page) {
        if (state.hasNoData() || pagination.tableModel == null || pagination.table == null) {
            return;
        }
        try {
            if (mode == PagedPreviewPanelMode.PROBE_ONLY) {
                showProbePage(page);
            } else {
                showTotalPage(page);
            }
        } catch (Exception ex) {
            log("分页读取失败: " + ex.getMessage());
        }
    }

    /**
     * 修改每页条数并回到第 1 页。
     *
     * @param newPageSize
     *            新的每页行数
     */
    public void changePageSize(int newPageSize) {
        if (state.hasNoData()) {
            return;
        }
        pagination.setPageSize(newPageSize);
        showPage(1);
    }

    /**
     * 重置分页控件为初始态。
     */
    public void resetPaginationControls() {
        pagination.currentPage = 1;
        pagination.totalPages = 1;
        if (mode == PagedPreviewPanelMode.PROBE_ONLY) {
            pagination.updateProbeControls(1, false, false);
        } else {
            if (pagination.pageSpinner != null) {
                pagination.pageSpinner.setValue(1);
            }
            pagination.updateControls(1, 1);
        }
    }

    /**
     * 含总页数模式：读页并展示。
     *
     * @param page
     *            页码
     * @throws Exception
     *             读取失败
     */
    private void showTotalPage(int page) throws Exception {
        int totalRows = state.getTotalRows();
        int totalPages = calculateTotalPages(totalRows, pagination.pageSize);
        page = clampPage(page, totalPages);
        PagedTablePage pg = state.getPagedSource().readPage(page, pagination.pageSize);
        fillTable(pg.getRows(), page, totalRows, totalPages);
        pagination.currentPage = page;
        pagination.totalPages = totalPages;
        if (pagination.pageSpinner != null) {
            pagination.pageSpinner.setValue(page);
        }
        pagination.updateControls(page, totalPages);
    }

    /**
     * 探测模式：读页并根据 hasNext 更新按钮，不展示总页数。
     *
     * @param page
     *            页码
     * @throws Exception
     *             读取失败
     */
    private void showProbePage(int page) throws Exception {
        if (page < 1) {
            page = 1;
        }
        PagedTablePage pg = state.getPagedSource().readPage(page, pagination.pageSize);
        boolean hasPrev = page > 1;
        boolean hasNext = pg.isHasNextPage();
        fillTable(pg.getRows(), page, PagedTablePage.UNKNOWN_TOTAL_ROWS, 0);
        updateProbeStatsLabels(page, pg.getRows().size());
        pagination.updateProbeControls(page, hasPrev, hasNext);
    }

    /**
     * 将一页数据填入表格。
     *
     * @param rows
     *            当前页行
     * @param page
     *            当前页码
     * @param totalRows
     *            总行数（探测模式为未知）
     * @param totalPages
     *            总页数（探测模式忽略）
     */
    private void fillTable(List<Object[]> rows, int page, int totalRows, int totalPages) {
        String[] columnNames = state.getColumnNames();
        pagination.tableModel.setRowCount(0);
        pagination.tableModel.setColumnCount(columnNames.length);
        pagination.tableModel.setColumnIdentifiers(columnNames);

        int startIndex = (page - 1) * pagination.pageSize;
        pagination.table.putClientProperty("startRow", startIndex);
        for (Object[] row : rows) {
            pagination.tableModel.addRow(row);
        }

        if (mode == PagedPreviewPanelMode.WITH_TOTAL) {
            updateTotalStatsLabels(totalRows, page, totalPages);
        }

        for (int i = 0; i < columnNames.length; i++) {
            pagination.table.getColumnModel().getColumn(i).setPreferredWidth(150);
        }
    }

    /**
     * 含总页数模式的行数标签。
     */
    private void updateTotalStatsLabels(int totalRows, int page, int totalPages) {
        if (pagination.rowLabel != null) {
            pagination.rowLabel.setText(String.format("行数: %d (第 %d/%d 页)", totalRows, page, totalPages));
        }
        if (pagination.colLabel != null && state.getColumnNames() != null) {
            pagination.colLabel.setText("列数: " + state.getColumnNames().length);
        }
    }

    /**
     * 探测模式的行数标签（无总行数）。
     */
    private void updateProbeStatsLabels(int page, int rowCount) {
        if (pagination.rowLabel != null) {
            pagination.rowLabel.setText(String.format("第 %d 页 (%d 行)", page, rowCount));
        }
        if (pagination.colLabel != null && state.getColumnNames() != null) {
            pagination.colLabel.setText("列数: " + state.getColumnNames().length);
        }
    }

    /**
     * 按总行数与每页条数计算总页数。
     */
    private int calculateTotalPages(int totalRows, int pageSize) {
        if (totalRows <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalRows / pageSize);
    }

    /**
     * 将页码限制在合法区间。
     */
    private int clampPage(int page, int totalPages) {
        return Math.max(1, Math.min(page, totalPages));
    }

    /**
     * 输出日志。
     */
    private void log(String message) {
        if (logConsumer != null) {
            logConsumer.accept(message);
        }
    }
}
