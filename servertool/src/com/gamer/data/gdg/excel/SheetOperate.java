package com.gamer.data.gdg.excel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import com.gamer.data.gdg.util.DataType;
import com.gamer.data.gdg.util.GdLocaleConfig;
import com.gamer.data.gdg.validate.GdErrorMessage;

/**
 * DOM 方式 Sheet 解析（.xls 及兼容路径）。
 */
public class SheetOperate {
    /** 数据行数 */
    private final int rows;
    /** Sheet 名 */
    private final String sheetName;

    /**
     * 构造 Sheet 操作对象。
     *
     * @param sheet
     *            POI Sheet
     */
    public SheetOperate(Sheet sheet) {
        // 取 Sheet 名
        this.sheetName = sheet.getSheetName();
        // 计算有效行数
        this.rows = getRealRows(sheet);
    }

    /**
     * @return 数据行数
     */
    public int getRows() {
        return this.rows;
    }

    /**
     * @return Sheet 名
     */
    public String getSheetName() {
        return this.sheetName;
    }

    /**
     * POI 单元格转字符串。
     */
    private String getCellValueAsString(Cell cell) {
        // 布尔转 Y/N
        if (cell.getCellType() == CellType.BOOLEAN) {
            return cell.getBooleanCellValue() ? "Y" : "N";
        }
        // 其余转字符串
        cell.setCellType(CellType.STRING);
        return cell.getStringCellValue().trim();
    }

    /**
     * 解析表头列配置。
     *
     * @param sheetName
     *            Sheet 名
     * @param header
     *            表头五行
     * @return 列数
     * @throws Exception
     *             表头错误
     */
    public static int storeHeader(String sheetName, List<String[]> header) throws Exception {
        String[] columnNames = header.get(0);
        String[] types = header.get(2);
        String[] ranges = header.get(4);
        for (int i = 0; i < types.length; i++) {
            validateHeaderColumn(sheetName, columnNames, types, ranges, i);
        }
        return types.length;
    }

    /**
     * 校验并规范化单列表头。
     */
    private static void validateHeaderColumn(String sheetName, String[] columnNames, String[] types, String[] ranges,
        int i) throws Exception {
        if (types[i] == null || types[i].isEmpty()) {
            throw new Exception(GdErrorMessage.formatCellError(sheetName, 3, columnNames[i], "", "列类型不能为空"));
        }
        if (types[i].startsWith("_")
            || (isInternational(columnNames[i]) && !isCurrentInternational(columnNames[i], GdLocaleConfig.LANGUAGE))) {
            types[i] = "";
            columnNames[i] = "";
            ranges[i] = "";
        } else if (isCurrentInternational(columnNames[i], GdLocaleConfig.LANGUAGE)) {
            columnNames[i] = getRealColName(columnNames[i]);
        }
        if (!types[i].isEmpty() && DataType.parseName(types[i]) == null) {
            throw new Exception(
                GdErrorMessage.formatCellError(sheetName, 3, columnNames[i], types[i], "列类型拼写错误"));
        }
    }

    /**
     * 校验列名不重复。
     */
    public static void checkColumnNameSame(String sheetName, String[] cols) throws Exception {
        Set<String> seen = new HashSet<>();
        for (String col : cols) {
            String name = isInternational(col) ? getRealColName(col) : col;
            if (!name.isEmpty() && !seen.add(name)) {
                throw new Exception(GdErrorMessage.formatCellError(sheetName, 1, name, name, "列名重复"));
            }
        }
    }

    /**
     * 有效行数（第 1 列非空）。
     */
    public int getRealRows(Sheet sheet) {
        for (int i = sheet.getLastRowNum(); i >= 0; i--) {
            Row row = sheet.getRow(i);
            if (row != null && row.getCell(0) != null) {
                if (!getCellValueAsString(row.getCell(0)).isEmpty()) {
                    return i + 1;
                }
            }
        }
        return 0;
    }

    /**
     * 是否当前语言国际化列名。
     */
    public static boolean isCurrentInternational(String colName, String locale) {
        return colName != null && colName.endsWith("(" + locale + ")");
    }

    /**
     * 取国际化列真实名。
     */
    public static String getRealColName(String colName) {
        return (colName != null && colName.length() > 4) ? colName.substring(0, colName.length() - 4) : "";
    }

    /**
     * 是否国际化列名格式。
     */
    public static boolean isInternational(String name) {
        return name != null && Pattern.matches("[a-zA-Z][a-zA-Z_0-9]*\\([a-zA-Z]{2}\\)", name);
    }
}
