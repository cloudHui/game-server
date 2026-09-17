package com.gamer.data.excel.modelgen.view.tree;

import com.gamer.data.excel.shared.Title;

/**
 * 复选框节点数据类行
 */
public class SheetLineNode extends SheetNode {

    /** Excel 列定义 */
    public final Title title;

    /** 是否与已有 Config 的 DataCell 匹配 */
    public final boolean existingDataCell;

    /**
     * @param title
     *            Excel 列定义
     * @param selected
     *            是否勾选
     * @param existingDataCell
     *            是否与已有 Config 的 DataCell 匹配
     */
    public SheetLineNode(Title title, boolean selected, boolean existingDataCell) {
        super(title != null ? title.toString() : "", selected);
        this.title = title;
        this.existingDataCell = existingDataCell;
    }

    @Override
    public String toString() {
        return title != null ? title.toString() : "";
    }
}
