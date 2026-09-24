package com.cloud.hub.web.arena.service;

import com.cloud.hub.game.arena.ArenaRules;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 剑气除魔底层数据持久层 (DAO)。
 * <p>
 * 负责 SQLite 架构建表与自动平滑升级、玩家状态全量快照查询及常用数据库原子更新操作。
 *
 * @author cloud
 */
public class ArenaDao {

    private static final String[] PLAYER_FIELDS = {
            "liquid", "coins", "fate", "stones", "pity", "dungeon_cleared", "dungeon_attempts",
            "formation_level", "grotto_level", "grind_level", "grind_exp", "last_grind_time",
            "beast_level", "beast_shards", "beast_tower_cleared", "beast_tower_attempts",
            "pills_tier1", "pills_tier2", "pills_tier3", "pills_tier4", "dungeon_settled"
    };

    /**
     * 初始化表结构并平滑升级已有数据库列。
     *
     * @param c 数据库连接
     * @throws SQLException 数据库异常
     */
    public void init(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS arena_player(" +
                    "user_id INTEGER PRIMARY KEY," +
                    "liquid INTEGER NOT NULL DEFAULT 5000," +
                    "coins INTEGER NOT NULL DEFAULT 2500," +
                    "fate INTEGER NOT NULL DEFAULT 12," +
                    "stones INTEGER NOT NULL DEFAULT 800," +
                    "pity INTEGER NOT NULL DEFAULT 0," +
                    "dungeon_cleared INTEGER NOT NULL DEFAULT 0," +
                    "dungeon_attempts INTEGER NOT NULL DEFAULT 5," +
                    "formation_level INTEGER NOT NULL DEFAULT 1," +
                    "grotto_level INTEGER NOT NULL DEFAULT 1," +
                    "grotto_claim_at INTEGER NOT NULL DEFAULT 0," +
                    "task_day TEXT NOT NULL DEFAULT ''," +
                    "task_login INTEGER NOT NULL DEFAULT 0," +
                    "task_dungeon INTEGER NOT NULL DEFAULT 0," +
                    "task_rank INTEGER NOT NULL DEFAULT 0," +
                    "task_recruit INTEGER NOT NULL DEFAULT 0," +
                    "task_arena INTEGER NOT NULL DEFAULT 0," +
                    "claimed TEXT NOT NULL DEFAULT ''," +
                    "grind_level INTEGER NOT NULL DEFAULT 1," +
                    "grind_exp INTEGER NOT NULL DEFAULT 0," +
                    "last_grind_time INTEGER NOT NULL DEFAULT 0," +
                    "beast_level INTEGER NOT NULL DEFAULT 1," +
                    "beast_shards INTEGER NOT NULL DEFAULT 0," +
                    "beast_tower_cleared INTEGER NOT NULL DEFAULT 0," +
                    "beast_tower_attempts INTEGER NOT NULL DEFAULT 3," +
                    "equipped_skill TEXT NOT NULL DEFAULT 'pierce'," +
                    "pills_tier1 INTEGER NOT NULL DEFAULT 0," +
                    "pills_tier2 INTEGER NOT NULL DEFAULT 0," +
                    "pills_tier3 INTEGER NOT NULL DEFAULT 0," +
                    "pills_tier4 INTEGER NOT NULL DEFAULT 0," +
                    "dungeon_settled INTEGER NOT NULL DEFAULT 0," +
                    "task_beast INTEGER NOT NULL DEFAULT 0)");

            upgradeColumns(s);

            s.execute("CREATE TABLE IF NOT EXISTS arena_hero(" +
                    "user_id INTEGER NOT NULL," +
                    "hero_id TEXT NOT NULL," +
                    "rank INTEGER NOT NULL DEFAULT 1," +
                    "stars INTEGER NOT NULL DEFAULT 1," +
                    "skill_level INTEGER NOT NULL DEFAULT 1," +
                    "shards INTEGER NOT NULL DEFAULT 0," +
                    "PRIMARY KEY(user_id,hero_id))");

            s.execute("CREATE TABLE IF NOT EXISTS arena_draw_log(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "user_id INTEGER NOT NULL," +
                    "hero_id TEXT NOT NULL," +
                    "quality TEXT NOT NULL," +
                    "duplicate INTEGER NOT NULL," +
                    "created_at INTEGER NOT NULL)");
        }
    }

    private void upgradeColumns(Statement s) {
        String[] newCols = {
                "grind_level INTEGER NOT NULL DEFAULT 1",
                "grind_exp INTEGER NOT NULL DEFAULT 0",
                "last_grind_time INTEGER NOT NULL DEFAULT 0",
                "beast_level INTEGER NOT NULL DEFAULT 1",
                "beast_shards INTEGER NOT NULL DEFAULT 0",
                "beast_tower_cleared INTEGER NOT NULL DEFAULT 0",
                "beast_tower_attempts INTEGER NOT NULL DEFAULT 3",
                "equipped_skill TEXT NOT NULL DEFAULT 'pierce'",
                "pills_tier1 INTEGER NOT NULL DEFAULT 0",
                "pills_tier2 INTEGER NOT NULL DEFAULT 0",
                "pills_tier3 INTEGER NOT NULL DEFAULT 0",
                "pills_tier4 INTEGER NOT NULL DEFAULT 0",
                "dungeon_settled INTEGER NOT NULL DEFAULT 0",
                "task_beast INTEGER NOT NULL DEFAULT 0"
        };
        for (String colDef : newCols) {
            try {
                s.execute("ALTER TABLE arena_player ADD COLUMN " + colDef);
            } catch (SQLException ignored) {
                // 已存在跳过
            }
        }
    }

    /**
     * 确保玩家账号与初始本命仙侣存在。
     *
     * @param c   数据库连接
     * @param uid 玩家唯一标识
     */
    public void ensure(Connection c, long uid) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO arena_player(user_id, last_grind_time) VALUES(?,?)")) {
            p.setLong(1, uid);
            p.setLong(2, System.currentTimeMillis());
            p.executeUpdate();
        }
        try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO arena_hero(user_id,hero_id) VALUES(?,?)")) {
            for (String h : new String[]{"jianhuang", "leizun"}) {
                p.setLong(1, uid);
                p.setString(2, h);
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    /**
     * 跨天自动重置每日次数与任务进度。
     *
     * @param c   数据库连接
     * @param uid 玩家唯一标识
     */
    public void refresh(Connection c, long uid) throws SQLException {
        String today = LocalDate.now().toString();
        String sql = "UPDATE arena_player SET task_day=?, dungeon_attempts=5, beast_tower_attempts=3, " +
                "task_login=1, task_dungeon=0, task_rank=0, task_recruit=0, task_arena=0, task_beast=0, " +
                "dungeon_settled=0, claimed='' WHERE user_id=? AND task_day<>?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, today);
            p.setLong(2, uid);
            p.setString(3, today);
            p.executeUpdate();
        }
    }

    /**
     * 扣减玩家指定资源字段数值。
     *
     * @param c      数据库连接
     * @param uid    玩家唯一标识
     * @param col    资源字段列名 ("liquid", "coins", "fate", "stones")
     * @param amount 扣减数量
     */
    public void spend(Connection c, long uid, String col, long amount) throws SQLException {
        String sql = "UPDATE arena_player SET " + col + "=" + col + "-? WHERE user_id=? AND " + col + ">=?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, amount);
            p.setLong(2, uid);
            p.setLong(3, amount);
            if (p.executeUpdate() != 1) {
                throw new IllegalArgumentException("资源不足，扣减失败: " + col);
            }
        }
    }

    /**
     * 累加玩家任务或业务计数进度。
     *
     * @param c     数据库连接
     * @param uid   玩家唯一标识
     * @param col   累加列名
     * @param count 累加增量
     */
    public void progress(Connection c, long uid, String col, int count) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE arena_player SET " + col + "=" + col + "+? WHERE user_id=?")) {
            p.setInt(1, count);
            p.setLong(2, uid);
            p.executeUpdate();
        }
    }

    /**
     * 查询玩家是否已拥有某位仙修。
     */
    public boolean hasHero(Connection c, long uid, String heroId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT 1 FROM arena_hero WHERE user_id=? AND hero_id=?")) {
            p.setLong(1, uid);
            p.setString(2, heroId);
            try (ResultSet r = p.executeQuery()) {
                return r.next();
            }
        }
    }

    /**
     * 增加仙修碎片数量。
     */
    public void addHeroShards(Connection c, long uid, String heroId, int shards) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE arena_hero SET shards=shards+? WHERE user_id=? AND hero_id=?")) {
            p.setInt(1, shards);
            p.setLong(2, uid);
            p.setString(3, heroId);
            p.executeUpdate();
        }
    }

    /**
     * 解锁新仙修。
     */
    public void addHero(Connection c, long uid, String heroId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("INSERT INTO arena_hero(user_id,hero_id) VALUES(?,?)")) {
            p.setLong(1, uid);
            p.setString(2, heroId);
            p.executeUpdate();
        }
    }

    /**
     * 写入抽卡日志流水。
     */
    public void logDraw(Connection c, long uid, String heroId, String quality, boolean dup) throws SQLException {
        String sql = "INSERT INTO arena_draw_log(user_id,hero_id,quality,duplicate,created_at) VALUES(?,?,?,?,?)";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, uid);
            p.setString(2, heroId);
            p.setString(3, quality);
            p.setInt(4, dup ? 1 : 0);
            p.setLong(5, System.currentTimeMillis());
            p.executeUpdate();
        }
    }

    /**
     * 读取玩家全量状态快照 (属性、神兽、挂机、丹药、仙修列表及任务)。
     *
     * @param c   数据库连接
     * @param uid 玩家唯一标识
     * @return 状态映射表
     */
    public Map<String, Object> read(Connection c, long uid) throws SQLException {
        Map<String, Object> out = new LinkedHashMap<>();
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM arena_player WHERE user_id=?")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    readPlayerProperties(r, out);
                    readTasks(r, out);
                }
            }
        }
        out.put("heroes", readHeroes(c, uid));
        return out;
    }

    private void readPlayerProperties(ResultSet r, Map<String, Object> out) throws SQLException {
        for (String k : PLAYER_FIELDS) {
            out.put(toCamel(k), r.getLong(k));
        }
        out.put("equippedSkill", r.getString("equipped_skill"));

        int bLv = r.getInt("beast_level");
        out.put("beastFormationAmp", ArenaRules.beastFormationMultiplier(bLv));
        out.put("beastShardCost", ArenaRules.beastShardCost(bLv));

        int gLv = r.getInt("grind_level");
        out.put("grindExpNeeded", ArenaRules.grindExpForLevel(gLv));

        int dCleared = r.getInt("dungeon_cleared");
        out.put("grindRates", ArenaRules.grindRates(dCleared));
        out.put("dungeonSettlement", ArenaRules.dungeonDailyReward(dCleared));

        int pTotal = r.getInt("pills_tier1") + r.getInt("pills_tier2") + r.getInt("pills_tier3") + r.getInt("pills_tier4");
        out.put("pillsTotal", pTotal);
    }

    private void readTasks(ResultSet r, Map<String, Object> out) throws SQLException {
        String claimed = r.getString("claimed");
        Map<String, Object> tasks = new LinkedHashMap<>();
        for (String id : new String[]{"login", "dungeon", "rank", "recruit", "arena", "beast"}) {
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("progress", r.getInt("task_" + id));
            t.put("claimed", Arrays.asList(claimed.split(",")).contains(id));
            tasks.put(id, t);
        }
        out.put("tasks", tasks);
    }

    private List<Map<String, Object>> readHeroes(Connection c, long uid) throws SQLException {
        List<Map<String, Object>> heroes = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM arena_hero WHERE user_id=? ORDER BY hero_id")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    Map<String, Object> h = new LinkedHashMap<>();
                    h.put("id", r.getString("hero_id"));
                    h.put("rank", r.getInt("rank"));
                    h.put("stars", r.getInt("stars"));
                    h.put("skill", r.getInt("skill_level"));
                    h.put("shards", r.getInt("shards"));
                    heroes.add(h);
                }
            }
        }
        return heroes;
    }

    private static String toCamel(String s) {
        StringBuilder b = new StringBuilder();
        boolean up = false;
        for (char ch : s.toCharArray()) {
            if (ch == '_') {
                up = true;
            } else {
                b.append(up ? Character.toUpperCase(ch) : ch);
                up = false;
            }
        }
        return b.toString();
    }
}
