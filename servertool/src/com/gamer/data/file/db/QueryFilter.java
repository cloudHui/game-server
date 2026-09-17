package com.gamer.data.file.db;

/**
 * 表数据筛选条件：列 + 运算符 + 值。
 */
public class QueryFilter {

    /** 筛选列名 */
    private String column;

    /** 运算符：=、!=、>、<、>=、<=、LIKE、IS NULL、IS NOT NULL */
    private String operator;

    /** 比较值（IS NULL / IS NOT NULL 时可为空） */
    private String value;

    /**
     * @return 列名
     */
    public String getColumn() {
        return column;
    }

    /**
     * @param column
     *            列名
     */
    public void setColumn(String column) {
        this.column = column;
    }

    /**
     * @return 运算符
     */
    public String getOperator() {
        return operator;
    }

    /**
     * @param operator
     *            运算符
     */
    public void setOperator(String operator) {
        this.operator = operator;
    }

    /**
     * @return 比较值
     */
    public String getValue() {
        return value;
    }

    /**
     * @param value
     *            比较值
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * 是否为空条件（未选列或未选运算符）。
     *
     * @return 无有效条件 true
     */
    public boolean isEmpty() {
        return column == null || column.trim().isEmpty();
    }

    /**
     * 运算符是否不需要值。
     *
     * @return 不需要值 true
     */
    public boolean isNullOperator() {
        return "IS NULL".equals(operator) || "IS NOT NULL".equals(operator);
    }
}
