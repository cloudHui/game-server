package com.gamer.data.excel.diff.core;

import static com.gamer.data.excel.ui.ViewUtils.objectsNotEqual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 按首列 ID 对比两行数据：同 ID 比列修改；非 ID 列相同视为换 ID；其余计增删。
 * <p>
 * 输出 CmdPanel / {@code GdIdRowDiff} 同款日志行。
 * </p>
 */
public final class IdRowDiffEngine {

    /** 行主键列下标（首列 ID） */
    public static final int ROW_KEY_COL = 0;
    /** 列修改明细最多展示条数 */
    public static final int MAX_CELL_DIFF_ROWS = 50;
    /** 增删行 ID 最多列出个数 */
    public static final int MAX_ROW_ID_SAMPLES = 20;

    private IdRowDiffEngine() {}

    /**
     * 对比结果：日志行 + 摘要计数。
     */
    public static final class Result {

        /** 日志行（无缩进；由 {@code SheetDiffCompareRunner} 统一加前缀空格） */
        public final List<String> logLines;
        /** 列修改总数 */
        public final int cellChanges;
        /** 新增行数 */
        public final int addedRows;
        /** 删除行数 */
        public final int deletedRows;

        private Result(List<String> logLines, int cellChanges, int addedRows, int deletedRows) {
            this.logLines = logLines;
            this.cellChanges = cellChanges;
            this.addedRows = addedRows;
            this.deletedRows = deletedRows;
        }

        /** @return 无任何差异 */
        public boolean isEmpty() {
            return cellChanges == 0 && addedRows == 0 && deletedRows == 0;
        }

        /** @return 列表摘要，如「改12 增2 删1」；无差异返回「无」 */
        public String summary() {
            if (isEmpty()) {
                return "无";
            }
            return "改" + cellChanges + " 增" + addedRows + " 删" + deletedRows;
        }
    }

    /**
     * 对比上层与当前 ID 行映射，生成日志差异。
     */
    public static Result compare(Map<String, String[]> srcRowsById, Map<String, String[]> currRowsById,
        String[] columnNames) {
        Map<String, String[]> srcLeft = new HashMap<>(srcRowsById);
        List<Map.Entry<String, String[]>> addedEntries = new ArrayList<>();
        DiffCollect collected = new DiffCollect();
        matchSameIdRows(srcLeft, currRowsById, columnNames, addedEntries, collected);
        List<Map.Entry<String, String[]>> deletedEntries = new ArrayList<>(srcLeft.entrySet());
        pairIdReplacementRows(deletedEntries, addedEntries, columnNames, collected);
        for (Map.Entry<String, String[]> e : addedEntries) {
            collected.addedIds.add(e.getKey());
        }
        for (Map.Entry<String, String[]> e : deletedEntries) {
            collected.deletedIds.add(e.getKey());
        }
        return buildResult(collected);
    }

    private static void matchSameIdRows(Map<String, String[]> srcLeft, Map<String, String[]> currRowsById,
        String[] columnNames, List<Map.Entry<String, String[]>> addedEntries, DiffCollect collected) {
        for (Map.Entry<String, String[]> entry : currRowsById.entrySet()) {
            String currId = entry.getKey();
            String[] currRow = entry.getValue();
            String[] srcRow = srcLeft.remove(currId);
            if (srcRow == null) {
                addedEntries.add(entry);
                continue;
            }
            appendCellDiffs(currId, srcRow, currRow, columnNames, collected);
        }
    }

    private static void pairIdReplacementRows(List<Map.Entry<String, String[]>> deletedEntries,
        List<Map.Entry<String, String[]>> addedEntries, String[] columnNames, DiffCollect collected) {
        Iterator<Map.Entry<String, String[]>> addIt = addedEntries.iterator();
        while (addIt.hasNext()) {
            Map.Entry<String, String[]> addEntry = addIt.next();
            Map.Entry<String, String[]> pairedDel = findNonIdMatch(deletedEntries, addEntry.getValue(), columnNames);
            if (pairedDel == null) {
                continue;
            }
            deletedEntries.remove(pairedDel);
            addIt.remove();
            appendCellDiffs(addEntry.getKey(), pairedDel.getValue(), addEntry.getValue(), columnNames, collected);
        }
    }

