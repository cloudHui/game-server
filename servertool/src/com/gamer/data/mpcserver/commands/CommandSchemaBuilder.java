package com.gamer.data.mpcserver.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gamer.data.mpcserver.core.Process;

/** 将命令注解转换为 MCP schema。 */
final class CommandSchemaBuilder {

    private CommandSchemaBuilder() {}

    static Map<String, Object> build(String registeredName, CommandHandler handler) {
        Process definition = requireDefinition(registeredName, handler);
        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();
        addParameters(properties, required, definition.required(), true);
        addParameters(properties, required, definition.optional(), false);

        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        Map<String, Object> tool = new HashMap<>();
        tool.put("name", definition.value());
        tool.put("title", titleFromName(definition.value()));
        tool.put("description", definition.description());
        tool.put("inputSchema", schema);
        tool.put("annotations", annotations(definition.readOnly()));
        return tool;
    }

    private static Process requireDefinition(String registeredName, CommandHandler handler) {
        if (handler == null) {
            throw new IllegalStateException("MCP命令处理器不能为空: " + registeredName);
        }
        Process definition = handler.getClass().getAnnotation(Process.class);
        if (definition == null) {
            throw new IllegalStateException("MCP命令缺少@Process定义: " + handler.getClass().getName());
        }
        if (!registeredName.equals(definition.value())) {
            throw new IllegalStateException(
                "MCP命令注册名不一致: registered=" + registeredName + ", definition=" + definition.value());
        }
        if (definition.description().trim().isEmpty()) {
            throw new IllegalStateException("MCP命令缺少说明: " + registeredName);
        }
        return definition;
    }

    private static void addParameters(Map<String, Object> properties, List<String> requiredOutput, String[] names,
        boolean required) {
        for (String name : names) {
            properties.put(name, schemaType(inferType(name)));
            if (required) {
                requiredOutput.add(name);
            }
        }
    }

    /**
     * 按参数名猜测 JSON 类型：含数量/偏移等关键字视为 integer，其余为 string。
     */
    private static String inferType(String name) {
        String lower = name.toLowerCase();
        return lower.contains("max") || lower.contains("limit") || lower.contains("offset") || lower.contains("count")
            || lower.contains("index") || lower.contains("seconds") || "head".equals(lower) || "tail".equals(lower)
                ? "integer" : "string";
    }

    private static Map<String, Object> schemaType(String type) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", type);
        return schema;
    }

    /**
     * 将命令名按 '_' 分段并转为 Title Case，例如 git_run → Git Run。
     */
    private static String titleFromName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        String[] parts = name.split("_");
        StringBuilder title = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                continue;
            }
            if (title.length() > 0) {
                title.append(' ');
            }
            title.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                title.append(part.substring(1));
            }
        }
        return title.toString();
    }

    private static Map<String, Object> annotations(boolean readOnly) {
        Map<String, Object> values = new HashMap<>();
        values.put("readOnlyHint", readOnly);
        values.put("destructiveHint", !readOnly);
        values.put("idempotentHint", readOnly);
        values.put("openWorldHint", Boolean.FALSE);
        return values;
    }
}
