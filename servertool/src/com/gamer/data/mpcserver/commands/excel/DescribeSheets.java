package com.gamer.data.mpcserver.commands.excel;

import com.gamer.data.mpcserver.commands.excel.support.Workbooks;
import com.gamer.data.mpcserver.commands.excel.support.XlsxReader;

import java.io.File;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamer.data.mpcserver.commands.CommandHandler;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.CommandResult;
import com.gamer.data.mpcserver.core.McpServiceLog;
import com.gamer.data.mpcserver.core.Process;

/**
 * 列出 Excel 的工作表信息。
 *
 */
@Process(value = "excel_describe_sheets", description = "列出 Excel 工作表信息。", required = {"fileAbsolutePath"})
public class DescribeSheets implements CommandHandler {
    @Override
    public CommandResult handle(CommandContext ctx, JsonNode params) throws Exception {
        if (ctx == null) {
            throw new IllegalArgumentException("CommandContext不能为空");
        }
        File file = ExcelFiles.require(ctx, params);

        if (XlsxReader.isXlsxFile(file)) {
            String out = XlsxReader.describeXlsxSheets(file);
            McpServiceLog.cmdAndResult(ctx.log(), McpServiceLog.SERVICE_EXCEL,
                "describeSheets xlsx(streamMeta) file=" + file.getAbsolutePath(), out);
            return CommandResult.of(out);
        }

        Workbook wb = Workbooks.getInstance().getReadOnly(file);

        int sheetCount = wb.getNumberOfSheets();
        StringBuilder sb = new StringBuilder();
        sb.append("SHEET_COUNT=").append(sheetCount).append("\n");
        sb.append("SHEET\tROWS\tCOLS\n");

        for (int i = 0; i < sheetCount; i++) {
            Sheet sheet = wb.getSheetAt(i);
            if (sheet == null) {
                continue;
            }

            String sheetName = sheet.getSheetName();
            int lastRow = sheet.getLastRowNum();
            int rows = lastRow + 1;
            if (rows < 0) {
                rows = 0;
            }

            int cols = getHeaderCols(sheet);
            sb.append(sheetName).append("\t").append(rows).append("\t").append(cols).append("\n");
        }

        String out = sb.toString();
        McpServiceLog.cmdAndResult(ctx.log(), McpServiceLog.SERVICE_EXCEL,
            "describeSheets file=" + file.getAbsolutePath(), out);
        return CommandResult.of(out);
    }

    /**
     * 用第 0 行列数估算 sheet 宽度。若第 0 行为空则尝试第 1 行，最多检查前 3 行。
     */
    private int getHeaderCols(Sheet sheet) {
        if (sheet == null) {
            return 0;
        }
        int checkRows = Math.min(sheet.getLastRowNum() + 1, 3);
        for (int r = 0; r < checkRows; r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            short lastCell = row.getLastCellNum();
            if (lastCell > 0) {
                return lastCell;
            }
        }
        return 0;
    }
}
