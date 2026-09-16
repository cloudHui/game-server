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
}
