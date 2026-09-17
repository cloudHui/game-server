package com.gamer.data.excel.core;

import com.gamer.data.gdg.excel.ExcelOperate;

/**
 * 配置表 Sheet 命名规则（与 GD 流水线一致）。
 */
public class ExcelSheetRules {

    private ExcelSheetRules() {}

    /**
     * 是否为合法英文配置 Sheet 名。
     *
     * @param sheetName
     *            Sheet 名
     * @return 合法返回 true
     */
    public static boolean isValidConfigSheetName(String sheetName) {
        return ExcelOperate.isValidSheetName(sheetName);
    }
}
