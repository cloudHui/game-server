package game.arena.journey;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JourneyRules {
    private JourneyRules() {
    }

    public static final int MAX_STAMINA = 120;

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
}
