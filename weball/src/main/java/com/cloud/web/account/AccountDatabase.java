package com.cloud.web.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 与 Lobby 共用 data/lobby.db（账号/邀请码）。Web 主鉴权，Lobby 按需默登。
 */
@Component
public class AccountDatabase {
    private static final Logger logger = LoggerFactory.getLogger(AccountDatabase.class);

    @Value("${account.db-path:data/lobby.db}")
    private String dbPath;

    private String jdbcUrl;

    @PostConstruct
    public void init() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("sqlite-jdbc 未找到", e);
        }
        File dbFile = new File(dbPath);
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建数据库目录: " + parent.getAbsolutePath());
        }
        jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        initSchema();
        logger.info("账号库: {}", dbFile.getAbsolutePath());
    }

    public Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement st = conn.createStatement()) {
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("PRAGMA journal_mode=WAL");
        }
        return conn;
    }

    private void initSchema() {
        String userSql = "CREATE TABLE IF NOT EXISTS user ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT NOT NULL UNIQUE,"
                + "nickname TEXT NOT NULL,"
                + "password_hash TEXT NOT NULL,"
                + "enabled INTEGER NOT NULL DEFAULT 1,"
                + "token TEXT,"
                + "created_at INTEGER NOT NULL,"
                + "last_login_at INTEGER"
                + ")";
        String inviteSql = "CREATE TABLE IF NOT EXISTS invite ("
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
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement st = conn.createStatement()) {
            st.execute(userSql);
            st.execute(inviteSql);
        } catch (SQLException e) {
            throw new IllegalStateException("初始化账号表失败", e);
        }
    }
}
