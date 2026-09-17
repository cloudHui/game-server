package com.gamer.data.excel.core;

import java.io.File;

import com.gamer.data.gdg.excel.XlsxStyles;

/**
 * xlsx 分页数据源（跳过表头 5 行后按页取数据行，StAX 跳行优化深页翻页）。
 * <p>
 * 使用方：CodeTool {@link com.gamer.data.excel.browse.ExcelViewer} Excel 预览。
 */
public class SaxPagedExcelSource implements PagedTableSource {

    private final File file;

    private final String sheetName;

    private final String[] columnHeaders;

    /** 样式读取失败回调，可为 null */
    private final XlsxStyles.Warn styleWarn;

    /**
     * 仅绑定文件与列名，不统计总行数。
     *
     * @param file
     *            xlsx 文件
     * @param sheetName
     *            工作表名
     * @param columnHeaders
     *            列显示名
     */
    public SaxPagedExcelSource(File file, String sheetName, String[] columnHeaders) throws Exception {
        this(file, sheetName, columnHeaders, null);
    }

    /**
     * 仅绑定文件与列名，不统计总行数。
     *
     * @param file
     *            xlsx 文件
     * @param sheetName
     *            工作表名
     * @param columnHeaders
     *            列显示名
     * @param styleWarn
     *            样式读取失败回调，可为 null
     */
    public SaxPagedExcelSource(File file, String sheetName, String[] columnHeaders, XlsxStyles.Warn styleWarn)
        throws Exception {
        this.file = file;
        this.sheetName = sheetName;
        this.columnHeaders = columnHeaders == null ? new String[0] : columnHeaders;
        this.styleWarn = styleWarn;
    }

    @Override
    public String[] getColumnHeaders() {
        return columnHeaders;
    }

    @Override
    public int getTotalRows() {
        return PagedTablePage.UNKNOWN_TOTAL_ROWS;
    }

    @Override
    public PagedTablePage readPage(int pageIndex, int pageSize) throws Exception {
        int startDataIndex = (pageIndex - 1) * pageSize;
        return XlsxStaxSheetPageReader.readPage(file, sheetName, startDataIndex, pageSize, styleWarn);
    }
}
