package com.cloud.hub.game.arena;

import java.util.Random;

/**
 * 剑气除魔纯规则。无 HTTP、SQL、系统时间依赖。
 */
public final class ArenaRules {
    private ArenaRules() {
    }

    public static long rankCost(int n) {
        positive(n);
        return n * 200L;
    }

    public static long skillCost(int n) {
        positive(n);
        return n * 150L;
    }

    public static int formationCost(int n) {
        positive(n);
        return n * 100;
    }

    public static Reward dungeonReward(int stage, boolean first) {
        if (stage < 1 || stage > 12) throw new IllegalArgumentException("副本层数非法");
        return new Reward((first ? 600L : 180L) * stage, (first ? 120L : 40L) * stage);
    }

    public static String drawQuality(int pity, Random r) {
        if (pity >= 90) return "金";
        if (r.nextInt(100) < 2) return "金";
        if (r.nextInt(100) < 20) return "红";
        return "橙";
    }

    public static int shards(String q) {
        return "金".equals(q) ? 80 : "红".equals(q) ? 40 : 20;
    }

    private static void positive(int n) {
        if (n < 1) throw new IllegalArgumentException("等级非法");
    }

    public static final class Reward {
        public final long liquid, coins;

        public Reward(long l, long c) {
            liquid = l;
            coins = c;
        }
    }
}
