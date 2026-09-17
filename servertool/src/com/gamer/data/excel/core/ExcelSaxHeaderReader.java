package com.gamer.data.excel.core;

import java.io.File;
import java.util.TreeMap;

import com.gamer.data.gdg.excel.GdStreamRowHandler;
import com.gamer.data.gdg.excel.XlsxOpcSession;
import com.gamer.data.gdg.excel.XlsxSheetStreamReader;

/**
 * 用 SAX 读取配置表前 5 行，取第 1 行作为列名。
 */
public class ExcelSaxHeaderReader {

    private static final int HEADER_ROWS = 5;

    private ExcelSaxHeaderReader() {}

    /**
     * 读取 Sheet 列名（配置表第 1 行）。
     */
    public static String[] readColumnNames(File file, String sheetName) throws Exception {
        final String[][] rows = new String[HEADER_ROWS][];
        GdStreamRowHandler handler = new GdStreamRowHandler() {
            @Override
            public void onRowEnd(int rowNum0, TreeMap<Integer, String> colValues) {
                if (rowNum0 >= HEADER_ROWS) {
                    return;
                }
                int last = colValues.isEmpty() ? 0 : colValues.lastKey() + 1;
                String[] line = new String[last];
                for (int i = 0; i < last; i++) {
                    line[i] = colValues.getOrDefault(i, "");
                }
                rows[rowNum0] = line;
            }

            @Override
            public void onFormulaCell(int rowNum0, int col) {}
        };
        XlsxOpcSession session = XlsxOpcSession.open(file);
        try {
            XlsxSheetStreamReader.readSheetRows(session, sheetName, handler);
        } finally {
            session.close();
        }
        String[] names = rows[0];
        if (names == null || names.length == 0) {
            return new String[0];
        }
        return names;
    }
}
