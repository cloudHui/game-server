package game.arena;
import org.junit.Test;import java.util.Random;import static org.junit.Assert.*;
public class ArenaRulesTest {
 @Test public void costsAndRewards(){assertEquals(600L,ArenaRules.rankCost(3));assertEquals(300L,ArenaRules.skillCost(2));ArenaRules.Reward r=ArenaRules.dungeonReward(2,true);assertEquals(1200L,r.liquid);assertEquals(240L,r.coins);}
 @Test public void pity(){assertEquals("金",ArenaRules.drawQuality(90,new Random(1)));assertEquals(80,ArenaRules.shards("金"));}
 @Test public void cultivationAndDungeonPowerUseStablePublicRules(){
  assertEquals(20,ArenaRules.starShardCost(1));
  assertEquals(35,ArenaRules.starShardCost(2));
  assertEquals(18000L,ArenaRules.dungeonPower(1));
  assertEquals(216000L,ArenaRules.dungeonPower(12));
  assertEquals(13040L,ArenaRules.heroPower(8500,1180,380,1,1,1));
 }
}
