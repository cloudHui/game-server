package com.cloud.hub.web.arena.service;

import com.cloud.hub.game.arena.ArenaRules;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 镇魔通天塔与挂机演武领域服务。
 * <p>
 * 负责 999 层通天塔逐层挑战、每日宗门俸禄结算及离线/在线持续演武收益收获。
 *
 * @author cloud
 */
public class ArenaTowerService {

    private final ArenaDao dao;

    public ArenaTowerService(ArenaDao dao) {
        this.dao = dao;
    }

    /**
     * 挑战指定层数的镇魔通天塔。
     *
     * @param c     数据库连接
     * @param uid   玩家唯一标识
     * @param stage 挑战层数 (1 ~ 999)
     * @throws SQLException 数据库异常
     */
    public void climb(Connection c, long uid, int stage) throws SQLException {
        int cleared;
        int tries;
        String query = "SELECT dungeon_cleared, dungeon_attempts FROM arena_player WHERE user_id=?";
        try (PreparedStatement p = c.prepareStatement(query)) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                cleared = r.getInt(1);
                tries = r.getInt(2);
            }
        }
        if (stage < 1 || stage > ArenaRules.MAX_TOWER_FLOORS || stage > cleared + 1) {
            throw new IllegalArgumentException("请先通关前置镇魔塔层");
        }
        if (tries < 1) {
            throw new IllegalArgumentException("今日镇魔副本次数已耗尽");
        }

        boolean first = stage > cleared;
        ArenaRules.Reward reward = ArenaRules.dungeonReward(stage, first);
        int pillTier = first ? ArenaRules.firstClearPillTier(stage) : 0;

        StringBuilder sql = new StringBuilder(
                "UPDATE arena_player SET dungeon_attempts=dungeon_attempts-1, " +
                        "dungeon_cleared=max(dungeon_cleared,?), liquid=liquid+?, coins=coins+?");
        if (pillTier > 0) {
            sql.append(", pills_tier").append(pillTier).append(" = min(15, pills_tier").append(pillTier).append("+1)");
        }
        sql.append(" WHERE user_id=?");

        try (PreparedStatement p = c.prepareStatement(sql.toString())) {
            p.setInt(1, stage);
            p.setLong(2, reward.liquid);
            p.setLong(3, reward.coins);
            p.setLong(4, uid);
            p.executeUpdate();
        }
        dao.progress(c, uid, "task_dungeon", 1);
    }

    /**
     * 结算并领取今日镇魔通天塔俸禄。
     *
     * @param c   数据库连接
     * @param uid 玩家唯一标识
     * @throws SQLException 数据库异常
     */
    public void settle(Connection c, long uid) throws SQLException {
        int cleared;
        int settled;
        String query = "SELECT dungeon_cleared, dungeon_settled FROM arena_player WHERE user_id=?";
        try (PreparedStatement p = c.prepareStatement(query)) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                cleared = r.getInt(1);
                settled = r.getInt(2);
            }
        }
        if (settled == 1) {
            throw new IllegalArgumentException("今日镇魔俸禄已领取");
        }
        if (cleared <= 0) {
            throw new IllegalArgumentException("尚未通关任何镇魔塔层，无法结算");
        }

        ArenaRules.DailySettlement ds = ArenaRules.dungeonDailyReward(cleared);
        String update = "UPDATE arena_player SET liquid=liquid+?, coins=coins+?, stones=stones+?, " +
                "fate=fate+?, dungeon_settled=1 WHERE user_id=?";
        try (PreparedStatement p = c.prepareStatement(update)) {
            p.setLong(1, ds.liquid);
            p.setLong(2, ds.coins);
            p.setLong(3, ds.stones);
            p.setInt(4, ds.fate);
            p.setLong(5, uid);
            p.executeUpdate();
        }
    }

    /**
     * 收获演武挂机收益（离线与在线时间积累）。
     *
     * @param c   数据库连接
     * @param uid 玩家唯一标识
     * @param now 当前系统时间戳
     * @throws SQLException 数据库异常
     */
    public void grind(Connection c, long uid, long now) throws SQLException {
        long last;
        int cleared;
        int gLv;
        int gExp;
        String query = "SELECT last_grind_time, dungeon_cleared, grind_level, grind_exp FROM arena_player WHERE user_id=?";
        try (PreparedStatement p = c.prepareStatement(query)) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                last = r.getLong(1);
                cleared = r.getInt(2);
                gLv = r.getInt(3);
                gExp = r.getInt(4);
            }
        }
        if (last == 0) {
            last = now - 60000;
        }
        long minutes = Math.max(1, Math.min(1440, (now - last) / 60000));
        ArenaRules.GrindRates gr = ArenaRules.grindRates(cleared);

        long addExp = gr.expPerMin * minutes;
        long addLiquid = gr.liquidPerMin * minutes;
        long addCoins = gr.coinsPerMin * minutes;
        int addShards = (int) (minutes / 120);

        long totalExp = gExp + addExp;
        while (totalExp >= ArenaRules.grindExpForLevel(gLv)) {
            totalExp -= ArenaRules.grindExpForLevel(gLv);
            gLv++;
        }

        String update = "UPDATE arena_player SET grind_level=?, grind_exp=?, last_grind_time=?, " +
                "liquid=liquid+?, coins=coins+?, beast_shards=beast_shards+? WHERE user_id=?";
        try (PreparedStatement p = c.prepareStatement(update)) {
            p.setInt(1, gLv);
            p.setLong(2, totalExp);
            p.setLong(3, now);
            p.setLong(4, addLiquid);
            p.setLong(5, addCoins);
            p.setInt(6, addShards);
            p.setLong(7, uid);
            p.executeUpdate();
        }
    }
}
