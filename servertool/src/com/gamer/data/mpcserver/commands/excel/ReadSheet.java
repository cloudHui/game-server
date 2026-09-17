package com.gamer.data.mpcserver.commands.excel;

import com.gamer.data.mpcserver.commands.excel.support.SheetOps;
import com.gamer.data.mpcserver.commands.excel.support.Workbooks;
import com.gamer.data.mpcserver.commands.excel.support.XlsxReader;

import java.io.File;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamer.data.mpcserver.commands.CommandHandler;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.CommandResult;
import com.gamer.data.mpcserver.core.McpServiceLog;
import com.gamer.data.mpcserver.core.McpUtils;
import com.gamer.data.mpcserver.core.Process;

/**
 * 读取 Excel 指定 sheet 的矩形区域，并以 TSV（tab 分隔）文本返回。
 *
 */
@Process(value = "excel_read_sheet", description = "读取 Excel 工作表内容。", required = {"fileAbsolutePath"},
    optional = {"sheetName", "range", "showFormula", "showStyle", "sheetIndex", "maxRows", "maxCols"})
public class ReadSheet implements CommandHandler {

    private static boolean truthy(JsonNode params, String key) {
        if (params == null) {
            return false;
        }
        JsonNode n = params.get(key);
        if (n != null && !n.isNull()) {
            if (n.isBoolean()) {
                return n.asBoolean();
            }
        }
        String s = McpUtils.text(params, key);
        if (s == null) {
            return false;
        }
        s = s.trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }

    private static boolean isSingleCell(SheetOps.CellRange rr) {
        return rr != null && rr.startRow == rr.endRow && rr.startCol == rr.endCol;
    }

    /** 与 range / max 参数对应的逻辑窗口（流式读前预计算，不依赖已打开的 Sheet）。 */
    private static ReadBounds computeBounds(SheetOps.CellRange rr, int maxRows, int maxCols) {
        int safeMaxRows = Math.max(0, maxRows);
        int safeMaxCols = Math.max(0, maxCols);
        int startRow = 0;
        int startCol = 0;
        int endRow;
        int endCol;
        if (rr == null) {
            endRow = startRow + Math.max(0, safeMaxRows - 1);
            endCol = startCol + Math.max(0, safeMaxCols - 1);
            return new ReadBounds(startRow, startCol, endRow, endCol);
        }
        if (isSingleCell(rr)) {
            startRow = rr.startRow;
            startCol = rr.startCol;
            endRow = startRow + Math.max(0, safeMaxRows - 1);
            endCol = startCol + Math.max(0, safeMaxCols - 1);
            return new ReadBounds(startRow, startCol, endRow, endCol);
        }
        startRow = rr.startRow;
        startCol = rr.startCol;
        endRow = rr.endRow;
        endCol = rr.endCol;
        if (safeMaxRows > 0) {
            endRow = Math.min(endRow, startRow + safeMaxRows - 1);
        }
        if (safeMaxCols > 0) {
            endCol = Math.min(endCol, startCol + safeMaxCols - 1);
        }
        return new ReadBounds(startRow, startCol, endRow, endCol);
    }

    /**
     * DOM 路径下与历史行为对齐：无 range 时按 sheet 实际行数与 maxRows 取小；有 range 时仍用 computeBounds， 但若超出 sheet lastRow 则收缩
     * endRow（避免无意义的长循环）。
     */
    private static ReadBounds computeBoundsForDom(Sheet sheet, SheetOps.CellRange rr, int maxRows, int maxCols) {
        if (sheet == null) {
            return new ReadBounds(0, 0, -1, -1);
        }
        if (rr == null) {
            int lastRow = sheet.getLastRowNum();
            int rowsToRead = Math.min(lastRow + 1, Math.max(0, maxRows));
            int endR = rowsToRead <= 0 ? -1 : rowsToRead - 1;
            int endC = Math.max(0, maxCols - 1);
            return new ReadBounds(0, 0, endR, endC);
        }
        ReadBounds b = computeBounds(rr, maxRows, maxCols);
        int lastRow = sheet.getLastRowNum();
        if (lastRow >= 0 && b.endRow > lastRow) {
            b = new ReadBounds(b.startRow, b.startCol, lastRow, b.endCol);
        }
        if (b.endRow < b.startRow) {
            b = new ReadBounds(b.startRow, b.startCol, b.startRow, b.endCol);
        }
        return b;
    }

