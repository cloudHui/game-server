package com.gamer.data.excel.modelgen.view.tree;

import com.gamer.data.excel.shared.FileWithSheets;

/**
 * 复选框节点数据类 文件
 */
public class SheetNode {
    public String name;
    public boolean selected;

    /** Excel 文件节点携带的完整数据；Sheet/列子节点为 null */
    public FileWithSheets fileWithSheets;

    /** Sheet 节点：列子节点是否已加载 */
    public boolean columnsLoaded;

    /** Sheet 节点：列是否正在后台加载 */
    public boolean columnsLoading;

    public SheetNode(String name, boolean selected) {
        this.name = name;
        this.selected = selected;
    }
}
