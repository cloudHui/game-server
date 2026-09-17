package com.gamer.data.excel.core;

import java.util.Collections;
import java.util.List;

import com.gamer.data.excel.ui.PagedPreviewPanel;

/**
 * 分页结果：当前页行、可选总行数、是否还有下一页。
 * 使用方：{@link PagedTableSource}、{@link PagedPreviewPanel}。
 */
public class PagedTablePage {

    /** 当前页数据行 */
    private final List<Object[]> rows;

    /** 数据区总行数；未知时为 {@link #UNKNOWN_TOTAL_ROWS} */
    private final int totalRows;

    /** 当前页之后是否还有数据行（Excel 探测模式） */
    private final boolean hasNextPage;

    /** 总行数未知（Excel 1a 不扫全表） */
    public static final int UNKNOWN_TOTAL_ROWS = -1;

    /**
     * @param rows
     *            当前页数据行
     * @param totalRows
     *            数据区总行数，未知传 {@link #UNKNOWN_TOTAL_ROWS}
     */
    public PagedTablePage(List<Object[]> rows, int totalRows) {
        this(rows, totalRows, false);
    }

    /**
     * @param rows
     *            当前页数据行
     * @param totalRows
     *            数据区总行数，未知传 {@link #UNKNOWN_TOTAL_ROWS}
     * @param hasNextPage
     *            当前页之后是否还有数据
     */
    public PagedTablePage(List<Object[]> rows, int totalRows, boolean hasNextPage) {
        this.rows = rows == null ? Collections.emptyList() : rows;
        this.totalRows = totalRows;
        this.hasNextPage = hasNextPage;
    }

    /**
     * @return 当前页行
     */
    public List<Object[]> getRows() {
        return rows;
    }

    /**
     * @return 总行数，未知时为 {@link #UNKNOWN_TOTAL_ROWS}
     */
    public int getTotalRows() {
        return totalRows;
    }

    /**
     * @return 是否还有下一页数据
     */
    public boolean isHasNextPage() {
        return hasNextPage;
    }
}
