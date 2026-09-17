package com.gamer.data.excel.modelgen.binding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个 Excel 文件的全部 Sheet 列长度绑定状态。
 */
public class ExcelColumnBindingState {

    /** Excel 文件名（含后缀） */
    public final String excelName;

    /** Sheet 名 -> 绑定行列表 */
    public final Map<String, List<ColumnBindingRow>> sheetBindings = new LinkedHashMap<>();

    /**
     * 构造 Excel 绑定状态容器。
     *
     * @param excelName
     *            Excel 文件名（含后缀）
     */
    public ExcelColumnBindingState(String excelName) {
        this.excelName = excelName;
    }

    /**
     * 获取或创建指定 Sheet 的绑定行列表。
     *
     * @param sheetName
     *            Sheet 名
     * @return 绑定行列表（永不为 null）
     */
    public List<ColumnBindingRow> getSheetRows(String sheetName) {
        return sheetBindings.computeIfAbsent(sheetName, k -> new ArrayList<>());
    }
}
