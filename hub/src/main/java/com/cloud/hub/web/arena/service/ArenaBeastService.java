package com.cloud.hub.web.arena.service;

import com.cloud.hub.game.arena.ArenaRules;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 坐骑神兽与兽神塔领域服务。
 * <p>
 * 负责神兽真身消耗碎片进阶进化、战阵放大增益以及挑战万妖兽神塔获取神兽碎片。
 */
public class ArenaBeastService {
    private final ArenaDao dao;

    public ArenaBeastService(ArenaDao dao) {
        this.dao = dao;
    }

    /**
     * 消耗真身碎片进阶坐骑神兽。
     *
     * @param c   数据库连接
     * @param uid 玩家唯一标识
     */
    public void upgrade(Connection c, long uid) throws SQLException {
        int bLevel, shards;
        try (PreparedStatement p = c.prepareStatement("SELECT beast_level, beast_shards FROM arena_player WHERE user_id=?")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                bLevel = r.getInt(1);
                shards = r.getInt(2);
            }
        }
        int need = ArenaRules.beastShardCost(bLevel);
        if (shards < need) {
            throw new IllegalArgumentException("神兽碎片不足，进阶需 " + need + " 碎片 (当前拥有 " + shards + ")");
        }

        try (PreparedStatement p = c.prepareStatement(
                "UPDATE arena_player SET beast_level=beast_level+1, beast_shards=beast_shards-? WHERE user_id=?")) {
            p.setInt(1, need);
            p.setLong(2, uid);
            p.executeUpdate();
        }
        dao.progress(c, uid, "task_beast", 1);
    }

    /**
     * 挑战万妖兽神塔层数获取神兽碎片。
     *
     * @param c     数据库连接
     * @param uid   玩家唯一标识
     * @param stage 兽神塔层数 (1 ~ 10)
     */
    public void challengeTower(Connection c, long uid, int stage) throws SQLException {
        int cleared, tries;
        try (PreparedStatement p = c.prepareStatement("SELECT beast_tower_cleared, beast_tower_attempts FROM arena_player WHERE user_id=?")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                cleared = r.getInt(1);
                tries = r.getInt(2);
            }
        }
        if (stage < 1 || stage > 10 || stage > cleared + 1) {
            throw new IllegalArgumentException("请先通关前置兽神塔层");
        }
        if (tries < 1) {
            throw new IllegalArgumentException("今日兽神塔挑战次数已耗尽");
        }

        int dropShards = ArenaRules.beastTowerDrop(stage);
        try (PreparedStatement p = c.prepareStatement(
                "UPDATE arena_player SET beast_tower_attempts=beast_tower_attempts-1, " +
                "beast_tower_cleared=max(beast_tower_cleared,?), beast_shards=beast_shards+? WHERE user_id=?")) {
            p.setInt(1, stage);
            p.setInt(2, dropShards);
            p.setLong(3, uid);
            p.executeUpdate();
        }
    }
}
