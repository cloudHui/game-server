package com.gamer.data.mpcserver.commands.excel.edit;

import java.io.File;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamer.data.mpcserver.commands.excel.ExcelFiles;
import com.gamer.data.mpcserver.commands.excel.support.SheetOps;
import com.gamer.data.mpcserver.commands.excel.support.Workbooks;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.McpUtils;

/** 单个 Workbook 的编辑会话。 */
final class EditSession {

    private final File file;
    private final Workbook workbook;
    private boolean saved;

    private EditSession(File file, Workbook workbook) {
        this.file = file;
        this.workbook = workbook;
    }

    static EditSession open(CommandContext ctx, JsonNode params) throws Exception {
        File file = ExcelFiles.require(ctx, params);
        return new EditSession(file, Workbooks.getInstance().get(file));
    }

    String requireText(JsonNode params, String name, String alias, String errorMessage) {
        return McpUtils.requiredText(params, errorMessage, name, alias);
    }

    Sheet sheet(String name, boolean createIfMissing, String missingMessage) {
        Sheet sheet = workbook.getSheet(name);
        if (sheet == null && createIfMissing) {
            sheet = workbook.createSheet(name);
        }
        if (sheet == null) {
            throw new IllegalArgumentException(missingMessage);
        }
        return sheet;
    }

    File file() {
        return file;
    }

    Workbook workbook() {
        return workbook;
    }

    void save() throws Exception {
        SheetOps.saveWorkbook(workbook, file);
        Workbooks.getInstance().markFileSaved(file, workbook);
        saved = true;
    }

    void discard() {
        if (!saved) {
            Workbooks.getInstance().discard(file, workbook);
        }
    }

}
