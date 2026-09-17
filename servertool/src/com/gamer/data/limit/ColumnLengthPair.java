package com.gamer.data.limit;

/**
 * 两列长度绑定对（列名为 Excel 第 1 行属性名）。
 */
public class ColumnLengthPair {

    /** 列 A 属性名 */
    public final String colA;

    /** 列 B 属性名 */
    public final String colB;

    /**
     * 构造列长度绑定对。
     *
     * @param colA 列 A 属性名（Excel 第 1 行）
     * @param colB 列 B 属性名（Excel 第 1 行）
     */
    public ColumnLengthPair(String colA, String colB) {
        // 保存列 A
        this.colA = colA;
        // 保存列 B
        this.colB = colB;
    }
}
