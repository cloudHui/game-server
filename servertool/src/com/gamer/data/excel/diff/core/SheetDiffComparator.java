package com.gamer.data.excel.diff.core;

import static com.gamer.data.excel.ui.ViewUtils.objectsNotEqual;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.gamer.data.gdg.excel.XlsxOpcSession;
import com.gamer.data.read.GdData;
import com.gamer.data.read.GdFileReader;
import com.gamer.data.read.GdIdRowDiff;

/**
 * 单 Sheet 差异计算：输出 CmdPanel / {@link GdIdRowDiff} 同款日志行。
 * <ul>
 * <li>Excel：每侧单次流式快照（表头+数据区），再语义对比</li>
 * <li>GD：直接复用 {@link GdIdRowDiff}</li>
 * </ul>
 */
public final class SheetDiffComparator {

    /** 超过此行数时 GD 差异仅输出摘要 */
    private static final int GD_FULL_COMPARE_MAX_ROWS = 5000;

    private SheetDiffComparator() {}

    /**
     * Excel 整表对比结果（表头日志 + 数据区结果）。
     */
    public static final class ExcelCompareResult {
        /** 表头差异日志 */
        public final List<String> headerLines;
        /** 数据区差异 */
        public final IdRowDiffEngine.Result data;

        public ExcelCompareResult(List<String> headerLines, IdRowDiffEngine.Result data) {
            this.headerLines = headerLines;
            this.data = data;
        }

        public String headerSummary() {
            return headerLines.isEmpty() ? "无" : ("表头" + headerLines.size());
        }
    }

    /**
     * 对比 Excel：src/curr 各扫一遍 Sheet。
     *
     * @param srcSession
     *            上层会话，可为 null
     * @param currSession
     *            当前会话
     * @param sheetName
     *            Sheet 名
     * @return 对比结果
     */
    public static ExcelCompareResult compareExcel(XlsxOpcSession srcSession, XlsxOpcSession currSession,
        String sheetName) throws Exception {
        if (currSession == null) {
            return new ExcelCompareResult(Collections.emptyList(),
                IdRowDiffEngine.compare(Collections.emptyMap(), Collections.emptyMap(), new String[0]));
        }
        ExcelSheetSnapshot curr = ExcelSheetSnapshot.load(currSession, sheetName);
        // 上层无源文件时按空表对比，当前表内容计为新增
        ExcelSheetSnapshot src = srcSession != null ? ExcelSheetSnapshot.load(srcSession, sheetName)
            : ExcelSheetSnapshot.empty();
        int maxCol = Math.max(src.maxColumnCount, curr.maxColumnCount);
        String[] colNames = pickColumnNames(src.headerRows, curr.headerRows, maxCol);
        List<String> headerLines = new ArrayList<>();
        for (int col = 0; col < maxCol; col++) {
            appendHeaderDiffLine(headerLines, src.headerRows, curr.headerRows, colNames, col);
        }
        IdRowDiffEngine.Result data = IdRowDiffEngine.compare(src.rowsById, curr.rowsById, colNames);
        return new ExcelCompareResult(headerLines, data);
    }

    /**
     * 对比上层 GD 与当前 GD（复用 {@link GdIdRowDiff}）。
     */
    public static List<String> compareGdFiles(File srcGd, File currGd) throws Exception {
        List<String> lines = new ArrayList<>();
        if (currGd == null || !currGd.exists()) {
            return lines;
        }
        String path = currGd.getName();
        GdData srcData = srcGd != null && srcGd.exists() ? GdFileReader.readGdFile(srcGd) : null;
        GdData currData = GdFileReader.readGdFile(currGd);
        int currRows = currData.getRowCount();
        int srcRows = srcData != null ? srcData.getRowCount() : 0;
        if (currRows > GD_FULL_COMPARE_MAX_ROWS || srcRows > GD_FULL_COMPARE_MAX_ROWS) {
            lines.add(path + "：");
            lines.add("  源 " + srcRows + " 行 → 当前 " + currRows + " 行（大表仅行数摘要）");
            return lines;
        }
        if (srcData == null) {
            lines.add(path + "：");
            lines.add("  全新 GD，当前 " + currRows + " 行（无上层）");
            return lines;
        }
        List<String> currCols = currData.header != null && currData.header.columnNames != null
            ? currData.header.columnNames : Collections.emptyList();
        List<String> srcCols = srcData.header != null && srcData.header.columnNames != null
            ? srcData.header.columnNames : Collections.emptyList();
        Set<String> addedColumns = new TreeSet<>(currCols);
        addedColumns.removeAll(srcCols);
        Set<String> deletedColumns = new TreeSet<>(srcCols);
        deletedColumns.removeAll(currCols);
        GdIdRowDiff.Result diff = GdIdRowDiff.compare(srcData, currData);
        return GdIdRowDiff.toLogLines(path, addedColumns, deletedColumns, diff);
    }

    private static void appendHeaderDiffLine(List<String> lines, String[][] sourceHead, String[][] currHead,
        String[] colNames, int col) {
        String name = col < colNames.length ? colNames[col] : ("列" + (col + 1));
        StringBuilder srcDesc = new StringBuilder();
        StringBuilder curDesc = new StringBuilder();
        boolean colDiff = false;
        for (int r = 0; r < ExcelSheetLayout.HEADER_ROWS; r++) {
            String v1 = cellAt(sourceHead, r, col);
            String v2 = cellAt(currHead, r, col);
            if (r > 0) {
                srcDesc.append(" | ");
                curDesc.append(" | ");
            }
            srcDesc.append("R").append(r + 1).append(":").append(v1);
            curDesc.append("R").append(r + 1).append(":").append(v2);
            if (objectsNotEqual(v1, v2)) {
                colDiff = true;
            }
        }
        if (colDiff) {
            lines.add("修改表头 " + name + "：" + srcDesc + " → " + curDesc);
        }
    }

    private static String[] pickColumnNames(String[][] sourceHead, String[][] currHead, int maxCol) {
        String[] names = new String[maxCol];
        for (int i = 0; i < maxCol; i++) {
            String n = cellAt(sourceHead, 0, i);
            if (n.isEmpty()) {
                n = cellAt(currHead, 0, i);
            }
            if (n.isEmpty()) {
                n = "列" + (i + 1);
            }
            names[i] = n;
        }
        return names;
    }

    private static String cellAt(String[][] head, int row, int col) {
        if (row < 0 || row >= head.length || head[row] == null || col < 0 || col >= head[row].length) {
            return "";
        }
        String v = head[row][col];
        return v == null ? "" : v;
    }
}
