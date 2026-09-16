package com.cloud.hub.game.arena;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArenaRulesExtendedTest {

    @Test
    public void testTowerScaleAndArchetypes() {
        assertEquals(999, ArenaRules.MAX_TOWER_FLOORS);
        assertTrue(ArenaRules.dungeonRequiredPower(1) > 0);
        assertTrue(ArenaRules.dungeonRequiredPower(999) > ArenaRules.dungeonRequiredPower(100));

        // 验证四系特性词缀循环
        assertEquals("HIGH_ATK", ArenaRules.enemyArchetype(1));
        assertEquals("HIGH_DEF", ArenaRules.enemyArchetype(2));
        assertEquals("HIGH_HEAL", ArenaRules.enemyArchetype(3));
        assertEquals("HIGH_RES", ArenaRules.enemyArchetype(4));
        assertEquals("HIGH_ATK", ArenaRules.enemyArchetype(5));

        assertNotNull(ArenaRules.enemyArchetypeName("HIGH_ATK"));
        assertNotNull(ArenaRules.enemyName(50));
    }

    @Test
    public void testPillSystem() {
        assertEquals(50, ArenaRules.MAX_PILLS_TOTAL);

        // 验证各阶上限: 10, 10, 15, 15
        assertTrue(ArenaRules.canConsumePill(1, 0, 0, 0, 0));
        assertFalse(ArenaRules.canConsumePill(1, 10, 0, 0, 0));

        assertTrue(ArenaRules.canConsumePill(2, 5, 0, 0, 0));
        assertFalse(ArenaRules.canConsumePill(2, 5, 10, 0, 0));

        assertTrue(ArenaRules.canConsumePill(3, 10, 10, 0, 0));
        assertFalse(ArenaRules.canConsumePill(3, 10, 10, 15, 0));

        // 总上限 50 拦截
        assertFalse(ArenaRules.canConsumePill(4, 10, 10, 15, 15));

        // 属性增加验证
        int[] s1 = ArenaRules.pillStats(1);
        assertEquals(400, s1[0]);
        assertEquals(40, s1[1]);

        int[] s4 = ArenaRules.pillStats(4);
        assertEquals(3000, s4[0]);
        assertEquals(1, s4[3]); // 1% 暴击
    }

    @Test
    public void testBeastAndTowerDrop() {
        assertEquals(30, ArenaRules.beastShardCost(1));
        assertEquals(60, ArenaRules.beastShardCost(2));
        assertEquals(100, ArenaRules.beastShardCost(3));
        assertEquals(150, ArenaRules.beastShardCost(4));
        assertEquals(220, ArenaRules.beastShardCost(5));

        // 战阵放大每级 5%
        assertEquals(5, ArenaRules.beastFormationMultiplier(1));
        assertEquals(30, ArenaRules.beastFormationMultiplier(6));

        assertTrue(ArenaRules.beastTowerDrop(1) >= 5);
        assertTrue(ArenaRules.beastTowerDrop(10) > ArenaRules.beastTowerDrop(1));
    }

    @Test
    public void testDailySettlementAndGrind() {
        ArenaRules.DailySettlement ds0 = ArenaRules.dungeonDailyReward(0);
        assertEquals(0, ds0.liquid);

        ArenaRules.DailySettlement ds12 = ArenaRules.dungeonDailyReward(12);
        assertEquals(3600L, ds12.liquid);
        assertEquals(1800L, ds12.coins);
        assertEquals(300L, ds12.stones);
        assertEquals(1, ds12.fate);

        ArenaRules.GrindRates gr = ArenaRules.grindRates(10);
        assertEquals(50L, gr.expPerMin);
        assertEquals(70L, gr.liquidPerMin);
        assertEquals(35L, gr.coinsPerMin);

        assertEquals(800L, ArenaRules.grindExpForLevel(1));
        assertEquals(1600L, ArenaRules.grindExpForLevel(2));
    }
}
