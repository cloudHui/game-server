package com.gamer.data.gdg.excel;

import java.util.TreeMap;

/**
 * xlsx 流式读表时的行级回调。
 */
public interface GdStreamRowHandler {

    /**
     * 遇到公式单元格。
     *
     * @param rowNum0 行号（0-based）
     * @param colIdx 列号（0-based）
     */
    void onFormulaCell(int rowNum0, int colIdx);

    /**
     * 一行解析结束。
     *
     * @param rowNum0 行号（0-based）
     * @param colValues 列号 -> 单元格值
     */
    void onRowEnd(int rowNum0, TreeMap<Integer, String> colValues);
}
