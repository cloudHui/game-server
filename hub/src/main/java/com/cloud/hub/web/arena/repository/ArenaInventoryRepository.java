package com.cloud.hub.web.arena.repository;

import com.cloud.hub.web.account.AccountDatabase;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 竞技场修仙背包道具仓储。
 * <p>
 * 负责玩家基础草药、矿石、星屑道具存储，物品列表拉取及配方消耗扣减与合成产出持久化。
 *
 * @author cloud
 */
@Repository
public class ArenaInventoryRepository {

    private final AccountDatabase db;

    public ArenaInventoryRepository(AccountDatabase db) {
        this.db = db;
    }

    /**
     * 容器启动时初始化道具背包表。
     */
    @PostConstruct
    public void init() {
        String sql = "CREATE TABLE IF NOT EXISTS arena_item(" +
                "user_id INTEGER NOT NULL, " +
                "item_id TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(user_id, item_id))";
        try (Connection c = db.getConnection();
             Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new IllegalStateException("初始化剑气背包失败", e);
        }
    }

    /**
     * 查询指定玩家数量大于 0 的所有背包道具。
     *
     * @param uid 玩家 ID
     * @return 道具列表
     * @throws SQLException 数据库异常
     */
    public List<Map<String, Object>> list(long uid) throws SQLException {
        List<Map<String, Object>> out = new ArrayList<>();
        String sql = "SELECT item_id, quantity FROM arena_item WHERE user_id = ? AND quantity > 0 ORDER BY item_id";
        try (Connection c = db.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    Map<String, Object> x = new LinkedHashMap<>();
                    x.put("id", r.getString(1));
                    x.put("quantity", r.getInt(2));
                    out.add(x);
                }
            }
        }
        return out;
    }

    /**
     * 新玩家或背包初始化赠送初始修仙材料。
     *
     * @param uid 玩家 ID
     * @throws SQLException 数据库异常
     */
    public void seed(long uid) throws SQLException {
        String sql = "INSERT OR IGNORE INTO arena_item(user_id, item_id, quantity) VALUES(?,?,?)";
        try (Connection c = db.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {
            String[] ids = {"herb", "ore", "star_dust"};
            int[] counts = {12, 12, 6};
            for (int i = 0; i < ids.length; i++) {
                p.setLong(1, uid);
                p.setString(2, ids[i]);
                p.setInt(3, counts[i]);
                p.addBatch();
            }
            p.executeBatch();
        }
    }

    /**
     * 事务执行材料合成：扣除灵币与输入材料，增加合成产物。
     *
     * @param uid    玩家 ID
     * @param input  输入材料 ID
     * @param need   消耗材料数量
     * @param output 输出产物 ID
     * @param gain   产物获得数量
     * @param coins  消耗灵币
     * @throws SQLException 数据库异常
     */
    public synchronized void craft(long uid, String input, int need, String output, int gain, int coins)
            throws SQLException {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            deductCost(c, uid, coins, input, need);
            addOutputItem(c, uid, output, gain);
            c.commit();
        }
    }

    private void deductCost(Connection c, long uid, int coins, String input, int need) throws SQLException {
        String sqlCoin = "UPDATE arena_player SET coins = coins - ? WHERE user_id = ? AND coins >= ?";
        try (PreparedStatement p = c.prepareStatement(sqlCoin)) {
            p.setInt(1, coins);
            p.setLong(2, uid);
            p.setInt(3, coins);
            if (p.executeUpdate() != 1) {
                throw new IllegalArgumentException("灵币不足");
            }
        }

        String sqlItem = "UPDATE arena_item SET quantity = quantity - ? WHERE user_id = ? AND item_id = ? AND quantity >= ?";
        try (PreparedStatement p = c.prepareStatement(sqlItem)) {
            p.setInt(1, need);
            p.setLong(2, uid);
            p.setString(3, input);
            p.setInt(4, need);
            if (p.executeUpdate() != 1) {
                throw new IllegalArgumentException("材料不足");
            }
        }
    }

    private void addOutputItem(Connection c, long uid, String output, int gain) throws SQLException {
        String sql = "INSERT INTO arena_item(user_id, item_id, quantity) VALUES(?,?,?) " +
                "ON CONFLICT(user_id, item_id) DO UPDATE SET quantity = quantity + excluded.quantity";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, uid);
            p.setString(2, output);
            p.setInt(3, gain);
            p.executeUpdate();
        }
    }
}
