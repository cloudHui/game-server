package com.gamer.data.file.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gamer.data.file.db.BlobParseRule;
import com.gamer.data.file.db.ConnectionConfig;

/**
 * WindowsTools 与 MCP 共用的数据库配置（连接 / BLOB 规则 / 解析 jar）。改配置改本类后重新编译。
 */
public final class DbConfig {

    /**
     * 数据库 Tab 懒加载用 common-proto.jar（解析 BLOB / PB 消息）。
     */
    public static final String PROTO_JAR = PathConfig.COMMON_DIR + "\\lib\\common-proto.jar";

    /**
     * protobuf-java 运行时 jar（与 common-proto 配套加载）。
     */
    public static final String PROTOBUF_JAVA_JAR = PathConfig.COMMON_DIR + "\\lib\\protobuf-java-3.14.0.jar";

    /**
     * MySQL JDBC 驱动 jar（数据库 Tab 连库用）。
     */
    public static final String MYSQL_JAR = PathConfig.SERVERTOOL_DIR + "\\lib\\mysql-connector-java-8.0.22.jar";

    /** 查表默认每页行数。 */
    public static final int ROW_LIMIT = 50;

    /** 本地 kingdom_game 连接别名（MCP target=local）。 */
    public static final String ALIAS_LOCAL_GAME = "本地game";

    /** 测试服 kingdom_game 连接别名（MCP target=test）。 */
    public static final String ALIAS_TEST_GAME = "测试服game";

    /** 测试服 passport 连接别名。 */
    public static final String ALIAS_TEST_PASS = "测试pass";

    /**
     * 数据库连接。
     * 每行格式：别名|host|port|user|password|database。
     */
    public static final String[] CONNECTIONS = {
        ALIAS_LOCAL_GAME + "|127.0.0.1|3306|gow|gow|kingdom_game",
        ALIAS_TEST_GAME + "|10.3.115.63|3306|gow|Gow#0123|kingdom_game",
        ALIAS_TEST_PASS + "|10.3.115.63|3306|gow|Gow#0123|kingdom_passport",
    };

    /**
     * BLOB 列 → Protobuf 消息名。每行：{ 表名, 列名, PBxxx }。
     */
    public static final String[][] BLOB_RULES = {
        {"role_level_info", "islandShop", "PBLevelIslandShopPersist"},
        {"role_level_info", "focus", "PBTask"},
        {"role_level_info", "levelCrashBuilds", "PBLevelCrashBuildsPersist"},
    };

    /** 解析后的连接，启动时建一次。 */
    private static final List<ConnectionConfig> CONNECTION_LIST = buildConnections();

    /** 解析后的 BLOB 规则，启动时建一次。 */
    private static final List<BlobParseRule> BLOB_RULE_LIST = buildBlobRules();

    private DbConfig() {}

    /**
     * 解析连接行：alias|host|port|user|password|database。
     *
     * @param body
     *            连接串
     * @return 连接配置，格式错误时 null
     */
    private static ConnectionConfig parseConnection(String body) {
        if (body == null) {
            return null;
        }
        String[] parts = body.split("\\|", 6);
        if (parts.length < 6) {
            return null;
        }
        ConnectionConfig conn = new ConnectionConfig();
        conn.setAlias(parts[0].trim());
        conn.setHost(parts[1].trim());
        try {
            conn.setPort(Integer.parseInt(parts[2].trim()));
        } catch (NumberFormatException e) {
            conn.setPort(3306);
        }
        conn.setUser(parts[3].trim());
        conn.setPassword(parts[4]);
        conn.setDatabase(parts[5].trim());
        if (conn.getAlias().isEmpty() || conn.getHost().isEmpty() || conn.getDatabase().isEmpty()) {
            return null;
        }
        return conn;
    }

    /**
     * 按别名从 {@link #CONNECTIONS} 查找连接。
     *
     * @param alias
     *            连接别名
     * @return 连接；无则 null
     */
    public static ConnectionConfig findConnection(String alias) {
        if (alias == null) {
            return null;
        }
        for (ConnectionConfig conn : CONNECTION_LIST) {
            if (alias.equals(conn.getAlias())) {
                return conn;
            }
        }
        return null;
    }

    /**
     * @return {@link #CONNECTIONS} 解析后的只读列表（跳过格式错误行）
     */
    public static List<ConnectionConfig> connections() {
        return CONNECTION_LIST;
    }

    /**
     * 按表.列查找 BLOB 规则。
     *
     * @param table
     *            表名
     * @param column
     *            列名
     * @return 规则；无则 null
     */
    public static BlobParseRule findBlobRule(String table, String column) {
        if (table == null || column == null) {
            return null;
        }
        String tableName = table.trim();
        String columnName = column.trim();
        for (BlobParseRule rule : BLOB_RULE_LIST) {
            if (tableName.equals(rule.getTable()) && columnName.equals(rule.getColumn())) {
                return rule;
            }
        }
        return null;
    }

    /**
     * @return {@link #BLOB_RULES} 解析后的只读列表（跳过不完整行）
     */
    public static List<BlobParseRule> blobRules() {
        return BLOB_RULE_LIST;
    }

    /**
     * 解析全部连接行。
     *
     * @return 只读列表
     */
    private static List<ConnectionConfig> buildConnections() {
        List<ConnectionConfig> list = new ArrayList<>();
        for (String line : CONNECTIONS) {
            ConnectionConfig conn = parseConnection(line);
            if (conn != null) {
                list.add(conn);
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * 解析全部 BLOB 规则行。
     *
     * @return 只读列表
     */
    private static List<BlobParseRule> buildBlobRules() {
        List<BlobParseRule> list = new ArrayList<>();
        for (String[] row : BLOB_RULES) {
            BlobParseRule rule = parseBlobRule(row);
            if (rule != null) {
                list.add(rule);
            }
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * 解析 BLOB 规则行：表、列、proto 消息名。
     *
     * @param row
     *            规则行
     * @return 规则；不完整时 null
     */
    private static BlobParseRule parseBlobRule(String[] row) {
        if (row == null || row.length < 3) {
            return null;
        }
        String table = row[0] == null ? "" : row[0].trim();
        String column = row[1] == null ? "" : row[1].trim();
        String protoMsg = row[2] == null ? "" : row[2].trim();
        if (table.isEmpty() || column.isEmpty() || protoMsg.isEmpty()) {
            return null;
        }
        return new BlobParseRule(table, column, protoMsg);
    }
}
