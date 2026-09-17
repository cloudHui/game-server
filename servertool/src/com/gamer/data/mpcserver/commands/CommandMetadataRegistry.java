package com.gamer.data.mpcserver.commands;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** MCP 命令定义目录。 */
public final class CommandMetadataRegistry {

    private CommandMetadataRegistry() {}

    /** 从实际注册的 Handler 构建工具定义。 */
    public static List<Map<String, Object>> buildToolDefinitions(Map<String, CommandHandler> handlers) {
        if (handlers == null) {
            return Collections.emptyList();
        }
        return handlers.entrySet().stream().map(entry -> CommandSchemaBuilder.build(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(definition -> String.valueOf(definition.get("name"))))
            .collect(Collectors.toList());
    }
}
