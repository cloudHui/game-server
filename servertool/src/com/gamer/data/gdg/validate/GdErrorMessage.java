package com.gamer.data.gdg.validate;

import com.gamer.data.limit.validate.ExcelErrorValueChecker;

/**
 * GD 生成过程单元格错误信息格式化工具。
 */
public class GdErrorMessage {

    private GdErrorMessage() {}

    /**
     * 格式化单元格原始值，空值显示为 (空)。
     *
     * @param value
     *            单元格值
     * @return 展示用字符串
     */
    public static String formatValue(String value) {
        if (value == null || value.isEmpty()) {
            return "(空)";
        }
        return value;
    }

    /**
     * 格式化单元格定位信息（Sheet + 行列 + 列字母 + 列名）。
     *
     * @param sheetName
     *            Sheet 名称
     * @param excelRow
     *            Excel 行号（1-based）
     * @param colName
     *            列字段名
     * @return 定位描述
     */
    public static String formatCellLocation(String sheetName, int excelRow, String colName) {
        String colPart = (colName != null && !colName.isEmpty()) ? "列名:" + colName : "";
        return String.format("Sheet(%s), 第%d行, %s", sheetName, excelRow, colPart);
    }

    /**
     * 格式化完整单元格错误信息。
     *
     * @param sheetName
     *            Sheet 名称
     * @param excelRow
     *            Excel 行号（1-based）
     * @param colName
     *            列字段名
     * @param value
     *            单元格值
     * @param reason
     *            错误原因
     * @return 完整错误描述
     */
    public static String formatCellError(String sheetName, int excelRow, String colName, String value, String reason) {
        return formatCellLocation(sheetName, excelRow, colName) + ", 值:" + formatValue(value) + ", " + reason;
    }

    /**
     * 格式化 Excel 错误值单元格错误信息。
     *
     * @param sheetName
     *            Sheet 名
     * @param excelRow
     *            行号（1-based）
     * @param colName
     *            列名
     * @param value
     *            单元格值
     * @return 错误描述
     */
    public static String formatExcelErrorCell(String sheetName, int excelRow, String colName,
                                              String value) {
        return formatCellError(sheetName, excelRow, colName, value, ExcelErrorValueChecker.REASON_EXCEL_ERROR);
    }

    /**
     * 为错误信息补充 Excel 文件名前缀。
     *
     * @param fileName
     *            Excel 文件名
     * @param message
     *            原始错误信息
     * @return 带文件前缀的错误信息
     */
    public static String prependExcelFile(String fileName, String message) {
        if (message == null || message.isEmpty()) {
            return "excel(" + fileName + ")";
        }
        if (message.startsWith("excel(")) {
            return message;
        }
        return "excel(" + fileName + "):" + message;
    }

}
