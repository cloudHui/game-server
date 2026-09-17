package com.gamer.data.mpcserver.commands.excel.edit;

import com.gamer.data.mpcserver.commands.excel.support.SheetOps;

import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.McpUtils;
import com.gamer.data.mpcserver.core.Process;

/**
 * 写入 Excel sheet 的指定 A1 范围。
 *
 * <p>
 * 参数（params）：
 * </p>
 * <ul>
 * <li>fileAbsolutePath（必填）：Excel 文件绝对路径</li>
 * <li>sheetName（必填）：目标 Sheet 名称</li>
 * <li>newSheet（可选）：Sheet 不存在时是否新建（true/false 或 "true"/"false"）</li>
 * <li>range（必填）：如 A1 或 A1:C3</li>
 * <li>values（必填）：二维数组 JSON 字符串 或 TSV 字符串 或单值</li>
 * </ul>
 *
 * <p>
 * values 支持：
 * </p>
 * <ul>
 * <li>JSON：二维数组（[ [a,b], [c,d] ]）</li>
 * <li>TSV：用换行分行、用 tab 分列</li>
 * <li>纯文本：写入 range 左上角</li>
 * </ul>
 */
@Process(value = "excel_write_to_sheet", description = "写入指定工作表区域。", readOnly = false,
    required = {"fileAbsolutePath", "sheetName", "range", "values"}, optional = {"newSheet"})
public class WriteSheet extends EditCommand {
    @Override
    protected Result edit(CommandContext ctx, JsonNode params, EditSession session) throws Exception {
        String sheetName = session.requireText(params, "sheetName", "sheet", "params.sheetName不能为空");

        String range = McpUtils.text(params, "range");
        if (range == null || range.trim().isEmpty()) {
            throw new IllegalArgumentException("params.range不能为空（如 A1 或 A1:C3）");
        }

        String values = McpUtils.text(params, "values");
        if (values == null) {
            throw new IllegalArgumentException("params.values不能为空");
        }

        String newSheetStr = McpUtils.text(params, "newSheet");
        boolean newSheet = false;
        if (newSheetStr != null) {
            String s = newSheetStr.trim();
            newSheet = "true".equalsIgnoreCase(s) || "1".equals(s);
        }

        Sheet sheet = session.sheet(sheetName, newSheet, "Sheet不存在: " + sheetName + ", newSheet=false");

        SheetOps.CellRange cr = SheetOps.parseA1Range(range);
        ObjectMapper mapper = ctx.mapper();
        List<List<String>> values2D = SheetOps.parseValues2D(values, mapper);

        SheetOps.fillRangeByValues(sheet, cr, values2D);

        String ok =
            "Successfully wrote range=" + range + " sheet=" + sheetName + " file=" + session.file().getAbsolutePath();
        return result(ok, "writeToSheet file=" + session.file().getAbsolutePath() + " sheet=" + sheetName + " range="
            + range + " newSheet=" + newSheet);
    }
}
