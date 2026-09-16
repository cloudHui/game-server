package com.cloud.hub.game.arena;

import java.util.Random;

/**
 * 剑气除魔核心数值与规则引擎。
 * <p>
 * 纯算法与静态数值计算，零 SQL、零 HTTP、零外部依赖。
 * 覆盖无尽通天塔、敌方四系词缀、属性丹药服用上限、神兽进阶、离线挂机速率与每日俸禄等体系。
 */
public final class ArenaRules {
    /** 镇魔通天塔最高挑战层数 (999层) */
    public static final int MAX_TOWER_FLOORS = 999;

    /** 全服修士终身最多可服用属性丹总数上限 (50颗) */
    public static final int MAX_PILLS_TOTAL = 50;

    private ArenaRules() {}

    /**
     * 计算仙修境界突破所需灵液消耗。
     *
     * @param rank 当前境界阶数 (必须 >= 1)
     * @return 消耗的灵液数量
     */
    public static long rankCost(int rank) {
        positive(rank);
        return rank * 200L;
    }

    /**
     * 计算仙修功法升级所需灵币消耗。
     *
     * @param skillLevel 当前功法等级 (必须 >= 1)
     * @return 消耗的灵币数量
     */
    public static long skillCost(int skillLevel) {
        positive(skillLevel);
        return skillLevel * 150L;
    }

    /**
     * 计算战阵提升所需战阵石消耗。
     *
     * @param formationLevel 当前战阵等级 (必须 >= 1)
     * @return 消耗的战阵石数量
     */
    public static int formationCost(int formationLevel) {
        positive(formationLevel);
        return formationLevel * 100;
    }

    /**
     * 计算挑战镇魔通天塔单层产出的基础通关资源奖励。
     *
     * @param stage 挑战层数 (1 ~ 999)
     * @param first 是否为首通（首通奖励为常规平定奖励的 3.3 倍以上）
     * @return 包含灵液与灵币的奖励对象
     */
    public static Reward dungeonReward(int stage, boolean first) {
        checkFloor(stage);
        return new Reward((first ? 600L : 180L) * stage, (first ? 120L : 40L) * stage);
    }

    /**
     * 计算通天塔对应层数通关所需的推荐战力阈值。
     * 采用平滑递增指数曲线：1~12层新手线性递增，13~999层平滑指数抬升。
     *
     * @param stage 副本层数 (1 ~ 999)
     * @return 推荐达标战力数值
     */
    public static long dungeonRequiredPower(int stage) {
        checkFloor(stage);
        if (stage <= 12) return stage * 18000L;
        return (long) (216000L + (stage - 12) * 12000L + Math.pow(stage - 12, 1.82) * 150L);
    }

    /**
     * 根据通天塔层数计算敌方的特性词缀代码。
     * <ul>
     *   <li>HIGH_ATK: 狂暴雷霆 (超高攻击秒人，克制方法：沉默打断)</li>
     *   <li>HIGH_DEF: 金刚铁壁 (极高免伤防御，克制方法：高攻破甲)</li>
     *   <li>HIGH_HEAL: 枯木逢春 (巨额自愈回春，克制方法：沉默禁疗或吸血消耗)</li>
     *   <li>HIGH_RES: 太虚魔障 (高抗性霸体，克制方法：霸体高防减伤)</li>
     * </ul>
     *
     * @param stage 副本层数 (1 ~ 999)
     * @return 词缀代码字符串
     */
    public static String enemyArchetype(int stage) {
        checkFloor(stage);
        switch (stage % 4) {
            case 1: return "HIGH_ATK";
            case 2: return "HIGH_DEF";
            case 3: return "HIGH_HEAL";
            default: return "HIGH_RES";
        }
    }

    /**
     * 获取敌方特性词缀的中文名称及应对提示。
     *
     * @param archetype 词缀代码
     * @return 中文描述文本
     */
    public static String enemyArchetypeName(String archetype) {
        if ("HIGH_ATK".equals(archetype)) return "狂暴雷霆 (高攻蓄力秒杀)";
        if ("HIGH_DEF".equals(archetype)) return "金刚铁壁 (极高免伤减伤)";
        if ("HIGH_HEAL".equals(archetype)) return "枯木逢春 (每回合巨额回血)";
        return "太虚魔障 (高抗性霸体)";
    }

