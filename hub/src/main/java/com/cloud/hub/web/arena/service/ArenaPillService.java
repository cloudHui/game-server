package com.cloud.hub.web.arena.service;

import com.cloud.hub.game.arena.ArenaRules;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 四阶属性丹药领域服务。
 * <p>
 * 负责一至四阶属性丹的服用上限检查、永久属性附加及仙缘直接购买四阶太清神丹。
 *
 * @author cloud
 */
public class ArenaPillService {

    private final ArenaDao dao;

    public ArenaPillService(ArenaDao dao) {
        this.dao = dao;
    }

    /**
     * 服用指定等阶的属性丹。
     *
     * @param c    数据库连接
     * @param uid  玩家唯一标识
     * @param tier 丹药等阶 (1 ~ 4)
     * @throws SQLException 数据库异常
     */
    public void consume(Connection c, long uid, int tier) throws SQLException {
        int[] tiers = queryPillTiers(c, uid);
        if (!ArenaRules.canConsumePill(tier, tiers[0], tiers[1], tiers[2], tiers[3])) {
            throw new IllegalArgumentException("该阶丹药已达服用上限或总数已达 50 颗封顶");
        }
        String update = "UPDATE arena_player SET pills_tier" + tier + "=pills_tier" + tier + "+1 WHERE user_id=?";
        try (PreparedStatement p = c.prepareStatement(update)) {
            p.setLong(1, uid);
            p.executeUpdate();
        }
    }

    /**
     * 消耗仙缘购买四阶太清飞升神丹 (8 仙缘/颗)。
     *
     * @param c   数据库连接
     * @param uid 玩家唯一标识
     * @throws SQLException 数据库异常
     */
    public void buy(Connection c, long uid) throws SQLException {
        int[] tiers = queryPillTiers(c, uid);
        if (!ArenaRules.canConsumePill(4, tiers[0], tiers[1], tiers[2], tiers[3])) {
            throw new IllegalArgumentException("四阶极品神丹已达上限（最多15颗）或总丹药已满 50 颗");
        }
        dao.spend(c, uid, "fate", 8);
        String update = "UPDATE arena_player SET pills_tier4=pills_tier4+1 WHERE user_id=?";
        try (PreparedStatement p = c.prepareStatement(update)) {
            p.setLong(1, uid);
            p.executeUpdate();
        }
    }

    private int[] queryPillTiers(Connection c, long uid) throws SQLException {
        String query = "SELECT pills_tier1, pills_tier2, pills_tier3, pills_tier4 FROM arena_player WHERE user_id=?";
        try (PreparedStatement p = c.prepareStatement(query)) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                return new int[]{r.getInt(1), r.getInt(2), r.getInt(3), r.getInt(4)};
            }
        }
    }
}
