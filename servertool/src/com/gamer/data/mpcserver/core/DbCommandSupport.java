package com.gamer.data.mpcserver.core;

import com.fasterxml.jackson.databind.JsonNode;

/** 数据库目标与库名解析。 */
public final class DbCommandSupport {
    private DbCommandSupport() {
    }

    public static DbDefaults requireDbDefaults(CommandContext context, JsonNode params) {
        String target = McpUtils.text(params, "target");
        target = target == null || target.trim().isEmpty() ? "local" : target.trim().toLowerCase(java.util.Locale.ROOT);
        DbDefaults defaults = context == null ? null : context.dbDefaults(target);
        if (defaults == null) {
            throw new IllegalArgumentException("target 仅支持 local 或 test");
        }
        return defaults;
    }

    public static String resolveDatabase(JsonNode params, DbDefaults defaults) {
        String database = McpUtils.text(params, "database");
        return database == null || database.trim().isEmpty() ? defaults.database() : database;
    }

}
