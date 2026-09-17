package com.gamer.data.mpcserver.commands.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamer.data.mpcserver.commands.CommandHandler;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.CommandResult;
import com.gamer.data.mpcserver.core.DbCommandSupport;
import com.gamer.data.mpcserver.core.DbDefaults;
import com.gamer.data.mpcserver.core.McpUtils;

/** mysql.general_log 公共查询。 */
abstract class GeneralLogs implements CommandHandler {
    private final String type;
    private final String filter;

    GeneralLogs(String type, String filter) {
        this.type = type;
        this.filter = filter;
    }

    @Override
    public CommandResult handle(CommandContext context, JsonNode params) throws Exception {
        DbDefaults database = DbCommandSupport.requireDbDefaults(context, params);
        int limit = number(params, "limit", 1, 50, 200);
        int offset = number(params, "offset", 0, 0, 10000);
        String sql = "SELECT event_time,user_host,thread_id,argument FROM mysql.general_log "
            + "WHERE command_type='Query' AND (" + filter + ") ORDER BY event_time DESC LIMIT " + limit + " OFFSET "
            + offset;
        StringBuilder out = new StringBuilder("URL=").append(database.buildJdbcUrl("mysql")).append("\nSQL=")
            .append(sql).append("\n\nEVENT_TIME\tUSER_HOST\tTHREAD_ID\tARGUMENT\n");

        try (
            Connection connection =
                DriverManager.getConnection(database.buildJdbcUrl("mysql"), database.user(), database.password());
            Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            int rows = 0;
            while (result.next()) {
                rows++;
                out.append(value(result.getObject(1))).append('\t').append(value(result.getObject(2))).append('\t')
                    .append(value(result.getObject(3))).append('\t').append(McpUtils.oneLine(result.getString(4)))
                    .append('\n');
            }
            out.append("\nROWS=").append(rows).append("\nTYPE=").append(type);
            return CommandResult.of(out.toString());
        }
    }

    private int number(JsonNode params, String name, int minimum, int defaultValue, int maximum) {
        Integer value = McpUtils.intVal(params, name);
        return value == null ? defaultValue : Math.max(minimum, Math.min(value, maximum));
    }

    private String value(Object value) {
        return value == null ? "" : McpUtils.oneLine(String.valueOf(value));
    }
}
