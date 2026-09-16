package com.cloud.hub.web.arena;

import com.cloud.hub.web.account.AccountDatabase;
import com.cloud.hub.web.arena.service.*;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * 剑气除魔核心数据仓储与业务分发门面 (Facade)。
 * <p>
 * 统一管理数据库连接与事务提交，并将具体业务逻辑委托给各专属领域服务：
 * <ul>
 *   <li>{@link ArenaDao}: 底层表结构与原子增删改查</li>
 *   <li>{@link ArenaTowerService}: 通天塔挑战、每日俸禄与离线挂机演武</li>
 *   <li>{@link ArenaBeastService}: 坐骑神兽真身进阶与兽神塔试炼</li>
 *   <li>{@link ArenaPillService}: 四阶属性丹药服用与仙缘购买</li>
 *   <li>{@link ArenaHeroService}: 仙修突破、功法提升、升星进阶与招募抽卡</li>
 *   <li>{@link ArenaTaskService}: 每日活跃任务、洞府聚灵与剑阵强化</li>
 * </ul>
 */
@Repository
public class ArenaRepository {
    private final AccountDatabase db;
    private final ArenaDao dao;
    private final ArenaTowerService tower;
    private final ArenaBeastService beasts;
    private final ArenaPillService pills;
    private final ArenaHeroService heroes;
    private final ArenaTaskService tasks;

    public ArenaRepository(AccountDatabase db) {
        this.db = db;
        this.dao = new ArenaDao();
        this.tower = new ArenaTowerService(dao);
        this.beasts = new ArenaBeastService(dao);
        this.pills = new ArenaPillService(dao);
        this.heroes = new ArenaHeroService(dao);
        this.tasks = new ArenaTaskService(dao);
    }

    /**
     * 容器启动时自动初始化数据表及补齐字段。
     */
    @PostConstruct
    public void init() {
        try (Connection c = db.getConnection()) {
            dao.init(c);
        } catch (SQLException e) {
            throw new IllegalStateException("初始化剑气除魔 SQLite 表失败", e);
        }
    }

    /**
     * 获取玩家当前全量修行状态快照。
     *
     * @param uid 玩家唯一标识
     * @return 状态映射表
     * @throws SQLException 数据库异常
     */
    public synchronized Map<String, Object> state(long uid) throws SQLException {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            dao.ensure(c, uid);
            dao.refresh(c, uid);
            Map<String, Object> out = dao.read(c, uid);
            c.commit();
            return out;
        }
    }

    /**
     * 统一处理玩家修行操作行为并持久化。
     *
     * @param uid    玩家唯一标识
     * @param action 操作指令
     * @param id     目标标识（英雄ID、任务ID或技能代码）
     * @param count  操作数值或层数
     * @param now    时间戳
     * @return 更新后的全量状态快照
     * @throws SQLException 数据库异常
     */
    public synchronized Map<String, Object> action(long uid, String action, String id, int count, long now) throws SQLException {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            dao.ensure(c, uid);
            dao.refresh(c, uid);

            switch (action) {
                case "rank":           heroes.rankUp(c, uid, id); break;
                case "skill":          heroes.skillUp(c, uid, id); break;
                case "star":           heroes.starUp(c, uid, id); break;
                case "draw":           heroes.draw(c, uid, count, now); break;

                case "dungeon":        tower.climb(c, uid, count); break;
                case "dungeon_settle": tower.settle(c, uid); break;
                case "grind_claim":    tower.grind(c, uid, now); break;

                case "beast_upgrade":  beasts.upgrade(c, uid); break;
                case "beast_tower":    beasts.challengeTower(c, uid, count); break;

                case "consume_pill":   pills.consume(c, uid, count); break;
                case "buy_pill":       pills.buy(c, uid); break;

                case "claim":          tasks.claim(c, uid, id); break;
                case "grotto":         tasks.grotto(c, uid, now); break;
                case "formation":      tasks.upgradeFormation(c, uid); break;
                case "equip_skill":    tasks.equipSkill(c, uid, id); break;

                case "arena":          dao.progress(c, uid, "task_arena", 1); break;
                default: throw new IllegalArgumentException("未知操作: " + action);
            }

            Map<String, Object> out = dao.read(c, uid);
            c.commit();
            return out;
        }
    }
}