    /**
     * 生成通天塔每一层守关魔怪的动态称号与名称。
     *
     * @param stage 副本层数 (1 ~ 999)
     * @return 守关魔怪完整名称
     */
    public static String enemyName(int stage) {
        checkFloor(stage);
        String[] prefixes = {"赤焰", "寒潭", "阴煞", "九尾", "罗刹", "血海", "幽冥", "吞天", "九幽", "天魔", "鸿蒙"};
        String[] types = {"妖将", "魔帅", "鬼祖", "妖皇", "魔圣", "邪尊", "剑灵", "道主"};
        String prefix = prefixes[(stage - 1) % prefixes.length];
        String type = types[(stage - 1) % types.length];
        return "第" + stage + "层 · " + prefix + type;
    }

    /**
     * 计算首通指定通天塔层数掉落的属性丹等阶。
     *
     * @param stage 副本层数
     * @return 丹药等阶 (1: 洗髓丹, 2: 聚灵丹, 3: 九幽丹, 0: 无首通丹药掉落)
     */
    public static int firstClearPillTier(int stage) {
        if (stage <= 30 && stage % 3 == 0) return 1;
        if (stage > 30 && stage <= 90 && (stage - 30) % 6 == 0) return 2;
        if (stage > 90 && stage <= 240 && (stage - 90) % 10 == 0) return 3;
        return 0;
    }

    /**
     * 校验玩家当前是否可以继续服用指定等阶的属性丹。
     * 规则：总数不能超过 MAX_PILLS_TOTAL (50颗)，且单阶不能超过其特定上限 (1阶10颗、2阶10颗、3阶15颗、4阶15颗)。
     *
     * @param tier 准备服用的丹药等阶 (1 ~ 4)
     * @param t1   当前已服用的一阶丹药数量
     * @param t2   当前已服用的二阶丹药数量
     * @param t3   当前已服用的三阶丹药数量
     * @param t4   当前已服用的四阶丹药数量
     * @return true 表示符合规则允许服用，false 表示已满
     */
    public static boolean canConsumePill(int tier, int t1, int t2, int t3, int t4) {
        int total = t1 + t2 + t3 + t4;
        if (total >= MAX_PILLS_TOTAL) return false;
        switch (tier) {
            case 1: return t1 < 10;
            case 2: return t2 < 10;
            case 3: return t3 < 15;
            case 4: return t4 < 15;
            default: return false;
        }
    }

    /**
     * 获取单颗指定等阶属性丹永久赋予的属性加成值。
     *
     * @param tier 属性丹等阶 (1 ~ 4)
     * @return 属性数组: [气血 hp, 攻击 atk, 防御 def, 暴击率 crit%]
     */
    public static int[] pillStats(int tier) {
        switch (tier) {
            case 1: return new int[]{400, 40, 15, 0};
            case 2: return new int[]{800, 80, 30, 0};
            case 3: return new int[]{1500, 150, 60, 0};
            case 4: return new int[]{3000, 300, 120, 1};
            default: return new int[]{0, 0, 0, 0};
        }
    }

    /**
     * 计算仙修升星所需消耗的本命碎片阶梯。
     *
     * @param currentStars 当前星级 (1 ~ 5)
     * @return 升至下一星所需的碎片数量
     */
    public static int starShardCost(int currentStars) {
        if (currentStars < 1 || currentStars >= 6) throw new IllegalArgumentException("星级非法");
        switch (currentStars) {
            case 1: return 20;
            case 2: return 35;
            case 3: return 50;
            case 4: return 70;
            default: return 100;
        }
    }

    /**
     * 计算坐骑神兽进阶至下一阶所需的真身碎片数量。
     *
     * @param currentLevel 当前神兽阶位 (1 ~ 6+)
     * @return 进阶消耗碎片数量
     */
    public static int beastShardCost(int currentLevel) {
        positive(currentLevel);
        switch (currentLevel) {
            case 1: return 30;
            case 2: return 60;
            case 3: return 100;
            case 4: return 150;
            case 5: return 220;
            default: return 300 + (currentLevel - 6) * 100;
        }
    }

