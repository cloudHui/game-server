package com.gamer.data.excel.ui;

import com.gamer.data.excel.core.PagedTableSource;

/**
 * 单块流式预览面板的数据状态（列名 + 分页源）。
 * 使用方：{@link PagedPreviewPanel}，由 CodeTool Excel/GD 看表共用。
 */
public class PagedPreviewState {

    /** 当前 Sheet 列显示名 */
    private String[] columnNames;

    /** 流式分页数据源，null 表示无数据 */
    private PagedTableSource pagedSource;

    /**
     * 重置状态（切换 Sheet 或文件时调用）。
     */
    public void clear() {
        columnNames = null;
        pagedSource = null;
    }

    /**
     * 绑定列名与流式数据源。
     *
     * @param columnNames
     *            列显示名
     * @param pagedSource
     *            分页数据源
     */
    public void bind(String[] columnNames, PagedTableSource pagedSource) {
        this.columnNames = columnNames;
        this.pagedSource = pagedSource;
    }

    /**
     * 是否已有可分页数据。
     *
     * @return 列名非空且数据源非空
     */
    public boolean hasNoData() {
        return columnNames == null || pagedSource == null;
    }

    /**
     * 数据区总行数；无数据源时返回 0。
     *
     * @return 总行数
     */
    public int getTotalRows() {
        if (pagedSource == null) {
            return 0;
        }
        return pagedSource.getTotalRows();
    }

    /**
     * @return 列显示名，可能为 null
     */
    public String[] getColumnNames() {
        return columnNames;
    }

    /**
     * @return 流式分页源，可能为 null
     */
    public PagedTableSource getPagedSource() {
        return pagedSource;
    }
}
