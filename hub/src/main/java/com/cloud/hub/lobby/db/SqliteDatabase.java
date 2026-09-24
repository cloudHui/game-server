package com.cloud.hub.lobby.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Lobby 大厅 SQLite 独立文件数据库连接与 Schema 初始化中心。
 * <p>
 * 负责在服务引导时加载 SQLite JDBC 驱动、按需自动创建存储目录文件、
 * 提供底层 JDBC 连接池获取，并保证必要的数据表结构自检建表。
 * </p>
 *
 * @author cloud
 */
public class SqliteDatabase {

    private static final Logger logger = LoggerFactory.getLogger(SqliteDatabase.class);
    private static SqliteDatabase instance;

    /** SQLite JDBC 连接字符串 */
    private final String jdbcUrl;

    /**
     * 私有构造函数，完成目录校验与 JDBC URL 装配。
     *
     * @param path 数据库文件绝对/相对路径
     */
    SqliteDatabase(String path) {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("sqlite-jdbc 未找到", e);
        }
        File dbFile = new File(path);
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new RuntimeException("无法创建数据库目录: " + parent.getAbsolutePath());
        }
        this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        logger.info("SQLite 数据库路径: {}", dbFile.getAbsolutePath());
    }

    /**
     * 获取单例实例。
     *
     * @return 数据库实例
     */
    public static synchronized SqliteDatabase getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Hub SQLite 尚未由统一配置初始化");
        }
        return instance;
    }

    /**
     * 初始化单例与数据表结构。
     *
     * @param path 数据库文件路径
     */
    public static synchronized void initialize(String path) {
        if (instance == null) {
            instance = new SqliteDatabase(path);
        }
        instance.initSchema();
    }

    /**
     * 获取原生 JDBC 数据库连接。
     *
     * @return 数据库 Connection
     * @throws SQLException 获取连接失败抛出
     */
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    /**
     * 初始化数据库基础 Schema 表结构。
     */
    public void initSchema() {
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.execute(getUserTableSql());
            st.execute(getInviteTableSql());
            st.execute(getCustomRoomTableSql());
            st.execute(getScoreTableSql());
            logger.info("SQLite 表结构初始化完成");
        } catch (SQLException e) {
            throw new RuntimeException("初始化 SQLite 表失败", e);
        }
    }

    private static String getUserTableSql() {
        return "CREATE TABLE IF NOT EXISTS user ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT NOT NULL UNIQUE,"
                + "nickname TEXT NOT NULL,"
                + "password_hash TEXT NOT NULL,"
                + "enabled INTEGER NOT NULL DEFAULT 1,"
                + "token TEXT,"
                + "created_at INTEGER NOT NULL,"
                + "last_login_at INTEGER"
                + ")";
    }

    private static String getInviteTableSql() {
        return "CREATE TABLE IF NOT EXISTS invite ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "token TEXT NOT NULL UNIQUE,"
                + "note TEXT,"
                + "created_by TEXT,"
                + "created_at INTEGER NOT NULL,"
                + "expires_at INTEGER,"
                + "max_uses INTEGER NOT NULL DEFAULT 1,"
                + "used_count INTEGER NOT NULL DEFAULT 0,"
                + "enabled INTEGER NOT NULL DEFAULT 1"
                + ")";
    }

    private static String getCustomRoomTableSql() {
        return "CREATE TABLE IF NOT EXISTS custom_room ("
                + "model_id INTEGER PRIMARY KEY, model_json TEXT NOT NULL, game_type INTEGER NOT NULL,"
                + "created_by TEXT, created_at INTEGER NOT NULL, enabled INTEGER NOT NULL DEFAULT 1"
                + ")";
    }

    private static String getScoreTableSql() {
        return "CREATE TABLE IF NOT EXISTS score_record ("
                + "table_id INTEGER NOT NULL, room_id INTEGER NOT NULL, game_type INTEGER NOT NULL,"
                + "round INTEGER NOT NULL, user_id INTEGER NOT NULL, seat INTEGER NOT NULL,"
                + "score INTEGER NOT NULL, total_score INTEGER NOT NULL, winner_seat INTEGER NOT NULL,"
                + "score_value INTEGER NOT NULL, win_type TEXT NOT NULL, created_at INTEGER NOT NULL,"
                + "PRIMARY KEY(table_id, round, user_id)"
                + ")";
    }
}

