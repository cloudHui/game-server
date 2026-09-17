package com.gamer.data.mpcserver.commands.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamer.data.mpcserver.commands.CommandHandler;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.CommandResult;
import com.gamer.data.mpcserver.core.DbCommandSupport;
import com.gamer.data.mpcserver.core.DbDefaults;
import com.gamer.data.mpcserver.core.Process;

/** 获取非系统库及其表列表。 */
@Process(value = "local_get_database_info", description = "获取数据库表信息。", optional = {"target"})
public final class DatabaseInfo implements CommandHandler {
    private static final int MAX_DATABASES = 50;
    private static final int MAX_TABLES = 200;
    private static final Set<String> SYSTEM_DATABASES =
        new HashSet<>(Arrays.asList("information_schema", "mysql", "performance_schema", "sys"));

    @Override
    public CommandResult handle(CommandContext context, JsonNode params) throws Exception {
        DbDefaults database = DbCommandSupport.requireDbDefaults(context, params);
        String url = database.buildJdbcUrl("mysql");
        StringBuilder out = new StringBuilder("URL=").append(url).append("\nDB\tTABLE\n");

        try (Connection connection = DriverManager.getConnection(url, database.user(), database.password())) {
            List<String> databases = databases(connection);
            for (String name : databases) {
                tables(out, connection, name);
            }
        }
        return CommandResult.of(out.toString());
    }

    private List<String> databases(Connection connection) throws Exception {
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
            ResultSet result = statement.executeQuery("SHOW DATABASES")) {
            while (result.next() && names.size() < MAX_DATABASES) {
                String name = result.getString(1);
                if (name != null && !SYSTEM_DATABASES.contains(name.trim())) {
                    names.add(name.trim());
                }
            }
        }
        return names;
    }

    private void tables(StringBuilder out, Connection connection, String database) throws Exception {
        String sql =
            "SELECT table_name FROM information_schema.tables " + "WHERE table_schema=? ORDER BY table_name LIMIT ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, database);
            statement.setInt(2, MAX_TABLES + 1);
            try (ResultSet result = statement.executeQuery()) {
                append(out, database, result);
            }
        } catch (Exception denied) {
            String escaped = database.replace("`", "``");
            try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SHOW TABLES FROM `" + escaped + "`")) {
                append(out, database, result);
            }
        }
    }

    private void append(StringBuilder out, String database, ResultSet result) throws Exception {
        int count = 0;
        while (result.next()) {
            if (count < MAX_TABLES) {
                out.append(database).append('\t').append(result.getString(1)).append('\n');
            }
            if (++count > MAX_TABLES) {
                break;
            }
        }
        if (count == 0) {
            out.append(database).append("\t(no tables)\n");
        } else if (count > MAX_TABLES) {
            out.append(database).append("\t... more tables omitted\n");
        }
    }
}
