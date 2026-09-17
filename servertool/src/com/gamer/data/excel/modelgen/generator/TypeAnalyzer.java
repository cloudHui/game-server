package com.gamer.data.excel.modelgen.generator;

import com.gamer.data.excel.modelgen.domain.ValueAnalysisResult;

/**
 * 模型字段类型分析 Module：封装类型规范化、数值判断和二维分隔识别。
 */
final class TypeAnalyzer {

    private TypeAnalyzer() {}

    /**
     * 分析单个数组值的维度和数值性质。
     *
     * @param value
     *            单元格值
     * @param currentDivider
     *            第一层分隔符名称
     * @return 值分析结果
     */
    static ValueAnalysisResult analyzeValue(String value, String currentDivider) {
        boolean numeric = !isNotNumeric(value);
        String[] subValues = splitSecondDimension(value, currentDivider);
        if (subValues != null) {
            return new ValueAnalysisResult(true, true, subValues);
        }
        return new ValueAnalysisResult(false, numeric, null);
    }

    /**
     * 将配置类型规范到生成器支持的集合。
     *
     * @param type
     *            原始类型
     * @return 规范类型
     */
    static String normalizeType(String type) {
        if (type == null || type.trim().isEmpty()) {
            return "string";
        }
        String normalized = type.trim().toLowerCase();
        if ("vindex".equals(normalized)) {
            return "int";
        }
        if ("string".equals(normalized) || "int".equals(normalized) || "int[]".equals(normalized)
            || "int[][]".equals(normalized)) {
            return normalized;
        }
        // string[] / string[][] 等不再支持，降为 string
        return "string";
    }

    /**
     * 判断文本是否不能解析为整数。
     *
     * @param value
     *            文本值
     * @return 不能解析时返回 true
     */
    static boolean isNotInteger(String value) {
        try {
            Integer.parseInt(value);
            return false;
        } catch (NumberFormatException ex) {
            return true;
        }
    }

    /**
     * 判断文本是否不能解析为数字。
     *
     * @param value
     *            文本值
     * @return 不能解析时返回 true
     */
    private static boolean isNotNumeric(String value) {
        try {
            Float.parseFloat(value);
            return false;
        } catch (NumberFormatException ex) {
            return true;
        }
    }

    /**
     * 按第一层分隔符选择第二层分隔符。
     *
     * @param value
     *            单元格值
     * @param currentDivider
     *            第一层分隔符名称
     * @return 第二层值；不是二维结构时返回 null
     */
    static String[] splitSecondDimension(String value, String currentDivider) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        if ("SPLIT_PIPE".equals(currentDivider) && value.contains(",")) {
            return value.split(",");
        }
        if ("SPLIT_COMMA".equals(currentDivider) && value.contains("|")) {
            return value.split("\\|");
        }
        if (currentDivider == null) {
            if (value.contains(",")) {
                return value.split(",");
            }
            if (value.contains("|")) {
                return value.split("\\|");
            }
        }
        return null;
    }
}
