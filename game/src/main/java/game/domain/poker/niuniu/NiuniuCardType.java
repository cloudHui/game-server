package game.domain.poker.niuniu;

import proto.ConstProto;

/**
 * 牛牛牌型等级、默认赔率倍数与协议枚举映射。
 * <p>
 * 等级由低到高（0~14）：无牛 &lt; 牛一~牛六 &lt; 牛七~牛八 &lt; 牛九 &lt; 牛牛 &lt; 四花牛 &lt; 五花牛 &lt; 炸弹牛 &lt; 五小牛。
 * </p>
 */
public enum NiuniuCardType {

    /** 无牛（1倍） */
    NO_NIU(0, "无牛", 1, ConstProto.CardType.NIU_NONE),
    /** 牛一（1倍） */
    NIU_1(1, "牛一", 1, ConstProto.CardType.NIU_1),
    /** 牛二（1倍） */
    NIU_2(2, "牛二", 1, ConstProto.CardType.NIU_2),
    /** 牛三（1倍） */
    NIU_3(3, "牛三", 1, ConstProto.CardType.NIU_3),
    /** 牛四（1倍） */
    NIU_4(4, "牛四", 1, ConstProto.CardType.NIU_4),
    /** 牛五（1倍） */
    NIU_5(5, "牛五", 1, ConstProto.CardType.NIU_5),
    /** 牛六（1倍） */
    NIU_6(6, "牛六", 1, ConstProto.CardType.NIU_6),
    /** 牛七（2倍） */
    NIU_7(7, "牛七", 2, ConstProto.CardType.NIU_7),
    /** 牛八（2倍） */
    NIU_8(8, "牛八", 2, ConstProto.CardType.NIU_8),
    /** 牛九（3倍） */
    NIU_9(9, "牛九", 3, ConstProto.CardType.NIU_9),
    /** 牛牛（4倍） */
    NIU_NIU(10, "牛牛", 4, ConstProto.CardType.NIU_NIU),
    /** 四花牛：4张 J/Q/K + 1张 10（5倍） */
    FOUR_FLOWER(11, "四花牛", 5, ConstProto.CardType.NIU_FOUR_FLOWERS),
    /** 五花牛：5张全为 J/Q/K（6倍） */
    FIVE_FLOWER(12, "五花牛", 6, ConstProto.CardType.NIU_FIVE_FLOWERS),
    /** 炸弹牛：4张同点数（7倍） */
    BOMB(13, "炸弹牛", 7, ConstProto.CardType.NIU_BOMB),
    /** 五小牛：5张均小于5且总和不超过10（8倍） */
    FIVE_SMALL(14, "五小牛", 8, ConstProto.CardType.NIU_FIVE_SMALL);

    /** 按等级下标（0~14）顺序排列的枚举缓存，避免重复创建数组与冗长 switch */
    private static final NiuniuCardType[] BY_LEVEL = values();

    /** 牌型大小比较等级（0~14） */
    private final int level;
    /** 牌型中文展示名称 */
    private final String name;
    /** 牌型默认结算倍数 */
    private final int defaultMultiplier;
    /** 对应的网络协议牌型枚举 */
    private final ConstProto.CardType protoType;

    NiuniuCardType(int level, String name, int defaultMultiplier, ConstProto.CardType protoType) {
        this.level = level;
        this.name = name;
        this.defaultMultiplier = defaultMultiplier;
        this.protoType = protoType;
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
     * 从协议牌型枚举反查领域牌型。
     *
     * @param protoType Protobuf 牌型枚举
     * @return 对应领域牌型，未匹配时返回 {@link #NO_NIU}
     */
    public static NiuniuCardType fromProto(ConstProto.CardType protoType) {
        if (protoType == null) {
            return NO_NIU;
        }
        for (NiuniuCardType type : BY_LEVEL) {
            if (type.protoType == protoType) {
                return type;
            }
        }
        return NO_NIU;
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
     * @return 倍数值
     */
    public int getDefaultMultiplier() {
        return defaultMultiplier;
    }

    /**
     * 判断是否成牛（牛一及以上）。
     *
     * @return true 表示成牛
     */
    public boolean hasNiu() {
        return this != NO_NIU;
    }

    /**
     * 转换为协议牌型枚举。
     *
     * @return Protobuf 牌型枚举
     */
    public ConstProto.CardType toProto() {
        return protoType;
    }
}
