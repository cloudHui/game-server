package com.gamer.data.gdg.validate;

import java.util.List;

import com.gamer.data.gdg.excel.SheetOperate;

/**
 * 解析后的 Sheet 表头与列配置（对应 Excel 前 5 行）。
 */
public class GdSheetSchema {

    /** Sheet 名称 */
    private final String sheetName;

    /** 表头五行数据（0-based 行 0~4） */
    private final List<String[]> headRows;

    /** 列数 */
    private final int columns;

    /**
     * 由表头五行构建 Schema 并完成表头校验。
     *
     * @param sheetName
     *            Sheet 名
     * @param headRows
     *            表头五行
     * @return Schema 实例
     * @throws Exception
     *             表头校验失败
     */
    public static GdSheetSchema build(String sheetName, List<String[]> headRows) throws Exception {
        // 校验列名不重复
        SheetOperate.checkColumnNameSame(sheetName, headRows.get(0));
        // 解析列类型并处理国际化列
        int cols = SheetOperate.storeHeader(sheetName, headRows);
        // 返回不可变包装
        return new GdSheetSchema(sheetName, headRows, cols);
    }

    /**
     * 构造 Schema。
     *
     * @param sheetName
     *            Sheet 名
     * @param headRows
     *            表头行
     * @param columns
     *            列数
     */
    private GdSheetSchema(String sheetName, List<String[]> headRows, int columns) {
        // 保存 Sheet 名
        this.sheetName = sheetName;
        // 保存表头
        this.headRows = headRows;
        // 保存列数
        this.columns = columns;
    }

    /**
     * @return Sheet 名称
     */
    public String getSheetName() {
        return sheetName;
    }

    /**
     * @return 表头行列表
     */
    public List<String[]> getHeadRows() {
        return headRows;
    }

    /**
     * @return 列数
     */
    public int getColumns() {
        return columns;
    }

    /**
     * 获取列字段名（表头第 1 行）。
     *
     * @param colIdx
     *            列索引（0-based）
     * @return 列名
     */
    public String getColumnName(int colIdx) {
        // 表头缺失则返回空
        if (headRows == null || headRows.isEmpty() || colIdx < 0) {
            return "";
        }
        // 取第 1 行列名行
        String[] names = headRows.get(0);
        if (colIdx >= names.length || names[colIdx] == null) {
            return "";
        }
        // 返回列名
        return names[colIdx];
    }
}
