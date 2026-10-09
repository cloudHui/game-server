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
            "pills_tier1", "pills_tier2", "pills_tier3", "pills_tier4", "dungeon_settled",
            "tribulation_level", "void_cleared"
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

            s.execute("CREATE TABLE IF NOT EXISTS arena_gear(" +
                    "user_id INTEGER NOT NULL," +
                    "slot TEXT NOT NULL," +
                    "gear_id TEXT NOT NULL," +
                    "level INTEGER NOT NULL DEFAULT 0," +
                    "PRIMARY KEY(user_id, slot))");

            s.execute("CREATE TABLE IF NOT EXISTS arena_item(" +
                    "user_id INTEGER NOT NULL," +
                    "item_id TEXT NOT NULL," +
                    "quantity INTEGER NOT NULL DEFAULT 0," +
                    "PRIMARY KEY(user_id, item_id))");

            s.execute("CREATE TABLE IF NOT EXISTS arena_field(" +
                    "user_id INTEGER NOT NULL," +
                    "field_id INTEGER NOT NULL," +
                    "seed_id TEXT NOT NULL DEFAULT ''," +
                    "plant_time INTEGER NOT NULL DEFAULT 0," +
                    "PRIMARY KEY(user_id, field_id))");
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
                "task_beast INTEGER NOT NULL DEFAULT 0",
                "tribulation_level INTEGER NOT NULL DEFAULT 0",
                "void_cleared INTEGER NOT NULL DEFAULT 0",
                "void_buffs TEXT NOT NULL DEFAULT ''"
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
        // 初始化四大装备槽位
        String[][] defaultGears = {
                {"weapon", "诛仙断界剑"},
                {"armor", "玄武神甲"},
                {"helmet", "天机星冠"},
                {"talisman", "混元金斗"}
        };
        try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO arena_gear(user_id, slot, gear_id, level) VALUES(?,?,?,0)")) {
            for (String[] g : defaultGears) {
                p.setLong(1, uid);
                p.setString(2, g[0]);
                p.setString(3, g[1]);
                p.addBatch();
            }
            p.executeBatch();
        }

        // 初始化基础材料与丹药
        String[][] defaultItems = {
                {"herb", "20"},
                {"ore", "20"},
                {"refine_stone", "10"},
                {"pill", "10"},
                {"star_pill", "5"}
        };
        try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO arena_item(user_id, item_id, quantity) VALUES(?,?,?)")) {
            for (String[] it : defaultItems) {
                p.setLong(1, uid);
                p.setString(2, it[0]);
                p.setInt(3, Integer.parseInt(it[1]));
                p.addBatch();
            }
            p.executeBatch();
        }

        // 初始化 3 块洞府灵田
        try (PreparedStatement p = c.prepareStatement("INSERT OR IGNORE INTO arena_field(user_id, field_id, seed_id, plant_time) VALUES(?,?, '', 0)")) {
            for (int fid = 1; fid <= 3; fid++) {
                p.setLong(1, uid);
                p.setInt(2, fid);
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
     * 奖励发放玩家基础资源 (灵液、灵币、灵石等)。
     */
    public void reward(Connection c, long uid, String col, long amount) throws SQLException {
        String sql = "UPDATE arena_player SET " + col + "=" + col + "+? WHERE user_id=?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, amount);
            p.setLong(2, uid);
            p.executeUpdate();
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
    /**
     * 读取玩家全量状态快照 (属性、神兽、挂机、丹药、仙修列表、任务、神装及背包材料)。
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
        out.put("gears", readGears(c, uid));
        out.put("items", readItems(c, uid));
        out.put("fields", readFields(c, uid, System.currentTimeMillis()));
        return out;
    }

    /**
     * 神装精炼打造：消耗灵币和精炼石 (refine_stone)。
     *
     * @param c    数据库连接
     * @param uid  玩家唯一标识
     * @param slot 装备槽位 (weapon / armor / helmet / talisman)
     */
    public void refineGear(Connection c, long uid, String slot) throws SQLException {
        int level = 0;
        String gearId = "";
        try (PreparedStatement p = c.prepareStatement("SELECT gear_id, level FROM arena_gear WHERE user_id=? AND slot=?")) {
            p.setLong(1, uid);
            p.setString(2, slot);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) {
                    throw new IllegalArgumentException("未找到该部位装备");
                }
                gearId = r.getString(1);
                level = r.getInt(2);
            }
        }
        if (level >= 15) {
            throw new IllegalArgumentException("该装备已达到最高精炼强化等级 (15级)");
        }
        int coinCost = (level + 1) * 300;
        int stoneCost = Math.max(1, (level + 1) / 2);

        spend(c, uid, "coins", coinCost);
        // 扣除背包中的 refine_stone
        String deductItemSql = "UPDATE arena_item SET quantity=quantity-? WHERE user_id=? AND item_id='refine_stone' AND quantity>=?";
        try (PreparedStatement p = c.prepareStatement(deductItemSql)) {
            p.setInt(1, stoneCost);
            p.setLong(2, uid);
            p.setInt(3, stoneCost);
            if (p.executeUpdate() != 1) {
                throw new IllegalArgumentException("精炼石不足，还需 " + stoneCost + " 块精炼石 (可前往无尽书阁炼制)");
            }
        }

        try (PreparedStatement p = c.prepareStatement("UPDATE arena_gear SET level=level+1 WHERE user_id=? AND slot=?")) {
            p.setLong(1, uid);
            p.setString(2, slot);
            p.executeUpdate();
        }
    }

    /**
     * 洞府渡劫天劫试炼：突破大境界。
     *
     * @param c   数据库连接
     * @param uid 玩家唯一标识
     */
    public void tribulate(Connection c, long uid) throws SQLException {
        int currentLevel = 0;
        try (PreparedStatement p = c.prepareStatement("SELECT tribulation_level FROM arena_player WHERE user_id=?")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    currentLevel = r.getInt(1);
                }
            }
        }
        if (currentLevel >= 6) {
            throw new IllegalArgumentException("已臻飞升真仙之境，功德圆满");
        }

        // 校验全队仙修最高境界
        int maxRank = 0;
        try (PreparedStatement p = c.prepareStatement("SELECT max(rank) FROM arena_hero WHERE user_id=?")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    maxRank = r.getInt(1);
                }
            }
        }
        int reqRank = (currentLevel + 1) * 4;
        if (maxRank < reqRank) {
            throw new IllegalArgumentException("主修仙侣境界未稳，需达到 " + reqRank + " 阶方可引动九天雷劫");
        }

        int liquidCost = (currentLevel + 1) * 2500;
        spend(c, uid, "liquid", liquidCost);

        // 高阶渡劫需要消耗背包中的炼气丹
        if (currentLevel >= 1) {
            int pillCost = currentLevel;
            String deductPillSql = "UPDATE arena_item SET quantity=quantity-? WHERE user_id=? AND item_id='pill' AND quantity>=?";
            try (PreparedStatement p = c.prepareStatement(deductPillSql)) {
                p.setInt(1, pillCost);
                p.setLong(2, uid);
                p.setInt(3, pillCost);
                if (p.executeUpdate() != 1) {
                    throw new IllegalArgumentException("缺少聚元灵丹护体，还需 " + pillCost + " 颗炼气丹抵御雷劫 (可前往书阁炼制)");
                }
            }
        }

        try (PreparedStatement p = c.prepareStatement("UPDATE arena_player SET tribulation_level=tribulation_level+1 WHERE user_id=?")) {
            p.setLong(1, uid);
            p.executeUpdate();
        }
    }

    private Map<String, Object> readGears(Connection c, long uid) throws SQLException {
        Map<String, Object> gears = new LinkedHashMap<>();
        try (PreparedStatement p = c.prepareStatement("SELECT slot, gear_id, level FROM arena_gear WHERE user_id=?")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    String slot = r.getString("slot");
                    Map<String, Object> g = new LinkedHashMap<>();
                    g.put("slot", slot);
                    g.put("name", r.getString("gear_id"));
                    int lvl = r.getInt("level");
                    g.put("level", lvl);
                    // 计算装备属性加成
                    fillGearStats(slot, lvl, g);
                    gears.put(slot, g);
                }
            }
        }
        return gears;
    }

    private void fillGearStats(String slot, int lvl, Map<String, Object> g) {
        if ("weapon".equals(slot)) {
            g.put("atk", 120 + lvl * 65);
            g.put("crit", (lvl / 3) * 1.5);
            g.put("desc", "攻击 +" + (120 + lvl * 65) + " · 暴击 +" + ((lvl / 3) * 1.5) + "%");
        } else if ("armor".equals(slot)) {
            g.put("hp", 1200 + lvl * 450);
            g.put("def", 60 + lvl * 25);
            g.put("desc", "气血 +" + (1200 + lvl * 450) + " · 防御 +" + (60 + lvl * 25));
        } else if ("helmet".equals(slot)) {
            g.put("hp", 800 + lvl * 300);
            g.put("reduction", 2.0 + lvl * 0.5);
            g.put("desc", "气血 +" + (800 + lvl * 300) + " · 免伤 +" + (2.0 + lvl * 0.5) + "%");
        } else if ("talisman".equals(slot)) {
            g.put("atk", 80 + lvl * 40);
            g.put("dodge", 3.0 + lvl * 0.6);
            g.put("desc", "攻击 +" + (80 + lvl * 40) + " · 闪避 +" + (3.0 + lvl * 0.6) + "%");
        }
    }

    private List<Map<String, Object>> readItems(Connection c, long uid) throws SQLException {
        List<Map<String, Object>> items = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT item_id, quantity FROM arena_item WHERE user_id=? AND quantity>0")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", r.getString(1));
                    item.put("quantity", r.getInt(2));
                    items.add(item);
                }
            }
        }
        return items;
    }

    private void readPlayerProperties(ResultSet r, Map<String, Object> out) throws SQLException {
        for (String k : PLAYER_FIELDS) {
            out.put(toCamel(k), r.getLong(k));
        }
        out.put("equippedSkill", r.getString("equipped_skill"));
        out.put("voidBuffs", r.getString("void_buffs") != null ? r.getString("void_buffs") : "");

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

    /**
     * 读取洞府 3 块灵田种植生长状态。
     */
    public List<Map<String, Object>> readFields(Connection c, long uid, long now) throws SQLException {
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("SELECT field_id, seed_id, plant_time FROM arena_field WHERE user_id=? ORDER BY field_id")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    Map<String, Object> f = new LinkedHashMap<>();
                    int fid = r.getInt(1);
                    String seed = r.getString(2);
                    long pt = r.getLong(3);
                    f.put("fieldId", fid);
                    f.put("seedId", seed);
                    f.put("plantTime", pt);
                    int matureSec = seedDuration(seed);
                    long elapsedSec = pt > 0 ? (now - pt) / 1000 : 0;
                    long remainSec = Math.max(0, matureSec - elapsedSec);
                    f.put("remainingSeconds", remainSec);
                    f.put("isMature", pt > 0 && remainSec == 0);
                    f.put("name", fieldName(fid));
                    f.put("cropName", cropName(seed));
                    list.add(f);
                }
            }
        }
        return list;
    }

    /**
     * 洞府灵田播种。
     */
    public void plantField(Connection c, long uid, int fieldId, String seedId, long now) throws SQLException {
        if (fieldId < 1 || fieldId > 3) throw new IllegalArgumentException("灵田编号无效");
        if (!"herb".equals(seedId) && !"ore".equals(seedId) && !"lotus".equals(seedId)) {
            throw new IllegalArgumentException("种子品类无效");
        }
        // 校验灵田是否为空
        try (PreparedStatement p = c.prepareStatement("SELECT seed_id FROM arena_field WHERE user_id=? AND field_id=?")) {
            p.setLong(1, uid);
            p.setInt(2, fieldId);
            try (ResultSet r = p.executeQuery()) {
                if (r.next() && !r.getString(1).isEmpty()) {
                    throw new IllegalArgumentException("该灵田已有灵植在生长中");
                }
            }
        }
        try (PreparedStatement p = c.prepareStatement("INSERT OR REPLACE INTO arena_field(user_id, field_id, seed_id, plant_time) VALUES(?,?,?,?)")) {
            p.setLong(1, uid);
            p.setInt(2, fieldId);
            p.setString(3, seedId);
            p.setLong(4, now);
            p.executeUpdate();
        }
    }

    /**
     * 洞府灵田灵植成熟收获。
     */
    public void harvestField(Connection c, long uid, int fieldId, long now) throws SQLException {
        String seedId = "";
        long plantTime = 0;
        try (PreparedStatement p = c.prepareStatement("SELECT seed_id, plant_time FROM arena_field WHERE user_id=? AND field_id=?")) {
            p.setLong(1, uid);
            p.setInt(2, fieldId);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) {
                    seedId = r.getString(1);
                    plantTime = r.getLong(2);
                }
            }
        }
        if (seedId == null || seedId.isEmpty()) {
            throw new IllegalArgumentException("该灵田尚未播种");
        }
        int matureSec = seedDuration(seedId);
        if ((now - plantTime) / 1000 < matureSec) {
            throw new IllegalArgumentException("灵植尚未成熟，还需培元生长");
        }

        // 发放收获奖励
        if ("herb".equals(seedId)) {
            addItem(c, uid, "herb", 3);
            reward(c, uid, "liquid", 200);
        } else if ("ore".equals(seedId)) {
            addItem(c, uid, "ore", 3);
            reward(c, uid, "liquid", 500);
        } else if ("lotus".equals(seedId)) {
            addItem(c, uid, "refine_stone", 2);
            addItem(c, uid, "pill", 1);
        }

        // 清空灵田槽位
        try (PreparedStatement p = c.prepareStatement("UPDATE arena_field SET seed_id='', plant_time=0 WHERE user_id=? AND field_id=?")) {
            p.setLong(1, uid);
            p.setInt(2, fieldId);
            p.executeUpdate();
        }
    }

    /**
     * 增加背包物品数量（具备 upsert 幂等支持）。
     */
    public void addItem(Connection c, long uid, String itemId, int count) throws SQLException {
        String sql = "INSERT INTO arena_item(user_id, item_id, quantity) VALUES(?,?,?) " +
                "ON CONFLICT(user_id, item_id) DO UPDATE SET quantity=quantity+?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, uid);
            p.setString(2, itemId);
            p.setInt(3, count);
            p.setInt(4, count);
            p.executeUpdate();
        }
    }

    /**
     * 虚空秘境（通天塔肉鸽模式）逐层闯关。
     */
    public void voidClimb(Connection c, long uid) throws SQLException {
        int cleared = 0;
        try (PreparedStatement p = c.prepareStatement("SELECT void_cleared FROM arena_player WHERE user_id=?")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                if (r.next()) cleared = r.getInt(1);
            }
        }
        if (cleared >= 100) {
            throw new IllegalArgumentException("已臻虚空幻境第 100 层巅峰！");
        }
        int nextFloor = cleared + 1;
        reward(c, uid, "liquid", nextFloor * 150L);
        reward(c, uid, "stones", 20L);
        try (PreparedStatement p = c.prepareStatement("UPDATE arena_player SET void_cleared=void_cleared+1 WHERE user_id=?")) {
            p.setLong(1, uid);
            p.executeUpdate();
        }
    }

    /**
     * 虚空秘境选择气运 Buff 加持。
     */
    public void voidChooseBuff(Connection c, long uid, String buffId) throws SQLException {
        if (buffId == null || buffId.trim().isEmpty()) return;
        String cur = "";
        try (PreparedStatement p = c.prepareStatement("SELECT void_buffs FROM arena_player WHERE user_id=?")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                if (r.next() && r.getString(1) != null) cur = r.getString(1);
            }
        }
        String updated = cur.isEmpty() ? buffId : cur + "," + buffId;
        try (PreparedStatement p = c.prepareStatement("UPDATE arena_player SET void_buffs=? WHERE user_id=?")) {
            p.setString(1, updated);
            p.setLong(2, uid);
            p.executeUpdate();
        }
    }

    /**
     * 重置虚空秘境挑战层数与气运。
     */
    public void voidReset(Connection c, long uid) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("UPDATE arena_player SET void_cleared=0, void_buffs='' WHERE user_id=?")) {
            p.setLong(1, uid);
            p.executeUpdate();
        }
    }

    private static int seedDuration(String seed) {
        if ("herb".equals(seed)) return 120; // 2分钟
        if ("ore".equals(seed)) return 300;  // 5分钟
        if ("lotus".equals(seed)) return 600;// 10分钟
        return 0;
    }

    private static String fieldName(int fid) {
        if (fid == 1) return "一号·九品灵田";
        if (fid == 2) return "二号·五品灵田";
        return "三号·玄天宝田";
    }

    private static String cropName(String seed) {
        if ("herb".equals(seed)) return "凝气草";
        if ("ore".equals(seed)) return "玄铁藤";
        if ("lotus".equals(seed)) return "九叶金莲";
        return "休耕";
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
