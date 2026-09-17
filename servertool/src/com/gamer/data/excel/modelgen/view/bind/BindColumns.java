package com.gamer.data.excel.modelgen.view.bind;

import java.util.ArrayList;
import java.util.List;

import com.gamer.data.excel.shared.FileWithSheets;
import com.gamer.data.excel.shared.Title;
import com.gamer.data.limit.ColumnLengthTypeUtil;

/**
 * Excel 绑定面板辅助：从展开时已缓存的 FileWithSheets 提取列名。
 * <p>
 * 使用方：{@link com.gamer.data.excel.browse.ExcelViewer} 列长度绑定面板。
 */
public class BindColumns {

    private BindColumns() {}

    /**
     * 从已展开缓存的 Title 中取数组列名（不触发磁盘读取）。
     *
     * @param excel
     *            Excel 结构
     * @param sheetName
     *            Sheet 名
     * @return 数组列名列表
     */
    static List<String> loadArrayColumnNames(FileWithSheets excel, String sheetName) {
        List<String> columns = new ArrayList<>();
        if (excel == null || excel.sheets == null || !excel.isStructureRefined(sheetName)) {
            return columns;
        }
        List<Title> titles = excel.sheets.get(sheetName);
        if (titles == null) {
            return columns;
        }
        for (Title title : titles) {
            if (title == null || title.getOldName() == null || title.getOldName().isEmpty()) {
                continue;
            }
            if (ColumnLengthTypeUtil.isArrayType(title.getType())) {
                columns.add(title.getOldName());
            }
        }
        return columns;
    }
}
