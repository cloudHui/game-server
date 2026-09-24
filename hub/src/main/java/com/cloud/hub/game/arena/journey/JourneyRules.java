package com.cloud.hub.game.arena.journey;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 历练纯规则引擎。
 * <p>
 * 纯内存计算，无 HTTP、SQL、系统时间依赖。
 * 管理体力消耗、体力随时间自动恢复及历练产出奖励。
 *
 * @author cloud
 */
public final class JourneyRules {
    /** 玩家最大体力上限 */
    public static final int MAX_STAMINA = 120;
    /** 体力恢复间隔毫秒数 (5分钟 = 300,000毫秒) */
    private static final long STAMINA_RECOVERY_INTERVAL_MILLIS = 5L * 60L * 1000L;
    /** 体力恢复间隔秒数 (300秒) */
    private static final int STAMINA_RECOVERY_INTERVAL_SECONDS = 5 * 60;

    private JourneyRules() {
    }

    /**
     * 计算历练产出的素材奖励。
     *
     * @param map  关卡地图索引 (1~6)
     * @param runs 挑战次数 (1~10)
     * @return 奖励素材与产出数量的键值映射
     */
    public static Map<String, Integer> rewards(int map, int runs) {
        if (map < 1 || map > 6 || runs < 1 || runs > 10) {
            throw new IllegalArgumentException("历练参数非法");
        }
        Map<String, Integer> rewards = new LinkedHashMap<>();
        rewards.put(map % 2 == 0 ? "ore" : "herb", map * runs * 2);
        rewards.put("star_dust", Math.max(1, map * runs / 2));
        return rewards;
    }

    /**
     * 计算指定挑战次数所需消耗的体力值。
     *
     * @param runs 挑战次数
     * @return 消耗的体力总量 (每次 6 点体力)
     */
    public static int staminaCost(int runs) {
        return runs * 6;
    }

    /**
     * 按经过时间结算体力自动恢复。
     * <ul>
     *   <li>updatedAt=0 表示首次读取，不追溯历史时间</li>
     *   <li>达到上限时更新时间推进到当前时刻，不积攒超出的恢复时间</li>
     * </ul>
     *
     * @param stamina   当前已有体力
     * @param updatedAt 上次体力刷新时间戳 (毫秒)
     * @param now       当前时间戳 (毫秒)
     * @return 体力恢复状态快照
     */
    public static StaminaRecovery recover(int stamina, long updatedAt, long now) {
        if (stamina < 0 || stamina > MAX_STAMINA || updatedAt < 0 || now < 0) {
            throw new IllegalArgumentException("体力恢复参数非法");
        }
        if (updatedAt == 0 || now <= updatedAt) {
            return new StaminaRecovery(stamina, now, STAMINA_RECOVERY_INTERVAL_SECONDS);
        }
        if (stamina >= MAX_STAMINA) {
            return new StaminaRecovery(MAX_STAMINA, now, 0);
        }

        long elapsed = now - updatedAt;
        long recovered = elapsed / STAMINA_RECOVERY_INTERVAL_MILLIS;
        if (recovered == 0) {
            return new StaminaRecovery(stamina, updatedAt,
                    secondsToNext(elapsed % STAMINA_RECOVERY_INTERVAL_MILLIS));
        }

        long recoveredStamina = stamina + recovered;
        if (recoveredStamina >= MAX_STAMINA) {
            return new StaminaRecovery(MAX_STAMINA, now, 0);
        }
        long recoveryMillis = recovered * STAMINA_RECOVERY_INTERVAL_MILLIS;
        long newUpdatedAt = updatedAt + recoveryMillis;
        return new StaminaRecovery((int) recoveredStamina, newUpdatedAt,
                secondsToNext(elapsed - recoveryMillis));
    }

    /**
     * 计算距离下一次体力恢复还需等待的秒数 (向上取整)。
     */
    private static int secondsToNext(long elapsedSinceRecovery) {
        long remaining = STAMINA_RECOVERY_INTERVAL_MILLIS - elapsedSinceRecovery;
        return (int) ((remaining + 999L) / 1000L);
    }

    /**
     * 体力恢复计算结果包装。
     */
    public static final class StaminaRecovery {
        /** 当前结算后的最终体力值 */
        public final int stamina;
        /** 归一化后的最后更新时间戳 */
        public final long updatedAt;
        /** 距离下一点体力恢复的剩余倒计时 (秒) */
        public final int secondsToNext;

        private StaminaRecovery(int stamina, long updatedAt, int secondsToNext) {
            this.stamina = stamina;
            this.updatedAt = updatedAt;
            this.secondsToNext = secondsToNext;
        }
    }
}
