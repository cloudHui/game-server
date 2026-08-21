package com.cloud.hub.lobby.db;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertTrue;

public class SqliteBusyTest {
    @Test
    public void writeFailsWhenDatabaseIsLocked() throws Exception {
        Path db = Files.createTempDirectory("hub-busy").resolve("lobby.db");
        String url = "jdbc:sqlite:" + db.toAbsolutePath();
        Class.forName("org.sqlite.JDBC");
        try (Connection locker = DriverManager.getConnection(url);
             Statement lock = locker.createStatement()) {
            lock.execute("BEGIN EXCLUSIVE");
            boolean failed = false;
            try (Connection writer = DriverManager.getConnection(url + "?busy_timeout=50");
                 Statement statement = writer.createStatement()) {
                statement.executeUpdate("CREATE TABLE t(id INTEGER)");
            } catch (SQLException error) {
                failed = error.getMessage() != null && error.getMessage().toLowerCase().contains("lock");
            }
            lock.execute("ROLLBACK");
            assertTrue(failed);
        }
    }
}
