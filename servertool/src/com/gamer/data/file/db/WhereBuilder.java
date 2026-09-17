package com.gamer.data.file.db;

import java.util.ArrayList;
import java.util.List;

/**
 * 将 {@link QueryFilter} 转为安全 WHERE 子句与参数列表。
 */
final class WhereBuilder {

    /** WHERE 片段与 JDBC 参数 */
    static final class WhereClause {
        /** SQL 片段，如 {@code  WHERE `roleId` = ?}，无条件时为空串 */
        private final String sql;

        /** 与 ? 对应的参数 */
        private final List<Object> params;

        WhereClause(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }

        String getSql() {
            return sql;
        }

        List<Object> getParams() {
            return params;
        }
    }

    private WhereBuilder() {
    }

    /**
     * 构建 WHERE 子句。
     *
     * @param filter
     *            筛选条件，为空时返回空 WHERE
     * @return WHERE 子句
     */
    static WhereClause build(QueryFilter filter) {
        if (filter == null || filter.isEmpty()) {
            return new WhereClause("", new ArrayList<>());
        }
        if (!isSafeIdentifier(filter.getColumn())) {
            throw new IllegalArgumentException("非法列名: " + filter.getColumn());
        }
        String op = filter.getOperator().trim().toUpperCase();
        if (!isAllowedOperator(op)) {
            throw new IllegalArgumentException("不支持的运算符: " + filter.getOperator());
        }
        List<Object> params = new ArrayList<>();
        String col = "`" + filter.getColumn().trim() + "`";
        if ("IS NULL".equals(op)) {
            return new WhereClause(" WHERE " + col + " IS NULL", params);
        }
        if ("IS NOT NULL".equals(op)) {
            return new WhereClause(" WHERE " + col + " IS NOT NULL", params);
        }
        String value = filter.getValue();
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("请填写筛选值");
        }
        value = value.trim();
        if ("LIKE".equals(op)) {
            params.add(value.indexOf('%') >= 0 ? value : "%" + value + "%");
            return new WhereClause(" WHERE " + col + " LIKE ?", params);
        }
        if ("=".equals(op) || "!=".equals(op) || "<>".equals(op) || ">".equals(op) || "<".equals(op)
            || ">=".equals(op) || "<=".equals(op)) {
            String sqlOp = "<>".equals(op) ? "!=" : op;
            if ("!=".equals(sqlOp)) {
                sqlOp = "<>";
            }
            params.add(parseValue(value));
            return new WhereClause(" WHERE " + col + " " + sqlOp + " ?", params);
        }
        throw new IllegalArgumentException("不支持的运算符: " + op);
    }

    /**
     * 尝试将值解析为数字，否则按字符串。
     */
    private static Object parseValue(String value) {
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    /**
     * 白名单运算符。
     */
    private static boolean isAllowedOperator(String op) {
        return "=".equals(op) || "!=".equals(op) || "<>".equals(op) || ">".equals(op) || "<".equals(op)
            || ">=".equals(op) || "<=".equals(op) || "LIKE".equals(op) || "IS NULL".equals(op)
            || "IS NOT NULL".equals(op);
    }

    /**
     * 列名安全校验。
     */
    private static boolean isSafeIdentifier(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }
}
