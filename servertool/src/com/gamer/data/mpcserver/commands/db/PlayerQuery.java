package com.gamer.data.mpcserver.commands.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamer.data.mpcserver.commands.CommandHandler;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.CommandResult;
import com.gamer.data.mpcserver.core.McpUtils;
import com.gamer.data.mpcserver.core.Process;

/**
 * 参数生成只读等值 SQL，在 MCP 进程内执行。
 * 靠 MySQL max_execution_time 掐 SELECT；设失败不挡查询。
 */
@Process(value = "player_db_query",
    description = "必须roleId；action=connections/schema/query；配置连接、索引等值SELECT、总超时5秒。",
    required = {"roleId"}, optional = {"action", "connection", "table", "idColumn", "maxRows"})
public final class PlayerQuery implements CommandHandler {
    /** 含连接、EXPLAIN、结果读取的总时限（毫秒）。 */
    public static final long TIMEOUT_MS = 5000;

    @Override
    public CommandResult handle(CommandContext ctx, JsonNode params) throws Exception {
        String roleId = roleId(params);
        String action = params.path("action").asText("query");
        if ("connections".equals(action)) {
            return CommandResult.of(ctx.mapper().writeValueAsString(ctx.playerDatabases().describe()));
        }
        if (!"query".equals(action) && !"schema".equals(action)) {
            throw new IllegalArgumentException("action 只支持 connections/schema/query");
        }
        String name = McpUtils.requiredText(params, "必须指定 connection 配置名称", "connection");
        try {
            Map<String, Object> result = execute(ctx.playerDatabases().require(name), params, roleId,
                System.currentTimeMillis() + TIMEOUT_MS);
            return CommandResult.of(ctx.mapper().writeValueAsString(result));
        } catch (SQLException e) {
            throw new IllegalArgumentException("数据库查询失败，SQLState=" + e.getSQLState() + " code=" + e.getErrorCode());
        }
    }

    /**
     * 构造并执行只读查询，不接受任意 SQL。
     *
     * @param db 已校验的连接配置
     * @param params 工具参数
     * @param roleId 已校验的完整 roleId
     * @param deadline 本次请求截止时间戳（毫秒）
     * @return 有界查询结果
     */
    private static Map<String, Object> execute(JsonNode db, JsonNode params, String roleId, long deadline)
        throws SQLException {
        String database = identifier(db.path("database").asText());
        String host = db.path("host").asText();
        if (!host.matches("[a-zA-Z0-9.:\\[\\]-]+")) {
            throw new IllegalArgumentException("host 格式无效");
        }
        String table = params.path("table").asText("");
        String action = params.path("action").asText("query");
        String column = params.path("idColumn").asText("roleId");
        if ("query".equals(action)) {
            identifier(table);
            identifier(column);
        } else if (!table.isEmpty()) {
            identifier(table);
        }
        String url = "jdbc:mysql://" + host + ":" + db.path("port").asInt() + "/" + database
            + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC&sslMode=VERIFY_IDENTITY"
            + "&allowPublicKeyRetrieval=false&connectTimeout=4500&socketTimeout=4500"
            + "&queryTimeoutKillsConnection=true&allowMultiQueries=false";
        Connection conn = DriverManager.getConnection(url, db.path("user").asText(), db.path("password").asText());
        try {
            conn.setReadOnly(true);
            if ("schema".equals(action)) {
                return schema(conn, table, deadline);
            }
            // 不接受视图，避免隐藏函数、跨库或非索引访问。
            List<Map<String, Object>> tables = select(conn,
                "SHOW FULL TABLES WHERE `Tables_in_" + database + "` = '" + table
                    + "' AND Table_type = 'BASE TABLE'", null, 2, deadline);
            if (tables.isEmpty()) {
                throw new IllegalArgumentException("必须指定当前配置库中的基础表");
            }
            int limit = Math.min(1000, Math.max(1, params.path("maxRows").asInt(200)));
            String sql = "SELECT * FROM `" + table + "` WHERE `" + column + "` = ? LIMIT " + (limit + 1);
            List<Map<String, Object>> plan = select(conn, "EXPLAIN " + sql, roleId, 100, deadline);
            validatePlan(plan);
            // 只在真正的 SELECT 前设服务端限时；SHOW/EXPLAIN 不受 max_execution_time 约束。
            limitSelect(conn, deadline);
            List<Map<String, Object>> rows = select(conn, sql, roleId, limit + 1, deadline);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("sql", sql);
            out.put("roleId", roleId);
            out.put("plan", plan);
            out.put("truncated", rows.size() > limit);
            out.put("rows", rows.size() > limit ? rows.subList(0, limit) : rows);
            return out;
        } finally {
            McpUtils.tryClose(conn);
        }
    }

