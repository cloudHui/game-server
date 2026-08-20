package game.arena.journey;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class JourneyRulesTest {
    @Test
    public void explorationCalculatesKnownRewardsAndStaminaCost() {
        Map<String, Integer> rewards = JourneyRules.rewards(2, 3);
        assertEquals(Integer.valueOf(12), rewards.get("ore"));
        assertEquals(Integer.valueOf(3), rewards.get("star_dust"));
        assertEquals(18, JourneyRules.staminaCost(3));
    }

    @Test(expected = IllegalArgumentException.class)
    public void explorationRejectsInvalidMap() {
        JourneyRules.rewards(7, 1);
    }
}
