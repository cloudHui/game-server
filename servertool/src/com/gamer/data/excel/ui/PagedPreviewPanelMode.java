package com.gamer.data.excel.ui;

/**
 * 流式预览面板分页展示模式。
 * 使用方：{@link PagedPreviewPanel}。
 */
public enum PagedPreviewPanelMode {

    /** GD：总行数来自文件头，显示总页数并支持跳转 */
    WITH_TOTAL,

    /** Excel：不扫全表计数，仅显示第 N 页，用 hasNext 控制下一页 */
    PROBE_ONLY
}
