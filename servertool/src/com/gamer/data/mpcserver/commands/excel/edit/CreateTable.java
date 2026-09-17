package com.gamer.data.mpcserver.commands.excel.edit;

import com.gamer.data.mpcserver.commands.excel.support.SheetOps;

import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.McpUtils;
import com.gamer.data.mpcserver.core.Process;

/**
 * 创建“表格区域”（MCP 实现为：NamedRange + AutoFilter）。
 *
 * <p>
 * POI 无法直接完整模拟 Excel 结构化表（XSSFTable）特性， 因此本工具提供最小可用语义：把某个 A1 区域注册为命名区域，并启用自动筛选。
 * </p>
 *
 * <p>
 * 参数：
 * </p>
 * <ul>
 * <li>fileAbsolutePath（必填）：Excel 文件绝对路径</li>
 * <li>sheetName（必填）：Sheet 名称</li>
 * <li>tableName（必填）：表名（用于 NamedRange 名称）</li>
 * <li>range（可选）：A1 或 A1:C3；未指定则按 used range 自动推断</li>
 * </ul>
 */
@Process(value = "excel_create_table", description = "创建 Excel 表格。", readOnly = false,
    required = {"fileAbsolutePath", "sheetName", "tableName"}, optional = {"range"})
public class CreateTable extends EditCommand {
    @Override
    protected Result edit(CommandContext ctx, JsonNode params, EditSession session) {
        String sheetName = session.requireText(params, "sheetName", "sheet", "params.sheetName不能为空");
        String tableName = session.requireText(params, "tableName", null, "params.tableName不能为空");

        String range = McpUtils.text(params, "range");
        Workbook wb = session.workbook();
        Sheet sheet = session.sheet(sheetName, false, "找不到sheet: " + sheetName);

        SheetOps.CellRange cr;
        if (range != null && !range.trim().isEmpty()) {
            cr = SheetOps.parseA1Range(range);
        } else {
            cr = SheetOps.calcUsedRange(sheet);
        }

        // 建 NamedRange：同名存在则直接复用 Name 并更新 refersToFormula
        Name existed = wb.getName(tableName);

        CellReference a = new CellReference(cr.startRow, cr.startCol, true, true);
        CellReference b = new CellReference(cr.endRow, cr.endCol, true, true);
        String refers = "'" + sheet.getSheetName() + "'!" + a.formatAsString() + ":" + b.formatAsString();

        if (existed != null) {
            try {
                existed.setRefersToFormula(refers);
            } catch (Exception ignored) {
                // ignore
            }
        } else {
            Name nameObj = wb.createName();
            nameObj.setNameName(tableName);
            nameObj.setRefersToFormula(refers);
        }

        // 启用 AutoFilter（通常以首行作为 header）
        try {
            sheet.setAutoFilter(new CellRangeAddress(cr.startRow, cr.endRow, cr.startCol, cr.endCol));
        } catch (Exception ignored) {
            // ignore
        }

        String ok = "Successfully created table: name=" + tableName + " refers=" + refers;
        return result(ok, "createTableRegion file=" + session.file().getAbsolutePath() + " sheet=" + sheetName
            + " tableName=" + tableName + " range=" + (range == null ? "" : range.trim()));
    }
}
