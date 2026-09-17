package com.gamer.data.excel.modelgen.view.bind;

import com.gamer.data.excel.modelgen.view.CheckedSheetItem;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.gamer.data.excel.shared.FileWithSheets;
import com.gamer.data.excel.modelgen.binding.ColumnLengthPairDedup;
import com.gamer.data.excel.modelgen.binding.ExcelColumnBindingState;
import com.gamer.data.ui.ViewUi;

/**
 * 列长度绑定总面板：按树勾选顺序展示已选 Sheet 的绑定区块。
 */
public class BindPanel extends JPanel {

    /** 各 Sheet 区块容器 */
    private final JPanel sectionsPanel = new JPanel();

    /** 当前展示的 Sheet 区块（顺序与树一致） */
    private final List<SheetSection> sheetSections = new ArrayList<>();

    /** 当前展示顺序对应的 Sheet 键 */
    private final List<String> shownSheetKeys = new ArrayList<>();

    /** 去重日志回调 */
    private ColumnLengthPairDedup.DedupLog dedupLog;

    /**
     * 构造绑定总面板。
     */
    public BindPanel() {
        super(new BorderLayout(5, 5));
        ViewUi.titled(this, "列长度绑定（添加列对后启用检测）");
        setOpaque(true);
        setBackground(ViewUi.CARD);
        sectionsPanel.setLayout(BindLayout.create());
        JScrollPane sectionsScroll = ViewUi.scroll(sectionsPanel);
        sectionsScroll.setBorder(null);
        sectionsScroll.getVerticalScrollBar().setUnitIncrement(16);
        add(sectionsScroll, BorderLayout.CENTER);
    }

    /**
     * 设置去重日志回调。
     *
     * @param dedupLog 日志回调
     */
    public void setDedupLog(ColumnLengthPairDedup.DedupLog dedupLog) {
        this.dedupLog = dedupLog;
    }

    /**
     * 加载树中已勾选的 Sheet，按树顺序增量插入/移除区块。
     *
     * @param checkedSheets 已勾选 Sheet 列表
     * @param stateResolver 绑定状态解析
     */
    public void loadCheckedSheets(List<CheckedSheetItem> checkedSheets, BindingStateResolver stateResolver) {
        List<String> nextKeys = buildSheetKeyList(checkedSheets);
        if (nextKeys.equals(shownSheetKeys)) {
            return;
        }
        flushAll();
        syncSheetSections(checkedSheets, stateResolver);
    }

    /**
     * 强制按当前勾选重建区块（列类型变更后刷新数组列下拉）。
     */
    public void forceLoadCheckedSheets(List<CheckedSheetItem> checkedSheets, BindingStateResolver stateResolver) {
        flushAll();
        shownSheetKeys.clear();
        sheetSections.clear();
        sectionsPanel.removeAll();
        syncSheetSections(checkedSheets, stateResolver);
    }

    /**
     * 保存全部 Sheet 区块编辑。
     */
    public void flushAll() {
        for (SheetSection sheetSection : sheetSections) {
            sheetSection.flushAll();
        }
    }

    /**
     * 构建 Sheet 键列表。
     */
    private List<String> buildSheetKeyList(List<CheckedSheetItem> checkedSheets) {
        List<String> keys = new ArrayList<>();
        if (checkedSheets == null) {
            return keys;
        }
        for (CheckedSheetItem item : checkedSheets) {
            if (item != null) {
                keys.add(item.buildKey());
            }
        }
        return keys;
    }

    /**
     * 增量同步 Sheet 区块：复用已有 SheetSection，仅调整顺序与增删。
     */
    private void syncSheetSections(List<CheckedSheetItem> checkedSheets, BindingStateResolver stateResolver) {
        Map<String, SheetSection> existingByKey = indexExistingSections();
        List<SheetSection> nextSections = new ArrayList<>();
        List<String> nextKeys = new ArrayList<>();
        if (checkedSheets != null) {
            for (CheckedSheetItem item : checkedSheets) {
                if (item == null || item.excel == null) {
                    continue;
                }
                String key = item.buildKey();
                SheetSection section = existingByKey.get(key);
                if (section == null) {
                    ExcelColumnBindingState state = stateResolver.resolve(item.excelName, item.excel);
                    section = createSheetSection(item, state);
                }
                nextSections.add(section);
                nextKeys.add(key);
            }
        }
        sheetSections.clear();
        shownSheetKeys.clear();
        sheetSections.addAll(nextSections);
        shownSheetKeys.addAll(nextKeys);
        sectionsPanel.removeAll();
        for (SheetSection sheetSection : sheetSections) {
            sectionsPanel.add(sheetSection);
        }
        repaintSections();
    }

    /**
     * 将当前区块按 key 索引，供增量复用。
     */
    private Map<String, SheetSection> indexExistingSections() {
        Map<String, SheetSection> map = new LinkedHashMap<>();
        for (int i = 0; i < sheetSections.size(); i++) {
            if (i < shownSheetKeys.size()) {
                map.put(shownSheetKeys.get(i), sheetSections.get(i));
            }
        }
        return map;
    }

    /**
     * 创建单个 Sheet 绑定区块。
     */
    private SheetSection createSheetSection(CheckedSheetItem item, ExcelColumnBindingState state) {
        List<String> columns = BindColumns.loadArrayColumnNames(item.excel, item.sheetName);
        return new SheetSection(item.excelName, item.sheetName, columns,
            state.getSheetRows(item.sheetName), dedupLog);
    }

    /**
     * 刷新区块容器布局。
     */
    private void repaintSections() {
        sectionsPanel.revalidate();
        sectionsPanel.repaint();
        revalidate();
        repaint();
    }

    /**
     * 绑定状态解析回调。
     */
    public interface BindingStateResolver {

        /**
         * 获取或加载 Excel 绑定状态。
         *
         * @param excelName Excel 文件名
         * @param excel Excel 结构
         * @return 绑定状态
         */
        ExcelColumnBindingState resolve(String excelName, FileWithSheets excel);
    }
}
