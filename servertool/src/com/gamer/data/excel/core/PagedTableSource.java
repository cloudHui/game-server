package com.gamer.data.excel.core;

/**
 * 统一分页数据源（Excel SAX / GD 流式）。
 */
public interface PagedTableSource {

    /**
     * @return 列显示名（含类型说明）
     */
    String[] getColumnHeaders();

    /**
     * @return 数据区总行数；未知时返回 {@link PagedTablePage#UNKNOWN_TOTAL_ROWS}
     */
    int getTotalRows();

    /**
     * 读取一页数据（pageIndex 从 1 开始）。
     *
     * @param pageIndex 页码
     * @param pageSize 每页行数
     * @return 分页结果
     * @throws Exception 读取失败
     */
    PagedTablePage readPage(int pageIndex, int pageSize) throws Exception;
}