    private static Sheet resolveSheetDom(Workbook wb, String sheetName, Integer sheetIndex) {
        if (wb == null) {
            throw new IllegalStateException("Workbook不能为空");
        }
        if (sheetName != null && !sheetName.trim().isEmpty()) {
            Sheet sheet = wb.getSheet(sheetName.trim());
            if (sheet == null) {
                throw new IllegalArgumentException("找不到sheet: " + sheetName);
            }
            return sheet;
        }
        if (sheetIndex != null) {
            if (sheetIndex < 0 || sheetIndex >= wb.getNumberOfSheets()) {
                throw new IllegalArgumentException("sheetIndex越界: " + sheetIndex);
            }
            return wb.getSheetAt(sheetIndex);
        }
        if (wb.getNumberOfSheets() == 0) {
            throw new IllegalStateException("Excel无sheet");
        }
        return wb.getSheetAt(0);
    }

    private static String resolveSheetNameForXlsx(File file, String sheetName, Integer sheetIndex) throws Exception {
        if (sheetName != null && !sheetName.trim().isEmpty()) {
            return sheetName.trim();
        }
        List<String> names = XlsxReader.readSheetNamesInOrder(file);
        if (names.isEmpty()) {
            throw new IllegalStateException("Excel无sheet: " + file.getName());
        }
        if (sheetIndex != null) {
            if (sheetIndex < 0 || sheetIndex >= names.size()) {
                throw new IllegalArgumentException("sheetIndex越界: " + sheetIndex);
            }
            return names.get(sheetIndex);
        }
        return names.get(0);
    }

    private static String formatCellDom(Cell cell, DataFormatter fmt, FormulaEvaluator eval, boolean showFormula,
        boolean showStyle, Workbook wb) {
        if (cell == null) {
            return "";
        }
        if (showFormula) {
            CellType t = cell.getCellType();
            if (t == CellType.FORMULA) {
                try {
                    String f = cell.getCellFormula();
                    return McpUtils.oneLine(f == null ? "" : f);
                } catch (Exception e) {
                    return "";
                }
            }
        }
        String base;
        if (eval == null) {
            base = fmt.formatCellValue(cell);
        } else {
            base = fmt.formatCellValue(cell, eval);
        }
        if (base == null) {
            base = "";
        }
        base = McpUtils.oneLine(base);
        if (showStyle && wb != null) {
            try {
                short df = cell.getCellStyle().getDataFormat();
                String fmtStr = wb.createDataFormat().getFormat(df);
                base = base + " |fmt:" + McpUtils.oneLine(fmtStr);
            } catch (Exception ignored) {
            }
        }
        return base;
    }

