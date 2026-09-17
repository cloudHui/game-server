package com.gamer.data.mpcserver.commands.excel.edit;

import com.gamer.data.mpcserver.commands.excel.support.SheetOps;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.McpUtils;
import com.gamer.data.mpcserver.core.Process;

/**
 * 格式化 Excel 指定 range（MCP 实现：应用简单 CellStyle）。
 *
 * <p>
 * styles（字符串）支持：
 * </p>
 * <ul>
 * <li>JSON 对象，例如：{"bold":true,"align":"center","vAlign":"middle"}</li>
 * </ul>
 *
 * <p>
 * 为了保证跨 POI 版本可编译，颜色相关字段先不做强约束。
 * </p>
 */
@Process(value = "excel_format_range", description = "格式化 Excel 区域。", readOnly = false,
    required = {"fileAbsolutePath", "sheetName", "range", "styles"})
public class FormatRange extends EditCommand {
    @Override
    protected Result edit(CommandContext ctx, JsonNode params, EditSession session) {
        String sheetName = session.requireText(params, "sheetName", "sheet", "params.sheetName不能为空");

        String range = McpUtils.text(params, "range");
        if (range == null || range.trim().isEmpty()) {
            throw new IllegalArgumentException("params.range不能为空（如 A1:C3）");
        }

        String styles = McpUtils.text(params, "styles");
        if (styles == null) {
            styles = "";
        }

        Workbook wb = session.workbook();
        Sheet sheet = session.sheet(sheetName, false, "找不到sheet: " + sheetName);

        SheetOps.CellRange cr = SheetOps.parseA1Range(range);
        JsonNode styleNode = null;
        String s = styles.trim();
        if (!s.isEmpty() && (s.startsWith("{") || s.startsWith("["))) {
            try {
                // styles 预期是 object；如果传了 array，按空处理
                styleNode = ctx.mapper().readTree(s);
            } catch (Exception ignored) {
            }
        }

        CellStyle style = SheetOps.buildSimpleCellStyle(wb, styleNode);
        SheetOps.applyStyleToRange(sheet, cr, style);

        String ok = "Successfully formatted range=" + range + " sheet=" + sheetName + " file="
            + session.file().getAbsolutePath();
        return result(ok,
            "formatRange file=" + session.file().getAbsolutePath() + " sheet=" + sheetName + " range=" + range);
    }
}
