package com.gamer.data.excel.modelgen.view;

import com.gamer.data.excel.shared.FileWithSheets;

/**
 * 树中已勾选的 Excel + Sheet 项（保持树遍历顺序）。
 */
public class CheckedSheetItem {

    /** Excel 表名（文件名） */
    public final String excelName;

    /** Sheet 名 */
    public final String sheetName;

    /** Excel 结构数据 */
    public final FileWithSheets excel;

    /**
     * @param excelName Excel 文件名
     * @param sheetName Sheet 名
     * @param excel Excel 结构
     */
    public CheckedSheetItem(String excelName, String sheetName, FileWithSheets excel) {
        this.excelName = excelName;
        this.sheetName = sheetName;
        this.excel = excel;
    }

    /**
     * 唯一键，用于增量同步 UI 区块。
     *
     * @return excelName + sheetName 组合键
     */
    public String buildKey() {
        return excelName + "\u0001" + sheetName;
    }
}
