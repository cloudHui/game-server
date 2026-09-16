package com.cloud.hub.web.arena.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 每日问道任务、洞府聚灵与剑阵领域服务。
 * <p>
 * 负责每日活跃任务达成与奖励领取、洞府聚灵挂机灵液收获、诛仙剑阵强化及修士神通技能切换装配。
 */
public class ArenaTaskService {
    private static final Map<String, int[]> TASK_CFG = new HashMap<>();

    static {
        // [目标次数, 灵液, 灵币, 仙缘, 战阵石, 活跃点]
        TASK_CFG.put("login", new int[]{1, 200, 0, 0, 0, 10});
        TASK_CFG.put("dungeon", new int[]{3, 500, 0, 0, 0, 20});
        TASK_CFG.put("rank", new int[]{1, 0, 300, 0, 0, 15});
        TASK_CFG.put("recruit", new int[]{1, 0, 0, 1, 0, 15});
        TASK_CFG.put("arena", new int[]{1, 0, 400, 0, 0, 20});
        TASK_CFG.put("beast", new int[]{1, 0, 0, 0, 150, 20});
    }

    private final ArenaDao dao;

    public ArenaTaskService(ArenaDao dao) {
        this.dao = dao;
    }

    /**
     * 领取已达成的每日任务奖励。
     *
     * @param c      数据库连接
     * @param uid    玩家唯一标识
     * @param taskId 任务标识 ("login", "dungeon", "rank", "recruit", "arena", "beast")
     */
    public void claim(Connection c, long uid, String taskId) throws SQLException {
        int[] cfg = TASK_CFG.get(taskId);
        if (cfg == null) throw new IllegalArgumentException("未知任务: " + taskId);

        String claimed;
        int progress;
        try (PreparedStatement p = c.prepareStatement("SELECT task_" + taskId + ", claimed FROM arena_player WHERE user_id=?")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                progress = r.getInt(1);
                claimed = r.getString(2);
            }
        }
        if (Arrays.asList(claimed.split(",")).contains(taskId)) {
            throw new IllegalArgumentException("该任务奖励已领取");
        }
        if (progress < cfg[0]) {
            throw new IllegalArgumentException("任务尚未达成");
        }

        try (PreparedStatement p = c.prepareStatement(
                "UPDATE arena_player SET liquid=liquid+?, coins=coins+?, fate=fate+?, stones=stones+?, claimed=claimed||? WHERE user_id=?")) {
            p.setInt(1, cfg[1]);
            p.setInt(2, cfg[2]);
            p.setInt(3, cfg[3]);
            p.setInt(4, cfg[4]);
            p.setString(5, taskId + ",");
            p.setLong(6, uid);
            p.executeUpdate();
        }
    }

    /**
     * 洞府聚灵收益收取。
     *
     * @param c   数据库连接
     * @param uid 玩家唯一标识
     * @param now 当前时间戳
     */
    public void grotto(Connection c, long uid, long now) throws SQLException {
        long last;
        int lv;
        try (PreparedStatement p = c.prepareStatement("SELECT grotto_claim_at, grotto_level FROM arena_player WHERE user_id=?")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                last = r.getLong(1);
                lv = r.getInt(2);
            }
        }
        long hours = (last == 0) ? 4 : Math.max(1, Math.min(12, (now - last) / 3600000));
        try (PreparedStatement p = c.prepareStatement("UPDATE arena_player SET liquid=liquid+?, grotto_claim_at=? WHERE user_id=?")) {
            p.setLong(1, hours * 240L * lv);
            p.setLong(2, now);
            p.setLong(3, uid);
            p.executeUpdate();
        }
    }

    /**
     * 诛仙战阵升级提升。
     *
     * @param c   数据库连接
     * @param uid 玩家唯一标识
     */
    public void upgradeFormation(Connection c, long uid) throws SQLException {
        int lv;
        try (PreparedStatement p = c.prepareStatement("SELECT formation_level FROM arena_player WHERE user_id=?")) {
            p.setLong(1, uid);
            try (ResultSet r = p.executeQuery()) {
                r.next();
                lv = r.getInt(1);
            }
        }
        dao.spend(c, uid, "stones", (long) lv * 100);
        try (PreparedStatement p = c.prepareStatement("UPDATE arena_player SET formation_level=formation_level+1 WHERE user_id=?")) {
            p.setLong(1, uid);
            p.executeUpdate();
        }
    }

    /**
     * 切换装配本命神通道法。
     *
     * @param c     数据库连接
     * @param uid   玩家唯一标识
     * @param skill 神通标识 ("pierce", "silence", "defense", "heal")
     */
    public void equipSkill(Connection c, long uid, String skill) throws SQLException {
        if (!Arrays.asList("silence", "pierce", "defense", "heal").contains(skill)) {
            skill = "pierce";
        }
        try (PreparedStatement p = c.prepareStatement("UPDATE arena_player SET equipped_skill=? WHERE user_id=?")) {
            p.setString(1, skill);
            p.setLong(2, uid);
            p.executeUpdate();
        }
    }
}
