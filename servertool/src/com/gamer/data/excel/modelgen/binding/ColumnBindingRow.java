package com.gamer.data.excel.modelgen.binding;

/**
 * 绑定面板中的一行：列 A 与列 B 属性名。
 */
public class ColumnBindingRow {

    /** 列 A 属性名 */
    public String colA;

    /** 列 B 属性名 */
    public String colB;

    /**
     * 构造绑定面板行数据。
     *
     * @param colA
     *            列 A 属性名
     * @param colB
     *            列 B 属性名
     */
    public ColumnBindingRow(String colA, String colB) {
        this.colA = colA;
        this.colB = colB;
    }
}
