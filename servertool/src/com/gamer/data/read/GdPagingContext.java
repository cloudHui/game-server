package com.gamer.data.read;

/**
 * GD 分页预览上下文：文件字节缓存 + 每行数据区起始偏移。
 * <p>
 * 使用方：{@link com.gamer.data.excel.core.GdPagedTableSource}、{@link GdFileReader#readDataPage(GdPagingContext, int, int)}。
 */
public class GdPagingContext {

    /** 已解析的文件头 */
    private final GdHeader header;

    /** 全文件字节缓存（打开时读一次） */
    private final byte[] fileBytes;

    /** 每行数据在 fileBytes 中的起始偏移；长度 = header.rows */
    private final int[] rowOffsets;

    /**
     * @param header
     *            文件头
     * @param fileBytes
     *            全文件字节
     * @param rowOffsets
     *            行偏移索引
     */
    public GdPagingContext(GdHeader header, byte[] fileBytes, int[] rowOffsets) {
        this.header = header;
        this.fileBytes = fileBytes;
        this.rowOffsets = rowOffsets;
    }

    /**
     * @return 文件头
     */
    public GdHeader getHeader() {
        return header;
    }

    /**
     * @return 全文件字节缓存
     */
    public byte[] getFileBytes() {
        return fileBytes;
    }

    /**
     * @return 行偏移表
     */
    public int[] getRowOffsets() {
        return rowOffsets;
    }
}
