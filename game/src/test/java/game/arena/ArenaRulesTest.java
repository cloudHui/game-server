package game.arena;
import org.junit.Test;import java.util.Random;import static org.junit.Assert.*;
public class ArenaRulesTest {@Test public void costsAndRewards(){assertEquals(600L,ArenaRules.rankCost(3));assertEquals(300L,ArenaRules.skillCost(2));ArenaRules.Reward r=ArenaRules.dungeonReward(2,true);assertEquals(1200L,r.liquid);assertEquals(240L,r.coins);}@Test public void pity(){assertEquals("金",ArenaRules.drawQuality(90,new Random(1)));assertEquals(80,ArenaRules.shards("金"));}}
