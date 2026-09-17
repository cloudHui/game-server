package com.gamer.data.mpcserver.commands;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamer.data.mpcserver.core.CommandDispatcher;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.CommandResult;
import com.gamer.data.mpcserver.core.Process;

/**
 * 帮助命令:输出当前支持的 method 列表与参数提示。
 *
 * <p>
 * 注意:这里的输出是给人看的帮助文本，并非严格的机器协议。
 * </p>
 * 
 * @author liuyunhui
 * @date 2026-04-13
 */
@Process(value = "help", description = "列出可用命令与参数示例。")
public class Help implements CommandHandler {
    @Override
    public CommandResult handle(CommandContext ctx, JsonNode params) {
        List<Map<String, Object>> definitions = new CommandDispatcher().toolDefinitions();
        StringBuilder sb = new StringBuilder("可用命令（以 tools/list schema 为准）:\n");
        for (Map<String, Object> definition : definitions) {
            sb.append("- ").append(definition.get("name")).append(": ").append(definition.get("description"))
                .append('\n');
        }
        return CommandResult.of(sb.toString());
    }
}
