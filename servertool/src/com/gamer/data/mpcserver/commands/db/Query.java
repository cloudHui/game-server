package com.gamer.data.mpcserver.commands.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamer.data.mpcserver.commands.CommandHandler;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.CommandResult;
import com.gamer.data.mpcserver.core.DbCommandSupport;
import com.gamer.data.mpcserver.core.DbDefaults;
import com.gamer.data.mpcserver.core.McpServiceLog;
import com.gamer.data.mpcserver.core.McpUtils;
import com.gamer.data.mpcserver.core.Process;

/** 执行只读查询或表级 DDL。 */
@Process(value = "local_sql_query", description = "执行受限 SQL；target 选择 local/test。", readOnly = false,
    required = {"sql"},
    optional = {"target", "database", "maxRows", "queryTimeoutSeconds", "maxOutputChars", "maxCellChars"})
public class Query implements CommandHandler {
    private static final int DEFAULT_ROWS = 50;
    private static final int MAX_ROWS = 2000;
    private static final int DEFAULT_TIMEOUT = 10;
    private static final int MAX_TIMEOUT = 120;
    private static final int DEFAULT_OUTPUT = 200000;
    private static final int MAX_OUTPUT = 1000000;
    private static final int DEFAULT_CELL = 2000;
    private static final int MAX_CELL = 20000;

    @Override
    public CommandResult handle(CommandContext ctx, JsonNode params) throws Exception {
        String sql = McpUtils.text(params, "sql");
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("params.sql不能为空");
        }
        sql = sql.trim().replace("\n", " ").replace("\r", " ").replace("\t", " ");
        if (sql.indexOf(';') >= 0) {
            throw new IllegalArgumentException("不允许多语句或分号");
        }

        String upper = sql.toUpperCase(Locale.ROOT);
        boolean query = startsWithWord(upper);
        boolean ddl = upper.matches("^(CREATE( TEMPORARY)?|ALTER|DROP) TABLE\\b.*");
        if (!query && !ddl) {
            throw new IllegalArgumentException("仅允许 SELECT/SHOW/DESC/DESCRIBE 或 CREATE/ALTER/DROP TABLE");
        }

        DbDefaults defaults = DbCommandSupport.requireDbDefaults(ctx, params);
        String url = defaults.buildJdbcUrl(DbCommandSupport.resolveDatabase(params, defaults));
        int maxRows = number(params, "maxRows", DEFAULT_ROWS, MAX_ROWS);
        int timeout = number(params, "queryTimeoutSeconds", DEFAULT_TIMEOUT, MAX_TIMEOUT);
        int maxOutput = number(params, "maxOutputChars", DEFAULT_OUTPUT, MAX_OUTPUT);
        int maxCell = number(params, "maxCellChars", DEFAULT_CELL, MAX_CELL);

        try (Connection connection = DriverManager.getConnection(url, defaults.user(), defaults.password());
            PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(timeout);
            if (!query) {
                int count = statement.executeUpdate();
                McpServiceLog.cmd(ctx.log(), McpServiceLog.SERVICE_DB, "ddl sql=" + McpUtils.oneLine(sql));
                return CommandResult.of("DDL executed ok; updateCount=" + count);
            }
            statement.setMaxRows(maxRows);
            try (ResultSet result = statement.executeQuery()) {
                return CommandResult.of(render(result, url, sql, maxRows, timeout, maxOutput, maxCell));
            }
        }
    }

    private String render(ResultSet result, String url, String sql, int maxRows, int timeout, int maxOutput,
        int maxCell) throws Exception {
        StringBuilder out = new StringBuilder();
        appendWithLimit(out, "URL=" + url, maxOutput);
        appendWithLimit(out, "SQL=" + McpUtils.oneLine(sql), maxOutput);
        appendWithLimit(out, "MAX_ROWS=" + maxRows + " QUERY_TIMEOUT_SECONDS=" + timeout, maxOutput);
        appendWithLimit(out, "", maxOutput);

        ResultSetMetaData metadata = result.getMetaData();
        int columns = metadata.getColumnCount();
        StringBuilder line = new StringBuilder();
        for (int column = 1; column <= columns; column++) {
            appendCell(line, metadata.getColumnLabel(column), column);
        }
        if (!appendWithLimit(out, line.toString(), maxOutput)) {
            return out.toString();
        }

        while (result.next()) {
            line.setLength(0);
            for (int column = 1; column <= columns; column++) {
                Object value = result.getObject(column);
                String text = value == null ? "" : McpUtils.oneLine(String.valueOf(value));
                if (text.length() > maxCell) {
                    text = text.substring(0, maxCell) + "...";
                }
                appendCell(line, text, column);
            }
            if (!appendWithLimit(out, line.toString(), maxOutput)) {
                return out.toString();
            }
        }
        return out.toString();
    }

    private void appendCell(StringBuilder line, String text, int column) {
        if (column > 1) {
            line.append('\t');
        }
        line.append(text);
    }

    private boolean startsWithWord(String sql) {
        for (String word : new String[]{"SELECT", "SHOW", "DESC", "DESCRIBE"}) {
            if (sql.startsWith(word)
                && (sql.length() == word.length() || Character.isWhitespace(sql.charAt(word.length())))) {
                return true;
            }
        }
        return false;
    }

    private int number(JsonNode params, String name, int defaultValue, int max) {
        Integer value = McpUtils.intVal(params, name);
        return value == null || value <= 0 ? defaultValue : Math.min(value, max);
    }
}
