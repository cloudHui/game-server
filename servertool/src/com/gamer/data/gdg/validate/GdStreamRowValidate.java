package com.gamer.data.gdg.validate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.gamer.data.limit.ColumnLengthPair;
import com.gamer.data.limit.validate.ColumnLengthLimitValidator;
import com.gamer.data.limit.validate.ExcelErrorValueChecker;
import com.gamer.data.limit.validate.LimitTypeValidator;

/**
 * 流式读表时单行数据校验（顺序：limit type → Excel错误值 → 范围/绑定）。
 */
public class GdStreamRowValidate {

    /** limit type 转换失败原因 */
    private static final String REASON_LIMIT_TYPE = "limit类型转换错误";

    /**
     * 禁止实例化。
     */
    private GdStreamRowValidate() {}

    /**
     * 将 TreeMap 列值转为定长行数组。
     *
     * @param colValues
     *            SAX 列 map
     * @param columns
     *            列数
     * @return 行数组
     */
    public static String[] toRowArray(TreeMap<Integer, String> colValues, int columns) {
        String[] row = new String[columns];
        for (int c = 0; c < columns; c++) {
            String v = colValues.get(c);
            row[c] = v == null ? "" : v;
        }
        return row;
    }

    /**
     * 校验一行数据区单元格（顺序 2→3→1→4）。
     *
     * @param excelName
     *            Excel 文件名
     * @param schema
     *            Sheet Schema
     * @param rowValues
     *            行数据
     * @param excelRow
     *            Excel 行号（1-based）
     * @param limitTypes
     *            limit 列 type（列名->type），可为 null
     * @param limitPairs
     *            limit 列对
     * @param vindexFirstRow
     *            vindex 映射
     * @param collector
     *            错误收集
     * @throws GdValidationAbortException
     *             满额中止
     */
    public static void validateDataRow(String excelName, GdSheetSchema schema, String[] rowValues, int excelRow,
        Map<String, String> limitTypes, List<ColumnLengthPair> limitPairs, Map<Integer, Integer> vindexFirstRow,
        GdErrorCollector collector) throws GdValidationAbortException {
        if (rowValues.length == 0 || isBlank(rowValues[0])) {
            return;
        }
        if (collector.shouldStop()) {
            throw new GdValidationAbortException();
        }
        validateLimitTypes(excelName, schema, rowValues, excelRow, limitTypes, collector);
        validateExcelErrorValues(excelName, schema, rowValues, excelRow, collector);
        validateRanges(excelName, schema, rowValues, excelRow, vindexFirstRow, collector);
        checkLimitPairs(excelName, schema, limitPairs, rowValues, excelRow, collector);
    }

    /**
     * 步骤2：limit 有 type 的列按 limit type 转换校验。
     */
    private static void validateLimitTypes(String excelName, GdSheetSchema schema, String[] rowValues, int excelRow,
        Map<String, String> limitTypes, GdErrorCollector collector) throws GdValidationAbortException {
        if (limitTypes == null || limitTypes.isEmpty()) {
            return;
        }
        String sheetName = schema.getSheetName();
        int cols = schema.getColumns();
        for (int colIdx = 0; colIdx < cols; colIdx++) {
            String colName = schema.getColumnName(colIdx);
            String limitType = limitTypes.get(colName);
            if (limitType == null || limitType.isEmpty()) {
                continue;
            }
            String value = colIdx < rowValues.length ? rowValues[colIdx] : "";
            if (LimitTypeValidator.canConvert(limitType, value)) {
                continue;
            }
            String msg = GdErrorMessage.formatCellError(sheetName, excelRow, colName, value,
                REASON_LIMIT_TYPE);
            collector.addErrorOrAbort(GdErrorMessage.prependExcelFile(excelName, msg));
        }
    }

    /**
     * 步骤1：所有列检查 Excel 错误字面量与未求值公式。
     */
    private static void validateExcelErrorValues(String excelName, GdSheetSchema schema, String[] rowValues,
        int excelRow, GdErrorCollector collector) throws GdValidationAbortException {
        String sheetName = schema.getSheetName();
        int cols = schema.getColumns();
        for (int colIdx = 0; colIdx < cols; colIdx++) {
            String value = colIdx < rowValues.length ? rowValues[colIdx] : "";
            if (!ExcelErrorValueChecker.isExcelErrorValue(value)) {
                continue;
            }
            String colName = schema.getColumnName(colIdx);
            String msg = GdErrorMessage.formatExcelErrorCell(sheetName, excelRow, colName, value);
            collector.addErrorOrAbort(GdErrorMessage.prependExcelFile(excelName, msg));
        }
    }

    /**
     * 步骤4：范围与 vindex 校验（仍用 Excel 表头）。
     */
    private static void validateRanges(String excelName, GdSheetSchema schema, String[] rowValues, int excelRow,
        Map<Integer, Integer> vindexFirstRow, GdErrorCollector collector) throws GdValidationAbortException {
        String sheetName = schema.getSheetName();
        List<String[]> headRows = schema.getHeadRows();
        int cols = schema.getColumns();
        try {
            for (int colIdx = 0; colIdx < cols; colIdx++) {
                String value = colIdx < rowValues.length ? rowValues[colIdx] : "";
                GdCellValidator.validateValueRange(sheetName, headRows, colIdx, value, excelRow, vindexFirstRow);
            }
        } catch (Exception e) {
            collector.addErrorOrAbort(GdErrorMessage.prependExcelFile(excelName, e.getMessage()));
        }
    }

    /**
     * 步骤4：列长度绑定校验。
     */
    private static void checkLimitPairs(String excelName, GdSheetSchema schema, List<ColumnLengthPair> pairs,
        String[] rowValues, int excelRow, GdErrorCollector collector) throws GdValidationAbortException {
        if (pairs == null || pairs.isEmpty()) {
            return;
        }
        for (ColumnLengthPair pair : pairs) {
            if (!checkOneLimitPair(excelName, schema, pair, rowValues, excelRow, collector)) {
                throw new GdValidationAbortException();
            }
        }
    }

    /**
     * 校验单个 limit 列对。
     */
    private static boolean checkOneLimitPair(String excelName, GdSheetSchema schema, ColumnLengthPair pair,
        String[] rowValues, int excelRow, GdErrorCollector collector) throws GdValidationAbortException {
        int colA = findLimitColumnIndex(schema, pair.colA);
        int colB = findLimitColumnIndex(schema, pair.colB);
        if (colA < 0 || colB < 0) {
            return true;
        }
        String valA = rowValues[colA];
        String valB = rowValues[colB];
        if (ColumnLengthLimitValidator.isLengthMatched(valA, valB)) {
            return true;
        }
        String msg = GdErrorMessage.prependExcelFile(excelName, "Sheet(" + schema.getSheetName() + "), 第" + excelRow
            + "行 列[" + pair.colA + "]与[" + pair.colB + "] 长度检测不一致");
        collector.addErrorOrAbort(msg);
        return !collector.shouldStop();
    }

    /**
     * 按列名找列索引。
     */
    private static int findLimitColumnIndex(GdSheetSchema schema, String columnName) {
        if (columnName == null) {
            return -1;
        }
        String[] header = schema.getHeadRows().get(0);
        for (int i = 0; i < header.length; i++) {
            String name = header[i];
            if (columnName.equals(name != null ? name.trim() : null)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 创建 vindex 映射容器。
     *
     * @return 空映射
     */
    public static Map<Integer, Integer> newVindexMap() {
        return new HashMap<>();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
