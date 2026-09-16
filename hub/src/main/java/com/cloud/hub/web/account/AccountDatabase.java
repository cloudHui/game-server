package com.cloud.hub.web.account;

import com.cloud.hub.storage.DataPathResolver;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 与 Lobby 共用 data/lobby.db（账号/邀请码）。
 * 采用 HikariCP 连接池优化 SQLite 读写吞吐与连接复用（参照 RuoYi 数据源配置规范）。
 */
@Component
public class AccountDatabase {
    private static final Logger logger = LoggerFactory.getLogger(AccountDatabase.class);

    @Value("${account.db-path:data/lobby.db}")
    private String dbPath;

    private String jdbcUrl;
    private HikariDataSource dataSource;
    private final DataPathResolver paths;

    public AccountDatabase(DataPathResolver paths) {
        this.paths = paths;
    }

    @PostConstruct
    public void init() {
        File dbFile = paths.resolve(dbPath).toFile();
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建数据库目录: " + parent.getAbsolutePath());
        }
        jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        initDataSource();
        initSchema();
        logger.info("账号库 HikariCP 连接池初始化完成: {}", dbFile.getAbsolutePath());
    }

    private void initDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setIdleTimeout(60000);
        config.setConnectionInitSql("PRAGMA busy_timeout=5000; PRAGMA journal_mode=WAL;");
        config.setPoolName("Hub-Sqlite-Pool");
        this.dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("数据源尚未初始化");
        }
        return dataSource.getConnection();
    }

    @PreDestroy
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("账号库连接池已关闭");
        }
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
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(userSql);
            st.execute(inviteSql);
        } catch (SQLException e) {
            throw new IllegalStateException("初始化账号表失败", e);
        }
    }
}
