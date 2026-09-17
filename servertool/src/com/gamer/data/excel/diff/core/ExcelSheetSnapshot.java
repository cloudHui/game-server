package com.gamer.data.excel.diff.core;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.gamer.data.gdg.excel.GdStreamRowHandler;
import com.gamer.data.gdg.excel.XlsxOpcSession;

/**
 * 单次流式读取一份 Sheet：表头前 {@link ExcelSheetLayout#HEADER_ROWS} 行 + 数据区 ID 行映射。
 */
public final class ExcelSheetSnapshot {

    /** 表头行（定长 HEADER_ROWS，元素不为 null） */
    public final String[][] headerRows;
    /** 数据区：首列 ID → 行（列宽对齐 maxColumnCount） */
    public final Map<String, String[]> rowsById;
    /** 表头与数据区出现过的最大列数 */
    public final int maxColumnCount;

    private ExcelSheetSnapshot(String[][] headerRows, Map<String, String[]> rowsById, int maxColumnCount) {
        this.headerRows = headerRows;
        this.rowsById = rowsById;
        this.maxColumnCount = maxColumnCount;
    }

    /**
     * 流式加载一份 Sheet（只扫一遍）。
     *
     * @param session
     *            OPC 会话
     * @param sheetName
     *            Sheet 名
     * @return 快照；session 为 null 时返回空快照
     */
    public static ExcelSheetSnapshot load(XlsxOpcSession session, String sheetName) throws Exception {
        if (session == null) {
            return empty();
        }
        final String[][] header = new String[ExcelSheetLayout.HEADER_ROWS][];
        final Map<String, TreeMap<Integer, String>> rawData = new HashMap<>();
        final int[] maxCol = new int[] {0};
        session.readSheetRows(sheetName, new GdStreamRowHandler() {
            @Override
            public void onFormulaCell(int rowNum0, int colIdx) {}

            @Override
            public void onRowEnd(int rowNum0, TreeMap<Integer, String> colValues) {
                int last = colValues.isEmpty() ? 0 : colValues.lastKey() + 1;
                if (last > maxCol[0]) {
                    maxCol[0] = last;
                }
                if (rowNum0 < ExcelSheetLayout.HEADER_ROWS) {
                    String[] line = new String[last];
                    for (int i = 0; i < last; i++) {
                        line[i] = colValues.getOrDefault(i, "");
                    }
                    header[rowNum0] = line;
                    return;
                }
                // 数据区：先存稀疏列，读完再按 maxCol 定长
                String id = colValues.getOrDefault(IdRowDiffEngine.ROW_KEY_COL, "");
                if (id == null || id.trim().isEmpty()) {
                    return;
                }
                rawData.put(id, new TreeMap<>(colValues));
            }
        });
        for (int i = 0; i < ExcelSheetLayout.HEADER_ROWS; i++) {
            if (header[i] == null) {
                header[i] = new String[0];
            }
            if (header[i].length > maxCol[0]) {
                maxCol[0] = header[i].length;
            }
        }
        int cols = maxCol[0];
        Map<String, String[]> rowsById = new HashMap<>();
        for (Map.Entry<String, TreeMap<Integer, String>> e : rawData.entrySet()) {
            rowsById.put(e.getKey(), toRowArray(e.getValue(), cols));
        }
        return new ExcelSheetSnapshot(header, rowsById, cols);
    }

    /** @return 空快照 */
    public static ExcelSheetSnapshot empty() {
        String[][] header = new String[ExcelSheetLayout.HEADER_ROWS][];
        for (int i = 0; i < header.length; i++) {
            header[i] = new String[0];
        }
        return new ExcelSheetSnapshot(header, new HashMap<>(), 0);
    }

    private static String[] toRowArray(TreeMap<Integer, String> colValues, int maxColumns) {
        String[] row = new String[maxColumns];
        for (int c = 0; c < maxColumns; c++) {
            row[c] = colValues.getOrDefault(c, "");
        }
        return row;
    }
}
