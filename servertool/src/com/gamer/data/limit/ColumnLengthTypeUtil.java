package com.gamer.data.limit;

/**
 * 列长度绑定：数组类型判定与外层长度计算（| 优先于 ,）。
 */
public class ColumnLengthTypeUtil {

    /**
     * 禁止实例化。
     */
    private ColumnLengthTypeUtil() {}

    /**
     * 是否为数组类型（int[]、int[][]、string[]、string[][] 等）。
     *
     * @param type
     *            第 3 行类型字符串
     * @return 是否数组
     */
    public static boolean isArrayType(String type) {
        if (type == null) {
            return false;
        }
        return type.trim().contains("[]");
    }

    /**
     * 计算数组单元格外层长度：含 | 按 | 分段；否则含 , 按 , 分段；否则为 1。
     *
     * @param value
     *            单元格文本（非空）
     * @return 外层长度
     */
    public static int computeArrayOuterLength(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        // | 优先于 ,
        if (value.indexOf('|') >= 0) {
            return value.split("\\|", -1).length;
        }
        if (value.indexOf(',') >= 0) {
            return value.split(",", -1).length;
        }
        return 1;
    }

}
