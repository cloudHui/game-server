package game.arena.journey;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;

public class JourneyRulesTest {
    @Test
    public void rewardsUseMapAndRunCounts() {
        Map<String, Integer> rewards = JourneyRules.rewards(2, 3);
        assertEquals(Integer.valueOf(12), rewards.get("ore"));
        assertEquals(Integer.valueOf(3), rewards.get("star_dust"));
        assertEquals(18, JourneyRules.staminaCost(3));
    }

    @Test
    public void staminaRecoversOnePointEveryFiveMinutes() {
        JourneyRules.StaminaRecovery recovery = JourneyRules.recover(100, 1_000_000L, 1_750_000L);

        assertEquals(102, recovery.stamina);
        assertEquals(1_600_000L, recovery.updatedAt);
        assertEquals(150, recovery.secondsToNext);
    }

    @Test
    public void staminaRecoveryStopsAtCapWithoutBankingTime() {
        JourneyRules.StaminaRecovery recovery = JourneyRules.recover(119, 1_000_000L, 1_750_000L);

        assertEquals(JourneyRules.MAX_STAMINA, recovery.stamina);
        assertEquals(1_750_000L, recovery.updatedAt);
        assertEquals(0, recovery.secondsToNext);
    }

    @Test
    public void firstStaminaReadStartsTimerWithoutHistoricalRecovery() {
        JourneyRules.StaminaRecovery recovery = JourneyRules.recover(30, 0L, 1_750_000L);

        assertEquals(30, recovery.stamina);
        assertEquals(1_750_000L, recovery.updatedAt);
        assertEquals(300, recovery.secondsToNext);
    }
}
