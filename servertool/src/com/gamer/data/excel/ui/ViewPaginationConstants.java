package com.gamer.data.excel.ui;

/**
 * 预览面板分页常量。
 * 使用方：CodeTool {@link com.gamer.data.excel.browse.ExcelViewer} 的 Excel/GD 流式预览。
 */
public final class ViewPaginationConstants {

    /** 可选每页条数 */
    public static final Integer[] PAGE_SIZES = {100, 200, 500};

    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = PAGE_SIZES[0];

    private ViewPaginationConstants() {}
}
