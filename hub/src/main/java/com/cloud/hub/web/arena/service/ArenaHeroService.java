package com.cloud.hub.web.arena.service;

import com.cloud.hub.game.arena.ArenaRules;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Random;

/**
 * 仙侣培养与招募领域服务。
 * <p>
 * 负责仙修境界突破、功法参悟升级、本命碎片升星（1~5星）以及消耗仙缘抽卡招募。
 */
public class ArenaHeroService {
    private static final String[] HERO_POOL = {
            "qinglan", "xuanshuang", "chixiao", "taixu", "yaohuang", "leizun", "jianhuang"
    };

    private final ArenaDao dao;

    public ArenaHeroService(ArenaDao dao) {
        this.dao = dao;
    }

    /**
     * 仙修境界突破提升阶位。
     *
     * @param c      数据库连接
     * @param uid    玩家唯一标识
     * @param heroId 仙修标识
     */
    public void rankUp(Connection c, long uid, String heroId) throws SQLException {
        int rank;
        try (PreparedStatement p = c.prepareStatement("SELECT rank FROM arena_hero WHERE user_id=? AND hero_id=?")) {
            p.setLong(1, uid);
            p.setString(2, heroId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("尚未拥有该仙侣");
                rank = r.getInt(1);
            }
        }
        dao.spend(c, uid, "liquid", ArenaRules.rankCost(rank));
        try (PreparedStatement p = c.prepareStatement("UPDATE arena_hero SET rank=rank+1 WHERE user_id=? AND hero_id=?")) {
            p.setLong(1, uid);
            p.setString(2, heroId);
            p.executeUpdate();
        }
        dao.progress(c, uid, "task_rank", 1);
    }

    /**
     * 仙修本命功法参悟升级。
     *
     * @param c      数据库连接
     * @param uid    玩家唯一标识
     * @param heroId 仙修标识
     */
    public void skillUp(Connection c, long uid, String heroId) throws SQLException {
        int skillLevel;
        try (PreparedStatement p = c.prepareStatement("SELECT skill_level FROM arena_hero WHERE user_id=? AND hero_id=?")) {
            p.setLong(1, uid);
            p.setString(2, heroId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("尚未拥有该仙侣");
                skillLevel = r.getInt(1);
            }
        }
        dao.spend(c, uid, "coins", ArenaRules.skillCost(skillLevel));
        try (PreparedStatement p = c.prepareStatement("UPDATE arena_hero SET skill_level=skill_level+1 WHERE user_id=? AND hero_id=?")) {
            p.setLong(1, uid);
            p.setString(2, heroId);
            p.executeUpdate();
        }
        dao.progress(c, uid, "task_rank", 1);
    }

    /**
     * 消耗仙修碎片进行升星进阶。
     *
     * @param c      数据库连接
     * @param uid    玩家唯一标识
     * @param heroId 仙修标识
     */
    public void starUp(Connection c, long uid, String heroId) throws SQLException {
        int stars, shards;
        try (PreparedStatement p = c.prepareStatement("SELECT stars, shards FROM arena_hero WHERE user_id=? AND hero_id=?")) {
            p.setLong(1, uid);
            p.setString(2, heroId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new IllegalArgumentException("尚未拥有该仙侣");
                stars = r.getInt(1);
                shards = r.getInt(2);
            }
        }
        if (stars >= 5) {
            throw new IllegalArgumentException("该仙修已达五星圆满");
        }
        int need = ArenaRules.starShardCost(stars);
        if (shards < need) {
            throw new IllegalArgumentException("本命碎片不足，还需 " + (need - shards) + " 碎片");
        }
        try (PreparedStatement p = c.prepareStatement("UPDATE arena_hero SET stars=stars+1, shards=shards-? WHERE user_id=? AND hero_id=?")) {
            p.setInt(1, need);
            p.setLong(2, uid);
            p.setString(3, heroId);
            p.executeUpdate();
        }
    }

    /**
     * 消耗仙缘寻访招募仙侣（单抽或十连）。
     *
     * @param c     数据库连接
     * @param uid   玩家唯一标识
     * @param count 抽取次数 (1 或 10)
     * @param seed  随机种子
     */
    public void draw(Connection c, long uid, int count, long seed) throws SQLException {
        if (count != 1 && count != 10) {
            throw new IllegalArgumentException("只支持单抽或十连");
        }
        dao.spend(c, uid, "fate", count);

        int pity;
        try (PreparedStatement p = c.prepareStatement("SELECT pity FROM arena_player WHERE user_id=?")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                pity = r.getInt(1);
            }
        }

        Random rng = new Random(seed);
        for (int i = 0; i < count; i++) {
            pity++;
            String quality = ArenaRules.drawQuality(pity, rng);
            if (count == 10 && i == 9 && "橙".equals(quality)) {
                quality = "红"; // 十连保底至少紫红
            }
            if ("金".equals(quality)) {
                pity = 0;
            }

            String heroId = HERO_POOL[rng.nextInt(HERO_POOL.length)];
            boolean dup = dao.hasHero(c, uid, heroId);
            if (dup) {
                dao.addHeroShards(c, uid, heroId, ArenaRules.shards(quality));
            } else {
                dao.addHero(c, uid, heroId);
            }
            dao.logDraw(c, uid, heroId, quality, dup);
        }

        try (PreparedStatement p = c.prepareStatement("UPDATE arena_player SET pity=? WHERE user_id=?")) {
            p.setInt(1, pity);
            p.setLong(2, uid);
            p.executeUpdate();
        }
        dao.progress(c, uid, "task_recruit", count);
    }
}
