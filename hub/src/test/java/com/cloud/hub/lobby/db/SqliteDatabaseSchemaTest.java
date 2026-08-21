package com.cloud.hub.lobby.db;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertTrue;

public class SqliteDatabaseSchemaTest {
    @Test
    public void initializeCreatesCustomRoomTable() throws Exception {
        Path db = Files.createTempDirectory("hub-schema").resolve("lobby.db");
        SqliteDatabase.initialize(db.toString());
        boolean found = false;
        try (Connection connection = SqliteDatabase.getInstance().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' AND name='custom_room'")) {
            found = rows.next();
        }
        assertTrue(found);
    }
}
