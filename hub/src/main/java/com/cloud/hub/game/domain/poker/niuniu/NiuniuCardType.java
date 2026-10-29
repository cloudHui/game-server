package com.cloud.hub.game.domain.poker.niuniu;

/**
 * 牛牛牌型等级与默认结算倍数枚举。
 * <p>
 * 等级由低到高（0~14）：无牛 &lt; 牛一~牛六 &lt; 牛七~牛八 &lt; 牛九 &lt; 牛牛 &lt; 四花牛 &lt; 五花牛 &lt; 炸弹牛 &lt; 五小牛。
 * </p>
 */
public enum NiuniuCardType {

    /** 无牛（1倍） */
    NO_NIU(0, "无牛", 1),
    /** 牛一（1倍） */
    NIU_1(1, "牛一", 1),
    /** 牛二（1倍） */
    NIU_2(2, "牛二", 1),
    /** 牛三（1倍） */
    NIU_3(3, "牛三", 1),
    /** 牛四（1倍） */
    NIU_4(4, "牛四", 1),
    /** 牛五（1倍） */
    NIU_5(5, "牛五", 1),
    /** 牛六（1倍） */
    NIU_6(6, "牛六", 1),
    /** 牛七（2倍） */
    NIU_7(7, "牛七", 2),
    /** 牛八（2倍） */
    NIU_8(8, "牛八", 2),
    /** 牛九（3倍） */
    NIU_9(9, "牛九", 3),
    /** 牛牛（4倍） */
    NIU_NIU(10, "牛牛", 4),
    /** 四花牛：4张 J/Q/K + 1张 10（5倍） */
    FOUR_FLOWER(11, "四花牛", 5),
    /** 五花牛：5张全为 J/Q/K（6倍） */
    FIVE_FLOWER(12, "五花牛", 6),
    /** 炸弹牛：4张同点数（7倍） */
    BOMB(13, "炸弹牛", 7),
    /** 五小牛：5张均小于5且总和不超过10（8倍） */
    FIVE_SMALL(14, "五小牛", 8);

    /** 按等级下标（0~14）顺序排列的枚举缓存，实现 O(1) 等级映射 */
    private static final NiuniuCardType[] BY_LEVEL = values();

    /** 牌型大小比较等级（取值范围 0~14） */
    private final int level;
    /** 牌型中文展示名称 */
    private final String name;
    /** 牌型默认结算赔率倍数（取值范围 1~8） */
    private final int defaultMultiplier;

    NiuniuCardType(int level, String name, int defaultMultiplier) {
        this.level = level;
        this.name = name;
        this.defaultMultiplier = defaultMultiplier;
    }

    /**
     * 按等级值获取对应牛牛牌型。
     *
     * @param level 牌型等级（0~14）
     * @return 对应牛牛牌型，越界时返回 {@link #NO_NIU}
     */
    public static NiuniuCardType ofLevel(int level) {
        return (level >= 0 && level < BY_LEVEL.length) ? BY_LEVEL[level] : NO_NIU;
    }

    /**
     * 获取牌型比较等级。
     *
     * @return 等级值（0~14）
     */
    public int getLevel() {
        return level;
    }

    /**
     * 获取牌型中文名称。
     *
     * @return 牌型名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取默认结算倍数。
     *
     * @return 倍数值（1~8）
     */
    public int getDefaultMultiplier() {
        return defaultMultiplier;
    }
}
