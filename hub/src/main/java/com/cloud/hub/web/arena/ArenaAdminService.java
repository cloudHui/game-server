package com.cloud.hub.web.arena;

import com.cloud.hub.web.account.AccountDatabase;
import com.cloud.hub.web.arena.repository.ArenaInventoryRepository;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 竞技场后台管理服务。
 * <p>
 * 提供管理员对玩家修仙资源的调节、英雄养成数据的修改、背包道具的调整以及副本次数与建筑等级的干预功能。
 *
 * @author cloud
 */
@Service
public class ArenaAdminService {

    private static final Set<String> RESOURCES = new HashSet<>(Arrays.asList("liquid", "coins", "fate", "stones"));

    private final AccountDatabase db;
    private final ArenaRepository arena;
    private final ArenaInventoryRepository inventory;
    private final ArenaJourneyService journey;

    public ArenaAdminService(AccountDatabase db,
                             ArenaRepository arena,
                             ArenaInventoryRepository inventory,
                             ArenaJourneyService journey) {
        this.db = db;
        this.arena = arena;
        this.inventory = inventory;
        this.journey = journey;
    }

    /**
     * 查询所有注册玩家的修仙简要状态。
     *
     * @return 玩家修仙状态列表
     * @throws SQLException 数据库异常
     */
    public List<Map<String, Object>> players() throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        String sql = "SELECT u.id, u.username, u.nickname, a.liquid, a.coins, a.fate, a.stones, " +
                "a.dungeon_cleared, a.formation_level " +
                "FROM user u LEFT JOIN arena_player a ON a.user_id = u.id ORDER BY u.id";

