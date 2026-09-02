package game.arena.journey;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JourneyRules {
    private JourneyRules() {
    }

    public static final int MAX_STAMINA = 120;
    private static final long STAMINA_RECOVERY_INTERVAL_MILLIS = 5L * 60L * 1000L;
    private static final int STAMINA_RECOVERY_INTERVAL_SECONDS = 5 * 60;

    public static Map<String, Integer> rewards(int map, int runs) {
        if (map < 1 || map > 6 || runs < 1 || runs > 10) {
            throw new IllegalArgumentException("历练参数非法");
        }
        Map<String, Integer> rewards = new LinkedHashMap<>();
        rewards.put(map % 2 == 0 ? "ore" : "herb", map * runs * 2);
        rewards.put("star_dust", Math.max(1, map * runs / 2));
        return rewards;
    }

    public static int staminaCost(int runs) {
        return runs * 6;
    }

    /**
     * 按经过时间恢复体力。updatedAt=0 表示首次读取，不追溯历史时间。
     * 达到上限时更新时间推进到当前时刻，不积攒超出的恢复时间。
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

    private static int secondsToNext(long elapsedSinceRecovery) {
        long remaining = STAMINA_RECOVERY_INTERVAL_MILLIS - elapsedSinceRecovery;
        return (int) ((remaining + 999L) / 1000L);
    }

    public static final class StaminaRecovery {
        public final int stamina;
        public final long updatedAt;
        public final int secondsToNext;

        private StaminaRecovery(int stamina, long updatedAt, int secondsToNext) {
            this.stamina = stamina;
            this.updatedAt = updatedAt;
            this.secondsToNext = secondsToNext;
        }
    }
}