    private static Map.Entry<String, String[]> findNonIdMatch(List<Map.Entry<String, String[]>> deletedEntries,
        String[] currRow, String[] columnNames) {
        for (Map.Entry<String, String[]> delEntry : deletedEntries) {
            if (nonIdColumnsEqual(delEntry.getValue(), currRow, columnNames.length)) {
                return delEntry;
            }
        }
        return null;
    }

    private static void appendCellDiffs(String currId, String[] srcRow, String[] currRow, String[] columnNames,
        DiffCollect collected) {
        for (int col = 0; col < columnNames.length; col++) {
            String leftVal = cellAt(srcRow, col);
            String rightVal = cellAt(currRow, col);
            if (!objectsNotEqual(leftVal, rightVal)) {
                continue;
            }
            collected.totalCellDiffs++;
            if (collected.cellLines.size() < MAX_CELL_DIFF_ROWS) {
                String label = formatColumnLabel(currId, columnNames, col);
                collected.cellLines.add("修改 " + label + "：" + leftVal + " → " + rightVal);
            }
        }
    }

    private static Result buildResult(DiffCollect collected) {
        List<String> lines = new ArrayList<>(collected.cellLines);
        int hidden = collected.totalCellDiffs - collected.cellLines.size();
        if (hidden > 0) {
            lines.add("还有 " + hidden + " 处列修改未展示");
        }
        if (!collected.addedIds.isEmpty()) {
            lines.add("新增行" + collected.addedIds.size() + " ["
                + joinIds(collected.addedIds) + "]");
        }
        if (!collected.deletedIds.isEmpty()) {
            lines.add("删除行" + collected.deletedIds.size() + " ["
                + joinIds(collected.deletedIds) + "]");
        }
        return new Result(lines, collected.totalCellDiffs, collected.addedIds.size(), collected.deletedIds.size());
    }

    private static String joinIds(Iterable<String> ids) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        int total = 0;
        for (String id : ids) {
            total++;
            if (IdRowDiffEngine.MAX_ROW_ID_SAMPLES >= 0 && n >= IdRowDiffEngine.MAX_ROW_ID_SAMPLES) {
                continue;
            }
            if (n > 0) {
                sb.append(", ");
            }
            sb.append(id);
            n++;
        }
        if (IdRowDiffEngine.MAX_ROW_ID_SAMPLES >= 0 && total > IdRowDiffEngine.MAX_ROW_ID_SAMPLES) {
            sb.append(" 等共 ").append(total).append(" 个");
        }
        return sb.toString();
    }

    private static boolean nonIdColumnsEqual(String[] leftRow, String[] rightRow, int colCount) {
        for (int col = 1; col < colCount; col++) {
            if (objectsNotEqual(cellAt(leftRow, col), cellAt(rightRow, col))) {
                return false;
            }
        }
        return true;
    }

    private static String formatColumnLabel(String rowId, String[] columnNames, int col) {
        String name = col < columnNames.length ? columnNames[col] : ("列" + (col + 1));
        return rowId + "·" + name;
    }

    private static String cellAt(String[] row, int col) {
        if (row == null || col < 0 || col >= row.length) {
            return "";
        }
        String v = row[col];
        return v == null ? "" : v;
    }

    private static final class DiffCollect {
        private final List<String> cellLines = new ArrayList<>();
        private int totalCellDiffs;
        private final TreeSet<String> addedIds = new TreeSet<>();
        private final TreeSet<String> deletedIds = new TreeSet<>();
    }
}