    /**
     * 计算坐骑神兽为全队战阵提供的伤害与属性总放大百分比。
     * 规则：每升 1 阶增幅 +5%。
     *
     * @param level 当前神兽阶位
     * @return 放大百分比数值 (例如 5 表示 +5%)
     */
    public static int beastFormationMultiplier(int level) {
        positive(level);
        return level * 5;
    }

    /**
     * 计算挑战万妖兽神塔层数获得的神兽碎片掉落数量。
     *
     * @param stage 兽神塔层数
     * @return 获得的神兽碎片数量
     */
    public static int beastTowerDrop(int stage) {
        positive(stage);
        return 5 + Math.min(30, stage * 2);
    }

    /**
     * 计算爬塔每日可领取的朝廷/宗门俸禄结算奖励。
     *
     * @param cleared 当前已平定的最高层数
     * @return 每日俸禄对象 (灵液、灵币、战阵石、仙缘)
     */
    public static DailySettlement dungeonDailyReward(int cleared) {
        if (cleared <= 0) return new DailySettlement(0, 0, 0, 0);
        long liquid = cleared * 300L;
        long coins = cleared * 150L;
        long stones = cleared * 25L;
        int fate = cleared >= 12 ? (cleared >= 100 ? 3 : 1) : 0;
        return new DailySettlement(liquid, coins, stones, fate);
    }

    /**
     * 计算修士刷塔演武等级升级所需的试炼经验。
     *
     * @param level 当前演武刷塔等级
     * @return 升至下一级所需经验
     */
    public static long grindExpForLevel(int level) {
        positive(level);
        return level * 800L;
    }

    /**
     * 根据通天塔通关层数计算每分钟离线/在线持续产出的挂机演武收益速率。
     *
     * @param clearedFloor 当前通关层数
     * @return 收益速率对象 (经验/分, 灵液/分, 灵币/分)
     */
    public static GrindRates grindRates(int clearedFloor) {
        int floor = Math.max(0, Math.min(MAX_TOWER_FLOORS, clearedFloor));
        long expPerMin = 20L + floor * 3L;
        long liquidPerMin = 30L + floor * 4L;
        long coinsPerMin = 15L + floor * 2L;
        return new GrindRates(expPerMin, liquidPerMin, coinsPerMin);
    }

    /**
     * 计算招募抽卡的品质结果与保底触发。
     *
     * @param pity 当前保底计数器 (>= 90 必出金)
     * @param r    随机数生成器
     * @return 品质标识 ("金", "红", "橙")
     */
    public static String drawQuality(int pity, Random r) {
        if (pity >= 90) return "金";
        if (r.nextInt(100) < 2) return "金";
        if (r.nextInt(100) < 20) return "红";
        return "橙";
    }

    /**
     * 抽取到重复仙侣时转化为仙侣碎片的数量。
     *
     * @param quality 品质
     * @return 转化碎片数量
     */
    public static int shards(String quality) {
        return "金".equals(quality) ? 80 : "红".equals(quality) ? 40 : 20;
    }

    private static void positive(int n) {
        if (n < 1) throw new IllegalArgumentException("数值非法，必须大于等于1");
    }

    private static void checkFloor(int stage) {
        if (stage < 1 || stage > MAX_TOWER_FLOORS) {
            throw new IllegalArgumentException("副本层数非法: " + stage);
        }
    }

    /** 副本通关奖励封装 */
    public static final class Reward {
        public final long liquid;
        public final long coins;

        public Reward(long liquid, long coins) {
            this.liquid = liquid;
            this.coins = coins;
        }
    }

    /** 每日俸禄结算封装 */
    public static final class DailySettlement {
        public final long liquid;
        public final long coins;
        public final long stones;
        public final int fate;

        public DailySettlement(long liquid, long coins, long stones, int fate) {
            this.liquid = liquid;
            this.coins = coins;
            this.stones = stones;
            this.fate = fate;
        }
    }

    /** 挂机演武收益速率封装 */
    public static final class GrindRates {
        public final long expPerMin;
        public final long liquidPerMin;
        public final long coinsPerMin;

        public GrindRates(long expPerMin, long liquidPerMin, long coinsPerMin) {
            this.expPerMin = expPerMin;
            this.liquidPerMin = liquidPerMin;
            this.coinsPerMin = coinsPerMin;
        }
    }
}
