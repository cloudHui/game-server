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
import com.gamer.data.mpcserver.core.Process;

/** 只读探测当前数据库账号权限。 */
@Process(value = "local_check_permissions", description = "检查数据库权限。", optional = {"target"})
public final class Permissions implements CommandHandler {
    @Override
    public CommandResult handle(CommandContext context, JsonNode params) throws Exception {
        DbDefaults database = DbCommandSupport.requireDbDefaults(context, params);
        String url = database.buildJdbcUrl("mysql");
        StringBuilder out = new StringBuilder("URL=").append(url).append('\n');
        try (Connection connection = DriverManager.getConnection(url, database.user(), database.password());
            Statement statement = connection.createStatement()) {
            out.append("CURRENT_USER=").append(value(statement, "SELECT CURRENT_USER()")).append('\n');
            probe(out, statement, "HAS_SHOW_DATABASES", "SHOW DATABASES");
            probe(out, statement, "HAS_INFORMATION_SCHEMA_TABLES",
                "SELECT table_name FROM information_schema.tables LIMIT 1");
            probe(out, statement, "HAS_MYSQL_GENERAL_LOG_QUERY", "SELECT event_time FROM mysql.general_log LIMIT 1");
        }
        return CommandResult.of(out.toString());
    }

    private String value(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? McpUtils.nvl(result.getString(1)) : "";
        }
    }

    private void probe(StringBuilder out, Statement statement, String name, String sql) {
        out.append(name).append('=');
        try (ResultSet ignored = statement.executeQuery(sql)) {
            out.append("true\n");
        } catch (Exception e) {
            out.append("false; err=").append(McpUtils.oneLine(e.getMessage())).append('\n');
        }
    }
}