    @Override
    public CommandResult handle(CommandContext ctx, JsonNode params) throws Exception {
        File file = ExcelFiles.require(ctx, params);
        String sheetName = McpUtils.text(params, "sheetName", "sheet");
        Integer sheetIndex = McpUtils.intVal(params, "sheetIndex");
        int maxRows = McpUtils.intOrDefault(params, "maxRows", 50);
        int maxCols = McpUtils.intOrDefault(params, "maxCols", 30);

        String rangeStr = McpUtils.text(params, "range");
        boolean showFormula = truthy(params, "showFormula");
        boolean showStyle = truthy(params, "showStyle");

        SheetOps.CellRange rr = null;
        if (rangeStr != null && !rangeStr.trim().isEmpty()) {
            rr = SheetOps.parseA1Range(rangeStr.trim());
        }

        ReadBounds b = computeBounds(rr, maxRows, maxCols);
        boolean stream = XlsxReader.isXlsxFile(file) && !showStyle
            && XlsxReader.shouldStreamRead(file, b.startRow, b.startCol, b.endRow, b.endCol);

        if (stream) {
            String effName = resolveSheetNameForXlsx(file, sheetName, sheetIndex);
            String body =
                XlsxReader.readRangeAsTsvBody(file, effName, b.startRow, b.startCol, b.endRow, b.endCol, showFormula);
            String out = "READ_MODE=STREAMING_XLSX\n" + "FILE=" + file.getAbsolutePath() + '\n' + "SHEET=" + effName
                + '\n' + "RANGE_ROWS=" + b.startRow + ':' + b.endRow + " COLS=" + b.startCol + ':' + b.endCol + '\n'
                + "showFormula=" + showFormula + " showStyle=" + false + '\n' + '\n' + body;
            McpServiceLog.cmdAndResult(ctx.log(), McpServiceLog.SERVICE_EXCEL,
                "readSheet file=" + file.getAbsolutePath() + " sheet=" + effName + " STREAM rangeRows=" + b.startRow
                    + "-" + b.endRow + " cols=" + b.startCol + "-" + b.endCol + " showFormula=" + showFormula,
                out);
            return CommandResult.of(out);
        }

        // —— DOM 路径（.xls、需要样式、或小 xlsx）——
        Workbook wb = Workbooks.getInstance().getReadOnly(file);
        Sheet sheet = resolveSheetDom(wb, sheetName, sheetIndex);

        ReadBounds bDom = computeBoundsForDom(sheet, rr, maxRows, maxCols);

        DataFormatter fmt = new DataFormatter();
        FormulaEvaluator eval = wb.getCreationHelper().createFormulaEvaluator();

        StringBuilder sb = new StringBuilder();
        sb.append("READ_MODE=DOM_WORKBOOK\n");
        sb.append("FILE=").append(file.getAbsolutePath()).append('\n');
        sb.append("SHEET=").append(sheet.getSheetName()).append('\n');
        sb.append("RANGE_ROWS=").append(bDom.startRow).append(':').append(bDom.endRow).append(" COLS=")
            .append(bDom.startCol).append(':').append(bDom.endCol).append('\n');
        sb.append("MAX_ROWS=").append(maxRows).append(" MAX_COLS=").append(maxCols).append('\n');
        sb.append("showFormula=").append(showFormula).append(" showStyle=").append(showStyle).append('\n');
        sb.append('\n');

        for (int r = bDom.startRow; r <= bDom.endRow; r++) {
            Row row = sheet.getRow(r);
            sb.append(r);
            if (row == null) {
                sb.append('\t').append("(null)").append('\n');
                continue;
            }
            for (int c = bDom.startCol; c <= bDom.endCol; c++) {
                sb.append('\t');
                Cell cell = row.getCell(c);
                sb.append(formatCellDom(cell, fmt, eval, showFormula, showStyle, wb));
            }
            sb.append('\n');
        }

        String out = sb.toString();
        McpServiceLog.cmdAndResult(ctx.log(), McpServiceLog.SERVICE_EXCEL,
            "readSheet file=" + file.getAbsolutePath() + " sheet=" + sheet.getSheetName() + " DOM rows=" + bDom.startRow
                + "-" + bDom.endRow + " cols=" + bDom.startCol + "-" + bDom.endCol + " showFormula=" + showFormula
                + " showStyle=" + showStyle,
            out);
        return CommandResult.of(out);
    }

    private static final class ReadBounds {
        final int startRow;
        final int startCol;
        final int endRow;
        final int endCol;

        ReadBounds(int startRow, int startCol, int endRow, int endCol) {
            this.startRow = startRow;
            this.startCol = startCol;
            this.endRow = endRow;
            this.endCol = endCol;
        }
    }
}
