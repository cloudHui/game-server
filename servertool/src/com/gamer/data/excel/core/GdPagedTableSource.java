package com.gamer.data.excel.core;

import java.io.File;

import com.gamer.data.read.GdFileReader;
import com.gamer.data.read.GdHeader;
import com.gamer.data.read.GdPagingContext;

/**
 * GD 文件分页数据源：打开时读盘一次并建行偏移索引，翻页直接定位不重复 IO。
 * <p>
 * 使用方：CodeTool {@link com.gamer.data.excel.browse.ExcelViewer}、{@link com.gamer.data.excel.browse.GdViewerFrame}。
 */
public class GdPagedTableSource implements PagedTableSource {

    private final GdPagingContext pagingContext;

    /**
     * 打开 GD：缓存文件字节 + 行偏移表。
     *
     * @param gdFile
     *            GD 文件
     * @throws Exception
     *             打开或索引失败
     */
    public GdPagedTableSource(File gdFile) throws Exception {
        this.pagingContext = GdFileReader.openPagingContext(gdFile);
    }

    @Override
    public String[] getColumnHeaders() {
        GdHeader header = pagingContext.getHeader();
        int cols = header.columnNames.size();
        String[] names = new String[cols];
        for (int i = 0; i < cols; i++) {
            String type = header.columnTypes.get(i);
            names[i] = header.columnNames.get(i) + " (" + type + ")";
        }
        return names;
    }

    @Override
    public int getTotalRows() {
        return pagingContext.getHeader().rows;
    }

    @Override
    public PagedTablePage readPage(int pageIndex, int pageSize) throws Exception {
        return GdFileReader.readDataPage(pagingContext, pageIndex, pageSize);
    }
}
