package com.cloud.hub.game.arena;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArenaBattleEngineTest {

    private ArenaBattleEngine.Hero hero(String id, long hp, long atk, long def, long spd, int mult, int stun, int steal) {
        return new ArenaBattleEngine.Hero(id, id + "名", "金", "强攻", hp, atk, def, spd, "破空斩", mult, stun, steal);
    }

    @Test
    public void testDeterministicBattleSimulation() {
        ArenaBattleEngine.Hero a = hero("a1", 10000, 1000, 300, 500, 200, 10, 0);
        ArenaBattleEngine.Hero d = hero("d1", 8000, 800, 200, 400, 150, 0, 0);

        ArenaBattleEngine.BattleResult r1 = ArenaBattleEngine.simulate(a, d, "pierce", 1, 12345L);
        ArenaBattleEngine.BattleResult r2 = ArenaBattleEngine.simulate(a, d, "pierce", 1, 12345L);

        assertEquals(r1.winner, r2.winner);
        assertEquals(r1.events.size(), r2.events.size());
        assertFalse(r1.events.isEmpty());
        assertEquals("BATTLE_START", r1.events.get(0).get("type"));
        assertEquals("BATTLE_END", r1.events.get(r1.events.size() - 1).get("type"));
    }

    @Test
    public void testSkillsAndArchetypesIntegration() {
        ArenaBattleEngine.Hero a = hero("a2", 15000, 1500, 400, 600, 200, 20, 10);
        ArenaBattleEngine.Hero d = hero("d2", 12000, 1200, 500, 300, 180, 0, 0);

        // 验证破甲流 (pierce)
        ArenaBattleEngine.BattleResult resPierce = ArenaBattleEngine.simulate(a, d, "pierce", 2, 42L);
        assertNotNull(resPierce.winner);

        // 验证沉默流 (silence)
        ArenaBattleEngine.BattleResult resSilence = ArenaBattleEngine.simulate(a, d, "silence", 1, 42L);
        assertNotNull(resSilence.winner);

        // 验证霸体流 (defense)
        ArenaBattleEngine.BattleResult resDefense = ArenaBattleEngine.simulate(a, d, "defense", 4, 42L);
        assertNotNull(resDefense.winner);

        // 验证吸血流 (heal)
        ArenaBattleEngine.BattleResult resHeal = ArenaBattleEngine.simulate(a, d, "heal", 3, 42L);
        assertNotNull(resHeal.winner);
    }

    @Test
    public void testTeamBattleSimulation() {
        java.util.List<ArenaBattleEngine.Hero> attackers = java.util.Arrays.asList(
                new ArenaBattleEngine.Hero("jianhuang", "无极剑皇", "金", "强攻", 8500, 1180, 380, 610, "万剑归宗", 260, 15, 20),
                new ArenaBattleEngine.Hero("yaohuang", "长生药皇", "金", "坚守", 9800, 700, 500, 420, "万古长春", 150, 9, 0),
                new ArenaBattleEngine.Hero("leizun", "九霄雷尊", "金", "控制", 7600, 960, 360, 590, "神霄万劫", 230, 28, 15)
        );

        java.util.List<ArenaBattleEngine.Hero> defenders = java.util.Arrays.asList(
                new ArenaBattleEngine.Hero("mingwang", "不动明王", "橙", "坚守", 7800, 500, 430, 320, "明王净世", 135, 16, 0),
                new ArenaBattleEngine.Hero("taixu", "太虚剑主", "红", "强攻", 6500, 820, 300, 520, "一剑破万法", 210, 18, 10),
                new ArenaBattleEngine.Hero("kongming", "空明道祖", "金", "控制", 8000, 900, 400, 650, "六道禁法", 180, 38, 0)
        );

        ArenaBattleEngine.BattleResult result = ArenaBattleEngine.simulateTeam(attackers, defenders, "pierce", 0, 9999L);
        assertNotNull(result);
        assertNotNull(result.winner);
        assertNotNull(result.synergies);
        assertTrue("应激活战阵羁绊", result.synergies.size() >= 2);
        assertFalse("对战事件流不应为空", result.events.isEmpty());
        assertEquals("BATTLE_START", result.events.get(0).get("type"));
        assertEquals("BATTLE_END", result.events.get(result.events.size() - 1).get("type"));
    }
}