    /**
     * @param conn 只读连接
     * @param table 空则列表，非空则字段/索引
     * @param deadline 本次截止时间戳（毫秒）
     * @return schema 结果
     */
    private static Map<String, Object> schema(Connection conn, String table, long deadline) throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();
        if (table.isEmpty()) {
            List<Map<String, Object>> tables = select(conn,
                "SHOW FULL TABLES WHERE Table_type = 'BASE TABLE'", null, 1000, deadline);
            out.put("tables", tables);
            out.put("truncated", tables.size() >= 1000);
            return out;
        }
        List<Map<String, Object>> columns = select(conn, "SHOW FULL COLUMNS FROM `" + table + "`", null, 1000, deadline);
        List<Map<String, Object>> indexes = select(conn, "SHOW INDEX FROM `" + table + "`", null, 1000, deadline);
        out.put("columns", columns);
        out.put("indexes", indexes);
        out.put("truncated", columns.size() >= 1000 || indexes.size() >= 1000);
        return out;
    }

    /**
     * 完整正整数 roleId，不通过浮点数或关联 ID 转换。
     *
     * @param params 工具参数
     * @return 去掉首尾空格后的完整 roleId
     */
    private static String roleId(JsonNode params) {
        String value = McpUtils.requiredText(params, "必须提供完整正整数 roleId", "roleId");
        if (!value.matches("[1-9][0-9]{0,19}")) {
            throw new IllegalArgumentException("roleId 必须是完整正整数");
        }
        return value;
    }

    /**
     * @param value 单个表/列/库标识符
     * @return 已验证原值
     */
    private static String identifier(String value) {
        if (value == null || !value.matches("[a-zA-Z_][a-zA-Z0-9_$]*")) {
            throw new IllegalArgumentException("表/列/库名只能包含字母、数字、下划线和$，不可跨库");
        }
        return value;
    }

    /**
     * 每个访问表必须使用有界索引。
     *
     * @param plan EXPLAIN 全部行
     */
    private static void validatePlan(List<Map<String, Object>> plan) {
        if (plan.isEmpty()) {
            throw new IllegalArgumentException("EXPLAIN 无执行计划，拒绝查询");
        }
        for (Map<String, Object> row : plan) {
            String type = String.valueOf(row.get("type"));
            if (row.get("key") == null || row.get("key").toString().isEmpty()
                || !type.matches("(?i)const|eq_ref|ref|range|index_merge")) {
                throw new IllegalArgumentException("EXPLAIN 拒绝：未使用有界索引；table=" + row.get("table")
                    + " type=" + type + " key=" + row.get("key"));
            }
        }
    }

    /**
     * 复用元数据、执行计划和业务结果读取；业务值一律通过占位符绑定。
     *
     * @param conn 只读连接
     * @param sql 已构造语句
     * @param roleId 等值绑定；元数据查询传 null
     * @param limit 最大行数
     * @param deadline 本次截止时间戳（毫秒）
     * @return 字符串化后的行
     */
    private static List<Map<String, Object>> select(Connection conn, String sql, String roleId, int limit,
        long deadline) throws SQLException {
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = null;
        try {
            stmt.setQueryTimeout(jdbcTimeout(deadline));
            stmt.setMaxRows(limit);
            if (roleId != null) {
                stmt.setString(1, roleId);
            }
            rs = stmt.executeQuery();
            List<Map<String, Object>> rows = new ArrayList<>();
            ResultSetMetaData meta = rs.getMetaData();
            int chars = 0;
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String value = rs.getString(i);
                    if (value != null && value.length() > 4000) {
                        value = value.substring(0, 4000) + "...[cell truncated]";
                    }
                    chars += value == null ? 0 : value.length();
                    if (chars > 100000) {
                        throw new IllegalArgumentException("结果过大；缩小 maxRows 后查询");
                    }
                    row.put(meta.getColumnLabel(i), value);
                }
                rows.add(row);
            }
            return rows;
        } finally {
            McpUtils.tryClose(rs);
            McpUtils.tryClose(stmt);
        }
    }

    /**
     * 只设 SELECT 服务端限时；失败忽略，避免测服权限/版本把整次查询卡死。
     *
     * @param conn 当前连接
     * @param deadline 本次截止时间戳（毫秒）
     */
    private static void limitSelect(Connection conn, long deadline) {
        Statement setup = null;
        try {
            setup = conn.createStatement();
            setup.setQueryTimeout(1);
            setup.execute("SET SESSION max_execution_time=" + remaining(deadline));
        } catch (SQLException ignored) {
            // 无权限或版本不支持时语句仍受 JDBC queryTimeout/socketTimeout 约束。
        } finally {
            McpUtils.tryClose(setup);
        }
    }

    /**
     * @param deadline 截止时间戳（毫秒）
     * @return JDBC 秒级超时，至少 1 秒，且不超过剩余时限
     */
    private static int jdbcTimeout(long deadline) {
        return Math.max(1, remaining(deadline) / 1000);
    }

    /**
     * @param deadline 截止时间戳（毫秒）
     * @return 剩余毫秒，上限 5000
     */
    private static int remaining(long deadline) {
        long left = deadline - System.currentTimeMillis() - 100;
        if (left <= 0) {
            throw new IllegalArgumentException("查询已到5秒截止时间");
        }
        return (int)Math.min(TIMEOUT_MS, left);
    }
}
