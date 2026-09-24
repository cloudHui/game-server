package com.cloud.hub.web.arena;

import com.cloud.hub.game.arena.journey.JourneyRules;
import com.cloud.hub.web.account.AccountDatabase;
import com.cloud.hub.web.arena.repository.ArenaInventoryRepository;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 竞技场仙途挂机与快速历练探索服务。
 * <p>
 * 负责玩家仙途体力、已解锁探索地图的最大进度管理，以及消耗体力进行快速探索获取丰厚修仙材料。
 *
 * @author cloud
 */
@Service
public class ArenaJourneyService {

    private final AccountDatabase db;
    private final ArenaInventoryRepository items;

    public ArenaJourneyService(AccountDatabase db, ArenaInventoryRepository items) {
        this.db = db;
        this.items = items;
    }

    /**
     * 容器启动时建表并初始化字段。
     */
    @PostConstruct
    public void init() {
        String sql = "CREATE TABLE IF NOT EXISTS arena_journey(" +
                "user_id INTEGER PRIMARY KEY, " +
                "stamina INTEGER NOT NULL DEFAULT 120, " +
                "max_map INTEGER NOT NULL DEFAULT 1, " +
                "updated_at INTEGER NOT NULL DEFAULT 0)";
        try (Connection c = db.getConnection();
             Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (Exception e) {
            throw new IllegalStateException("初始化 arena_journey 表失败", e);
        }
    }

    /**
     * 获取玩家当前历练探索状态与物品库存。
     *
     * @param uid 玩家 ID
     * @return 状态映射表
     * @throws SQLException 数据库异常
     */
    public Map<String, Object> state(long uid) throws SQLException {
        ensure(uid);
        String sql = "SELECT stamina, max_map FROM arena_journey WHERE user_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("stamina", r.getInt(1));
                m.put("maxMap", r.getInt(2));
                m.put("items", items.list(uid));
                return m;
            }
        }
    }

    /**
     * 执行指定地图的快速历练探索。
     *
     * @param uid  玩家 ID
     * @param map  地图编号
     * @param runs 探索次数
     * @return 更新后的历练状态
     * @throws SQLException 数据库异常
     */
    public synchronized Map<String, Object> explore(long uid, int map, int runs) throws SQLException {
        ensure(uid);
        Map<String, Integer> rewards = JourneyRules.rewards(map, runs);
        int cost = JourneyRules.staminaCost(runs);

        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            deductStaminaAndUnlock(c, uid, map, cost);
            grantRewards(c, uid, rewards);
            c.commit();
        }
        return state(uid);
    }

    private void deductStaminaAndUnlock(Connection c, long uid, int map, int cost) throws SQLException {
        String sql = "UPDATE arena_journey SET stamina = stamina - ?, max_map = max(max_map, ?) " +
                "WHERE user_id = ? AND stamina >= ? AND max_map >= ?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setInt(1, cost);
            p.setInt(2, Math.min(6, map + 1));
            p.setLong(3, uid);
            p.setInt(4, cost);
            p.setInt(5, map);
            if (p.executeUpdate() != 1) {
                throw new IllegalArgumentException("体力不足或地图未解锁");
            }
        }
    }

    private void grantRewards(Connection c, long uid, Map<String, Integer> rewards) throws SQLException {
        String sql = "INSERT INTO arena_item(user_id, item_id, quantity) VALUES(?,?,?) " +
                "ON CONFLICT(user_id, item_id) DO UPDATE SET quantity = quantity + excluded.quantity";
        for (Map.Entry<String, Integer> e : rewards.entrySet()) {
            try (PreparedStatement p = c.prepareStatement(sql)) {
                p.setLong(1, uid);
                p.setString(2, e.getKey());
                p.setInt(3, e.getValue());
                p.executeUpdate();
            }
        }
    }

    private void ensure(long uid) throws SQLException {
        items.seed(uid);
        String sql = "INSERT OR IGNORE INTO arena_journey(user_id) VALUES(?)";
        try (Connection c = db.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, uid);
            p.executeUpdate();
        }
    }
}
