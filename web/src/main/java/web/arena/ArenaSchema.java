package web.arena;

import java.sql.SQLException;
import java.sql.Statement;

final class ArenaSchema {
    private ArenaSchema() {}

    static void initialize(Statement statement) throws SQLException {
        statement.execute("CREATE TABLE IF NOT EXISTS arena_player(user_id INTEGER PRIMARY KEY,liquid INTEGER NOT NULL DEFAULT 5000,coins INTEGER NOT NULL DEFAULT 2500,fate INTEGER NOT NULL DEFAULT 12,stones INTEGER NOT NULL DEFAULT 800,pity INTEGER NOT NULL DEFAULT 0,dungeon_cleared INTEGER NOT NULL DEFAULT 0,dungeon_attempts INTEGER NOT NULL DEFAULT 5,formation_level INTEGER NOT NULL DEFAULT 1,grotto_level INTEGER NOT NULL DEFAULT 1,grotto_claim_at INTEGER NOT NULL DEFAULT 0,task_day TEXT NOT NULL DEFAULT '',task_login INTEGER NOT NULL DEFAULT 0,task_dungeon INTEGER NOT NULL DEFAULT 0,task_rank INTEGER NOT NULL DEFAULT 0,task_recruit INTEGER NOT NULL DEFAULT 0,task_arena INTEGER NOT NULL DEFAULT 0,task_skill INTEGER NOT NULL DEFAULT 0,task_formation INTEGER NOT NULL DEFAULT 0,task_grotto INTEGER NOT NULL DEFAULT 0,claimed TEXT NOT NULL DEFAULT '',activity_claimed TEXT NOT NULL DEFAULT '')");
        statement.execute("CREATE TABLE IF NOT EXISTS arena_hero(user_id INTEGER NOT NULL,hero_id TEXT NOT NULL,rank INTEGER NOT NULL DEFAULT 1,stars INTEGER NOT NULL DEFAULT 1,skill_level INTEGER NOT NULL DEFAULT 1,shards INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(user_id,hero_id))");
        statement.execute("CREATE TABLE IF NOT EXISTS arena_draw_log(id INTEGER PRIMARY KEY AUTOINCREMENT,user_id INTEGER NOT NULL,hero_id TEXT NOT NULL,quality TEXT NOT NULL,duplicate INTEGER NOT NULL,created_at INTEGER NOT NULL)");
        addColumn(statement, "arena_player", "task_skill", "INTEGER NOT NULL DEFAULT 0");
        addColumn(statement, "arena_player", "task_formation", "INTEGER NOT NULL DEFAULT 0");
        addColumn(statement, "arena_player", "task_grotto", "INTEGER NOT NULL DEFAULT 0");
        addColumn(statement, "arena_player", "activity_claimed", "TEXT NOT NULL DEFAULT ''");
    }

    private static void addColumn(Statement statement, String table, String column, String type) throws SQLException {
        try {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        } catch (SQLException error) {
            if (!error.getMessage().toLowerCase().contains("duplicate column")) throw error;
        }
    }
}