        try (Connection c = db.getConnection();
             PreparedStatement p = c.prepareStatement(sql);
             ResultSet r = p.executeQuery()) {
            while (r.next()) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("userId", r.getLong("id"));
                x.put("username", r.getString("username"));
                x.put("nickname", r.getString("nickname"));
                x.put("initialized", r.getObject("liquid") != null);
                x.put("liquid", r.getLong("liquid"));
                x.put("coins", r.getLong("coins"));
                x.put("fate", r.getLong("fate"));
                x.put("stones", r.getLong("stones"));
                x.put("dungeonCleared", r.getInt("dungeon_cleared"));
                x.put("formationLevel", r.getInt("formation_level"));
                out.add(x);
            }
        }
        return out;
    }

    /**
     * 获取玩家修仙全量存档及背包、挂机探索详情。
     *
     * @param userId 目标玩家 ID
     * @return 玩家详情快照
     * @throws SQLException 数据库异常
     */
    public Map<String, Object> detail(long userId) throws SQLException {
        Map<String, Object> s = arena.state(userId);
        inventory.seed(userId);
        s.put("inventory", inventory.list(userId));
        s.put("journey", journey.state(userId));
        return s;
    }

    /**
     * 调整或增减指定玩家的背包道具数量。
     *
     * @param userId 目标玩家 ID
     * @param itemId 物品 ID
     * @param delta  增减变化量
     * @return 更新后的全量详情
     * @throws SQLException 数据库异常
     */
    public Map<String, Object> adjustItem(long userId, String itemId, int delta) throws SQLException {
        if (itemId == null || !itemId.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("物品 ID 非法");
        }
        inventory.seed(userId);
        try (Connection c = db.getConnection()) {
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT OR IGNORE INTO arena_item(user_id, item_id, quantity) VALUES(?,?,0)")) {
                p.setLong(1, userId);
                p.setString(2, itemId);
                p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement(
                    "UPDATE arena_item SET quantity = max(0, quantity + ?) WHERE user_id = ? AND item_id = ?")) {
                p.setInt(1, delta);
                p.setLong(2, userId);
                p.setString(3, itemId);
                p.executeUpdate();
            }
        }
        return detail(userId);
    }

    /**
     * 调整玩家指定基础货币（灵液、灵币、仙缘、战阵石）。
     *
     * @param userId   目标玩家 ID
     * @param resource 资源名称
     * @param delta    变动值（可正可负）
     * @return 更新后的状态
     * @throws SQLException 数据库异常
     */
    public Map<String, Object> adjust(long userId, String resource, long delta) throws SQLException {
        if (!RESOURCES.contains(resource)) {
            throw new IllegalArgumentException("未知资源: " + resource);
        }
        arena.state(userId);
        try (Connection c = db.getConnection();
             PreparedStatement p = c.prepareStatement(
                     "UPDATE arena_player SET " + resource + " = max(0, " + resource + " + ?) WHERE user_id = ?")) {
            p.setLong(1, delta);
            p.setLong(2, userId);
            p.executeUpdate();
        }
        return arena.state(userId);
    }

    /**
     * 调整玩家副本进度与建筑等级。
     *
     * @param userId          目标玩家 ID
     * @param dungeonCleared  通关层数
     * @param dungeonAttempts 剩余副本次数
     * @param formationLevel  战阵等级
     * @param grottoLevel     洞府等级
     * @return 更新后的全量详情
     * @throws SQLException 数据库异常
     */
    public Map<String, Object> progress(long userId, int dungeonCleared, int dungeonAttempts,
                                        int formationLevel, int grottoLevel) throws SQLException {
        if (dungeonCleared < 0 || dungeonCleared > 12) {
            throw new IllegalArgumentException("通关数必须在 0 到 12 之间");
        }
        if (dungeonAttempts < 0) {
            throw new IllegalArgumentException("副本次数不能小于 0");
        }
        if (formationLevel < 1) {
            throw new IllegalArgumentException("战阵等级不能小于 1");
        }
        if (grottoLevel < 1) {
            throw new IllegalArgumentException("洞府等级不能小于 1");
        }
        arena.state(userId);
        try (Connection c = db.getConnection();
             PreparedStatement p = c.prepareStatement(
                     "UPDATE arena_player SET dungeon_cleared = ?, dungeon_attempts = ?, " +
                             "formation_level = ?, grotto_level = ? WHERE user_id = ?")) {
            p.setInt(1, dungeonCleared);
            p.setInt(2, dungeonAttempts);
            p.setInt(3, formationLevel);
            p.setInt(4, grottoLevel);
            p.setLong(5, userId);
            p.executeUpdate();
        }
        return detail(userId);
    }

    /**
     * 调整玩家英雄属性、星级、道法等级及碎片。
     *
     * @param userId 目标玩家 ID
     * @param heroId 英雄 ID
     * @param rank   境界等阶
     * @param stars  星级
     * @param skill  功法等级
     * @param shards 碎片数量
     * @return 更新后的状态
     * @throws SQLException 数据库异常
     */
    public Map<String, Object> hero(long userId, String heroId, int rank, int stars, int skill, int shards)
            throws SQLException {
        if (heroId == null || !heroId.matches("[a-z0-9_-]{1,32}")) {
            throw new IllegalArgumentException("仙侣 ID 非法");
        }
        arena.state(userId);
        String sql = "INSERT INTO arena_hero(user_id, hero_id, rank, stars, skill_level, shards) " +
                "VALUES(?,?,?,?,?,?) ON CONFLICT(user_id, hero_id) DO UPDATE SET " +
                "rank = excluded.rank, stars = excluded.stars, " +
                "skill_level = excluded.skill_level, shards = excluded.shards";

        try (Connection c = db.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, userId);
            p.setString(2, heroId);
            p.setInt(3, clamp(rank, 1, 80));
            p.setInt(4, clamp(stars, 1, 5));
            p.setInt(5, clamp(skill, 1, 100));
            p.setInt(6, Math.max(0, shards));
            p.executeUpdate();
        }
        return arena.state(userId);
    }

    private int clamp(int n, int min, int max) {
        return Math.max(min, Math.min(max, n));
    }
}
