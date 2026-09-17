package com.gamer.data.gdg.validate;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;

import com.gamer.data.gdg.util.DataType;

/**
 * GD 单元格校验与格式化（DOM 与流式共用）。
 */
public class GdCellValidator {

    /**
     * 禁止实例化。
     */
    private GdCellValidator() {}

    /**
     * 按列类型格式化数值。
     *
     * @param sheetName
     *            Sheet 名
     * @param headRows
     *            表头
     * @param colIdx
     *            列索引
     * @param value
     *            原始值
     * @param excelRow
     *            Excel 行号（1-based）
     * @return 格式化后的值
     * @throws Exception
     *             转换失败
     */
    public static String formatValueByType(String sheetName, List<String[]> headRows, int colIdx, String value,
        int excelRow) throws Exception {
        // 解析列类型
        DataType type = parseColumnType(sheetName, headRows, colIdx);
        try {
            // 浮点列统一四位小数
            if (type == DataType.TYPE_FLOAT) {
                return new DecimalFormat("0.0000").format(Double.parseDouble(value));
            }
            // 整型列去小数
            if (type == DataType.TYPE_INT || type == DataType.TYPE_V_IDX) {
                double d = Double.parseDouble(value);
                return String.valueOf((int)d);
            }
            // 字符串列原样
            return value;
        } catch (Exception e) {
            // 包装为带行列信息的错误
            throw new Exception(GdErrorMessage.formatCellError(sheetName, excelRow,
                    getColumnName(headRows, colIdx), value, "数值转换错误"));
        }
    }

    /**
     * 校验数值范围与 vindex 唯一性。
     *
     * @param sheetName
     *            Sheet 名
     * @param headRows
     *            表头
     * @param colIdx
     *            列索引
     * @param value
     *            单元格值
     * @param excelRow
     *            Excel 行号（1-based）
     * @param vindexFirstRow
     *            vindex 已出现 ID -> 首次行号
     * @throws Exception
     *             校验失败
     */
    public static void validateValueRange(String sheetName, List<String[]> headRows, int colIdx, String value,
        int excelRow, Map<Integer, Integer> vindexFirstRow) throws Exception {
        // 表头结构不完整则跳过范围校验
        if (!hasRangeConfig(headRows, colIdx)) {
            return;
        }
        // 取范围配置与类型
        String rangeStr = headRows.get(4)[colIdx];
        DataType type = parseColumnType(sheetName, headRows, colIdx);
        // 执行范围与唯一性
        checkRangeAndVindex(sheetName, headRows, colIdx, value, excelRow, rangeStr, type, vindexFirstRow);
    }

    /**
     * 解析列类型。
     *
     * @param sheetName
     *            Sheet 名
     * @param headRows
     *            表头
     * @param colIdx
     *            列索引
     * @return 列类型
     * @throws Exception
     *             表头缺失
     */
    private static DataType parseColumnType(String sheetName, List<String[]> headRows, int colIdx) throws Exception {
        // 类型行必须存在
        if (headRows.size() < 3) {
            throw new Exception("Sheet(" + sheetName + ") 表头格式错误，类型行缺失");
        }
        // 解析类型名
        return DataType.parseName(headRows.get(2)[colIdx]);
    }

    /**
     * 判断是否存在范围配置。
     *
     * @param headRows
     *            表头
     * @param colIdx
     *            列索引
     * @return 是否可校验范围
     */
    private static boolean hasRangeConfig(List<String[]> headRows, int colIdx) {
        // 表头行数不足
        if (headRows == null || headRows.size() <= 4) {
            return false;
        }
        // 取类型行与范围行
        String[] typeArray = headRows.get(2);
        String[] rangeArray = headRows.get(4);
        if (typeArray == null || rangeArray == null) {
            return false;
        }
        // 列索引合法
        return colIdx >= 0 && colIdx < typeArray.length && colIdx < rangeArray.length;
    }

