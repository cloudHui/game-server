package com.cloud.hub.game.domain.poker.niuniu;

/**
 * 牛牛牌型枚举定义与基础倍数。
 * <p>
 * 等级由低到高排列：无牛(0) < 牛一~牛六(1~6) < 牛七~牛八(7~8) < 牛九(9) < 牛牛(10) <
 * 四花牛(11) < 五花牛(12) < 炸弹牛(13) < 五小牛(14)。
 *
 * @author cloud
 */
public enum NiuniuCardType {

    /** 无牛（没牛）：5张牌中任意3张都无法凑成10的倍数（1倍） */
    NO_NIU(0, "无牛", 1),

    /** 牛一：3张凑整，剩余2张和个位数为1（1倍） */
    NIU_1(1, "牛一", 1),

    /** 牛二：3张凑整，剩余2张和个位数为2（1倍） */
    NIU_2(2, "牛二", 1),

    /** 牛三：3张凑整，剩余2张和个位数为3（1倍） */
    NIU_3(3, "牛三", 1),

    /** 牛四：3张凑整，剩余2张和个位数为4（1倍） */
    NIU_4(4, "牛四", 1),

    /** 牛五：3张凑整，剩余2张和个位数为5（1倍） */
    NIU_5(5, "牛五", 1),

    /** 牛六：3张凑整，剩余2张和个位数为6（1倍） */
    NIU_6(6, "牛六", 1),

    /** 牛七：3张凑整，剩余2张和个位数为7（通常2倍） */
    NIU_7(7, "牛七", 2),

    /** 牛八：3张凑整，剩余2张和个位数为8（通常2倍） */
    NIU_8(8, "牛八", 2),

    /** 牛九：3张凑整，剩余2张和个位数为9（通常3倍） */
    NIU_9(9, "牛九", 3),

    /** 牛牛：3张凑整，剩余2张和也是10的倍数（通常4倍） */
    NIU_NIU(10, "牛牛", 4),

    /** 四花牛：4张J/Q/K + 1张10（银牛） */
    FOUR_FLOWER(11, "四花牛", 5),

    /** 五花牛：5张全部为J/Q/K（金牛） */
    FIVE_FLOWER(12, "五花牛", 6),

    /** 炸弹牛：4张牌面相同 */
    BOMB(13, "炸弹牛", 7),

    /** 五小牛：5张均<5且总和<=10 */
    FIVE_SMALL(14, "五小牛", 8);

    private final int level;
    private final String name;
    private final int defaultMultiplier;

    NiuniuCardType(int level, String name, int defaultMultiplier) {
        this.level = level;
        this.name = name;
        this.defaultMultiplier = defaultMultiplier;
    }

    public int getLevel() {
        return level;
    }

    public String getName() {
        return name;
    }

    public int getDefaultMultiplier() {
        return defaultMultiplier;
    }

    /**
     * 是否是有牛（牛一及以上）
     */
    public boolean hasNiu() {
        return this != NO_NIU;
    }

    /**
     * 是否是特殊大牌型（四花牛及以上）
     */
    public boolean isSpecial() {
        return this.level >= FOUR_FLOWER.level;
    }

    /**
     * 转换为 Protobuf CardType 枚举
     */
    public proto.ConstProto.CardType toProto() {
        switch (this) {
            case NIU_1: return proto.ConstProto.CardType.NIU_1;
            case NIU_2: return proto.ConstProto.CardType.NIU_2;
            case NIU_3: return proto.ConstProto.CardType.NIU_3;
            case NIU_4: return proto.ConstProto.CardType.NIU_4;
            case NIU_5: return proto.ConstProto.CardType.NIU_5;
            case NIU_6: return proto.ConstProto.CardType.NIU_6;
            case NIU_7: return proto.ConstProto.CardType.NIU_7;
            case NIU_8: return proto.ConstProto.CardType.NIU_8;
            case NIU_9: return proto.ConstProto.CardType.NIU_9;
            case NIU_NIU: return proto.ConstProto.CardType.NIU_NIU;
            case FOUR_FLOWER: return proto.ConstProto.CardType.NIU_FOUR_FLOWERS;
            case FIVE_FLOWER: return proto.ConstProto.CardType.NIU_FIVE_FLOWERS;
            case BOMB: return proto.ConstProto.CardType.NIU_BOMB;
            case FIVE_SMALL: return proto.ConstProto.CardType.NIU_FIVE_SMALL;
            default: return proto.ConstProto.CardType.NIU_NONE;
        }
    }

    /**
     * 从 Protobuf CardType 枚举转换
     */
    public static NiuniuCardType fromProto(proto.ConstProto.CardType protoType) {
        if (protoType == null) return NO_NIU;
        switch (protoType) {
            case NIU_1: return NIU_1;
            case NIU_2: return NIU_2;
            case NIU_3: return NIU_3;
            case NIU_4: return NIU_4;
            case NIU_5: return NIU_5;
            case NIU_6: return NIU_6;
            case NIU_7: return NIU_7;
            case NIU_8: return NIU_8;
            case NIU_9: return NIU_9;
            case NIU_NIU: return NIU_NIU;
            case NIU_FOUR_FLOWERS: return FOUR_FLOWER;
            case NIU_FIVE_FLOWERS: return FIVE_FLOWER;
            case NIU_BOMB: return BOMB;
            case NIU_FIVE_SMALL: return FIVE_SMALL;
            default: return NO_NIU;
        }
    }
}
