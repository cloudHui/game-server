package com.gamer.data.limit.validate;

import com.gamer.data.limit.ColumnLengthTypeUtil;

/**
 * 列长度一致性判断（GD 行校验用，只比各列外层长度）。
 */
public class ColumnLengthLimitValidator {

    /**
     * 禁止实例化。
     */
    private ColumnLengthLimitValidator() {}

    /**
     * 判断两列单元格外层长度是否一致（两列都空视为通过；任一列为 -1 视为跳过）。
     *
     * @param valueA
     *            列 A 值
     * @param valueB
     *            列 B 值
     * @return 是否一致
     */
    public static boolean isLengthMatched(String valueA, String valueB) {
        if (isMinusOne(valueA) || isMinusOne(valueB)) {
            return true;
        }
        boolean emptyA = isBlank(valueA);
        boolean emptyB = isBlank(valueB);
        if (emptyA && emptyB) {
            return true;
        }
        if (emptyA != emptyB) {
            return false;
        }
        int outerA = ColumnLengthTypeUtil.computeArrayOuterLength(valueA.trim());
        int outerB = ColumnLengthTypeUtil.computeArrayOuterLength(valueB.trim());
        return outerA == outerB;
    }

    /**
     * 判断字符串是否空白。
     *
     * @param value
     *            字符串
     * @return 是否空白
     */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 判断单元格值是否为 -1。
     *
     * @param value
     *            单元格字符串值
     * @return 是否为 -1
     */
    private static boolean isMinusOne(String value) {
        return value != null && "-1".equals(value.trim());
    }
}
