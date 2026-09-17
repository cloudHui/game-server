package com.gamer.data.file.db;

/**
 * BLOB 列 proto 解析规则：表.列 → proto 消息名。
 */
public class BlobParseRule {

    /** 表名 */
    private final String table;

    /** 列名 */
    private final String column;

    /** proto 消息名，如 PBTask */
    private final String protoMsg;

    /**
     * @param table
     *            表名
     * @param column
     *            列名
     * @param protoMsg
     *            proto 消息名
     */
    public BlobParseRule(String table, String column, String protoMsg) {
        this.table = table;
        this.column = column;
        this.protoMsg = protoMsg;
    }

    /**
     * @return 表名
     */
    public String getTable() {
        return table;
    }

    /**
     * @return 列名
     */
    public String getColumn() {
        return column;
    }

    /**
     * @return proto 消息名
     */
    public String getProtoMsg() {
        return protoMsg;
    }
}
