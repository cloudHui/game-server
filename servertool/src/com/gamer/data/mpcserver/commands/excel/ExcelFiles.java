package com.gamer.data.mpcserver.commands.excel;

import java.io.File;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.McpUtils;

/** Excel 路径统一走 FileSandbox。 */
public final class ExcelFiles {
    private ExcelFiles() {}

    public static File require(CommandContext ctx, JsonNode params) {
        String path = McpUtils.text(params, "fileAbsolutePath", "file");
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("params.fileAbsolutePath不能为空");
        }
        if (ctx == null || ctx.fileSandbox() == null) {
            throw new IllegalStateException("FileSandbox未初始化");
        }
        return ctx.fileSandbox().requireAllowedFile(path.trim());
    }
}