    /**
     * 执行范围与 vindex 唯一性检查。
     *
     * @param sheetName
     *            Sheet 名
     * @param headRows
     *            表头
     * @param colIdx
     *            列索引
     * @param value
     *            单元格值
     * @param excelRow
     *            Excel 行号
     * @param rangeStr
     *            范围字符串
     * @param type
     *            列类型
     * @param vindexFirstRow
     *            vindex 首次行映射
     * @throws Exception
     *             校验失败
     */
    private static void checkRangeAndVindex(String sheetName, List<String[]> headRows, int colIdx, String value,
        int excelRow, String rangeStr, DataType type, Map<Integer, Integer> vindexFirstRow) throws Exception {
        try {
            // 拆分范围上下界
            String[] range = rangeStr.split("~");
            // 浮点范围
            if (type == DataType.TYPE_FLOAT) {
                checkFloatRange(sheetName, headRows, colIdx, value, excelRow, range, rangeStr);
            } else if (type == DataType.TYPE_INT || type == DataType.TYPE_V_IDX) {
                // 整数范围
                checkIntRange(sheetName, headRows, colIdx, value, excelRow, range, rangeStr);
                // vindex 唯一
                if (type == DataType.TYPE_V_IDX) {
                    checkVindexUnique(sheetName, headRows, colIdx, value, excelRow, vindexFirstRow);
                }
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            // 范围配置格式错误
            throw new Exception(GdErrorMessage.formatCellError(sheetName, excelRow,
                    getColumnName(headRows, colIdx), value, "范围校验失败, 范围:" + rangeStr));
        } catch (NumberFormatException e) {
            // 数值无法参与范围比较
            throw new Exception(GdErrorMessage.formatCellError(sheetName, excelRow,
                    getColumnName(headRows, colIdx), value, "范围校验失败, 范围:" + rangeStr));
        }
    }

    /**
     * 校验浮点范围。
     */
    private static void checkFloatRange(String sheetName, List<String[]> headRows, int colIdx, String value,
        int excelRow, String[] range, String rangeStr) throws Exception {
        // 解析当前值与边界
        float f = Float.parseFloat(value);
        float min = Float.parseFloat(range[0]);
        float max = Float.parseFloat(range[1]);
        // 超出则报错
        if (f < min || f > max) {
            throwRangeError(sheetName, headRows, colIdx, value, excelRow, rangeStr);
        }
    }

    /**
     * 校验整数范围。
     */
    private static void checkIntRange(String sheetName, List<String[]> headRows, int colIdx, String value, int excelRow,
        String[] range, String rangeStr) throws Exception {
        // 解析当前值与边界
        int i = Integer.parseInt(value);
        int min = Integer.parseInt(range[0]);
        int max = Integer.parseInt(range[1]);
        // 超出则报错
        if (i < min || i > max) {
            throwRangeError(sheetName, headRows, colIdx, value, excelRow, rangeStr);
        }
    }

    /**
     * 校验 vindex 唯一性。
     */
    private static void checkVindexUnique(String sheetName, List<String[]> headRows, int colIdx, String value,
        int excelRow, Map<Integer, Integer> vindexFirstRow) throws Exception {
        // 解析 ID 值
        int id = Integer.parseInt(value);
        // 查首次出现行
        Integer firstRow = vindexFirstRow.get(id);
        if (firstRow != null) {
            // 与首次行重复
            throw new Exception(GdErrorMessage.formatCellError(sheetName, excelRow,
                    getColumnName(headRows, colIdx), value, "唯一列值重复, 与第" + firstRow + "行重复"));
        }
        // 记录首次出现
        vindexFirstRow.put(id, excelRow);
    }

    /**
     * 抛出超出范围错误。
     */
    private static void throwRangeError(String sheetName, List<String[]> headRows, int colIdx, String value,
        int excelRow, String rangeStr) throws Exception {
        // 构造超范围文案
        throw new Exception(GdErrorMessage.formatCellError(sheetName, excelRow,
                getColumnName(headRows, colIdx), value, "超出数值范围, 范围:" + rangeStr));
    }

    /**
     * 取列名。
     *
     * @param headRows
     *            表头
     * @param colIdx
     *            列索引
     * @return 列名
     */
    private static String getColumnName(List<String[]> headRows, int colIdx) {
        // 表头缺失
        if (headRows == null || headRows.isEmpty() || colIdx < 0) {
            return "";
        }
        // 取列名行
        String[] names = headRows.get(0);
        if (colIdx >= names.length || names[colIdx] == null) {
            return "";
        }
        return names[colIdx];
    }
}
