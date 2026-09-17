package com.gamer.data.mpcserver.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.gamer.data.mpcserver.commands.CommandHandler;
import com.gamer.data.mpcserver.commands.CommandMetadataRegistry;

/** 命令实例与 MCP 工具定义。 */
public final class CommandDispatcher {
    private static final Map<String, CommandHandler> HANDLERS;
    private static final List<Map<String, Object>> TOOLS;
    static {
        long start = System.currentTimeMillis();
        HANDLERS = HandlerRegistry.buildSingle(CommandHandler.class);
        TOOLS = CommandMetadataRegistry.buildToolDefinitions(HANDLERS);
        System.err.printf("MCP tools registered: %d, cost: %dms%n", HANDLERS.size(),
            System.currentTimeMillis() - start);
    }

    public CommandHandler get(String method) {
        return HANDLERS.get(method);
    }

    public List<Map<String, Object>> toolDefinitions() {
        return new ArrayList<>(TOOLS);
    }
}
