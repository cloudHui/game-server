package game.arena;
import java.util.Random;
/** 剑气除魔纯规则。无 HTTP、SQL、系统时间依赖。 */
public final class ArenaRules {
 private ArenaRules(){}
 public static long rankCost(int n){positive(n);return n*200L;} public static long skillCost(int n){positive(n);return n*150L;} public static int formationCost(int n){positive(n);return n*100;}
 public static Reward dungeonReward(int stage,boolean first){if(stage<1||stage>12)throw new IllegalArgumentException("副本层数非法");return new Reward((first?600L:180L)*stage,(first?120L:40L)*stage);}
 public static int starShardCost(int stars){if(stars<1||stars>=6)throw new IllegalArgumentException("星级非法");return 20+(stars-1)*15;}
 public static long dungeonPower(int stage){if(stage<1||stage>12)throw new IllegalArgumentException("副本层数非法");return stage*18000L;}
 public static long heroPower(long hp,long atk,long def,int rank,int stars,int skill){positive(rank);positive(stars);positive(skill);long base=hp/5+atk*8+def*5;return base+(rank-1)*900L+(stars-1)*2400L+(skill-1)*650L;}
 public static String drawQuality(int pity,Random r){if(pity>=90)return "金";if(r.nextInt(100)<2)return "金";if(r.nextInt(100)<20)return "红";return "橙";} public static int shards(String q){return "金".equals(q)?80:"红".equals(q)?40:20;}
 private static void positive(int n){if(n<1)throw new IllegalArgumentException("等级非法");} public static final class Reward{public final long liquid,coins;public Reward(long l,long c){liquid=l;coins=c;}}
}
