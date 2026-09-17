package com.gamer.data.excel.modelgen.loader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import com.gamer.data.log.Log;
import com.gamer.data.excel.modelgen.generator.Generator;
import com.gamer.data.excel.shared.FileWithSheets;
import com.gamer.data.excel.shared.Title;
import com.gamer.data.gdg.excel.ExcelOperate;
import com.gamer.data.gdg.excel.GdStreamRowHandler;
import com.gamer.data.gdg.excel.XlsxSheetStreamReader;

/**
 * 模型生成 Excel 懒加载：打开页签仅 Sheet 名，展开单个 Sheet 时 SAX 读表头并推断 string 数组类型。
 * <p>
 * 使用方：{@link com.gamer.data.excel.browse.ExcelViewer} 模型生成树。
 */
public class ExcelLoader {

    /** 表头行数（属性名/描述/类型/附加描述等） */
    private static final int HEADER_ROW_COUNT = 5;

    /** SAX 扫描数据行的物理行上限（从第 5 行起） */
    private static final int MAX_SAX_DATA_PHYSICAL_ROWS = 300;

    /**
     * 禁止实例化。
     */
    private ExcelLoader() {}

    /**
     * 展开 Sheet 时加载列定义并对 string 列做数组类型推断，结果缓存在 FileWithSheets 中。
     *
     * @param fws
     *            Excel 结构
     * @param sheetName
     *            Sheet 名
     * @param log
     *            日志，可为 null（样式失败时写 stderr）
     * @throws Exception
     *             读取失败
     */
    public static void loadSheetColumnsOnExpand(FileWithSheets fws, String sheetName, Log log) throws Exception {
        if (fws == null || sheetName == null || fws.sourceFile == null) {
            return;
        }
        if (fws.isStructureRefined(sheetName)) {
            return;
        }
        File file = fws.sourceFile;
        String lower = file.getName().toLowerCase();
        if (lower.endsWith(".xlsx")) {
            loadSheetStructureFromXlsx(file, fws, sheetName, log);
        } else {
            loadSheetStructureFromWorkbook(file, fws, sheetName);
        }
        fws.markStructureRefined(sheetName);
    }

    /**
     * xlsx：单次 SAX 读表头 + 有限数据行，推断 string 数组类型。
     *
     * @param file
     *            xlsx 文件
     * @param fws
     *            Excel 结构
     * @param sheetName
     *            Sheet 名
     * @param log
     *            日志，可为 null
     * @throws Exception
     *             读取失败
     */
    private static void loadSheetStructureFromXlsx(File file, FileWithSheets fws, String sheetName, Log log)
        throws Exception {
        final Map<Integer, TreeMap<Integer, String>> headerRows = new TreeMap<>();
        final List<TreeMap<Integer, String>> dataRows = new ArrayList<>();
        final int dataRowLimit = HEADER_ROW_COUNT + MAX_SAX_DATA_PHYSICAL_ROWS;

        GdStreamRowHandler handler = new GdStreamRowHandler() {
            @Override
            public void onRowEnd(int rowNum, TreeMap<Integer, String> colValues) {
                if (rowNum < HEADER_ROW_COUNT) {
                    headerRows.put(rowNum, new TreeMap<>(colValues));
                } else if (rowNum < dataRowLimit) {
                    dataRows.add(new TreeMap<>(colValues));
                }
            }

            @Override
            public void onFormulaCell(int rowNum, int col) {}
        };
        XlsxSheetStreamReader.readSheetRows(file, sheetName, handler, ExcelOperate.warnFromLog(log));
        Generator.buildTitlesFromHeaderRows(fws, sheetName, headerRows);
        List<Title> titles = fws.sheets.get(sheetName);
        Generator.refineStringTypesFromDataRows(sheetName, titles, headerRows, dataRows);
    }

    /**
     * xls 等格式：读表头并推断 string 数组类型。
     */
    private static void loadSheetStructureFromWorkbook(File file, FileWithSheets fws, String sheetName) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(file, null, true)) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet != null) {
                Generator.setJavaTitle(fws, sheetName, sheet);
                List<Title> titles = fws.sheets.get(sheetName);
                Generator.refineStringTypesFromSheet(sheet, titles);
            }
        }
    }
}
