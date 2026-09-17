package com.gamer.data.excel.modelgen.view.tree;

/**
 * Sheet 树懒加载占位节点，使无列子节点的 Sheet 在 JTree 中仍显示为可展开。
 * <p>
 * 使用方：{@link com.gamer.data.excel.browse.ExcelViewer} 模型生成树。
 */
public final class SheetPlaceholder {

    /** 单例占位标记 */
    public static final SheetPlaceholder INSTANCE = new SheetPlaceholder();

    /**
     * 禁止外部实例化。
     */
    private SheetPlaceholder() {}

    @Override
    public String toString() {
        return "...";
    }
}
