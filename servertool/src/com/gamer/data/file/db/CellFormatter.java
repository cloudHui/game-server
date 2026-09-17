package com.gamer.data.file.db;

import com.gamer.data.file.config.DbConfig;

/**
 * 表格单元格与 BLOB 详情格式化。
 */
final class CellFormatter {

    /** 表格内 BLOB 摘要最大字符数 */
    private static final int SUMMARY_MAX = 120;

    private CellFormatter() {
    }

    /**
     * 格式化为表格展示文本。
     *
     * @param value
     *            单元格原始值
     * @param table
     *            表名
     * @param column
     *            列名
     * @param decoder
     *            proto 解码器
     * @return 展示文本
     */
    static String formatForTable(Object value, String table, String column, ProtoBlobDecoder decoder) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[]) {
            return formatBlobSummary((byte[]) value, table, column, decoder);
        }
        return String.valueOf(value);
    }

    /**
     * 格式化为详情文本（BLOB 完整 proto 或 hex）。
     *
     * @param value
     *            单元格原始值
     * @param table
     *            表名
     * @param column
     *            列名
     * @param decoder
     *            proto 解码器
     * @return 详情文本
     */
    static String formatForDetail(Object value, String table, String column, ProtoBlobDecoder decoder) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[]) {
            return formatBlobDetail((byte[]) value, table, column, decoder);
        }
        return String.valueOf(value);
    }

    /**
     * BLOB 表格摘要。
     */
    private static String formatBlobSummary(byte[] data, String table, String column, ProtoBlobDecoder decoder) {
        if (data.length == 0) {
            return "<BLOB 0 bytes>";
        }
        BlobParseRule rule = DbConfig.findBlobRule(table, column);
        if (rule == null) {
            return "<BLOB " + data.length + " bytes>";
        }
        try {
            String text = decoder.decode(rule.getProtoMsg(), data);
            if (text.length() <= SUMMARY_MAX) {
                return text.replace('\n', ' ');
            }
            return text.substring(0, SUMMARY_MAX).replace('\n', ' ') + "...";
        } catch (Throwable t) {
            return "<BLOB " + data.length + " bytes, 解析失败: " + t.getMessage() + ">";
        }
    }

    /**
     * BLOB 详情：有规则则 proto 文本，否则 hex。
     */
    private static String formatBlobDetail(byte[] data, String table, String column, ProtoBlobDecoder decoder) {
        if (data.length == 0) {
            return "<空 BLOB>";
        }
        BlobParseRule rule = DbConfig.findBlobRule(table, column);
        if (rule != null) {
            try {
                String text = decoder.decode(rule.getProtoMsg(), data);
                if (text == null || text.trim().isEmpty()) {
                    return "（proto 消息为空）\n\n" + toHex(data);
                }
                return text;
            } catch (Throwable t) {
                return "proto 解析失败: " + t.getMessage() + "\n\n" + toHex(data);
            }
        }
        return "长度: " + data.length + " bytes\n\n" + toHex(data);
    }

    /**
     * 字节转 hex，每行 32 字节。
     *
     * @param data
     *            字节
     * @return hex 文本
     */
    private static String toHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            if (i > 0 && i % 32 == 0) {
                sb.append('\n');
            }
            sb.append(String.format("%02x", data[i] & 0xFF));
        }
        return sb.toString();
    }
}

