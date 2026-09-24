package com.cloud.hub.game.domain.card;

/**
 * 扑克牌常量定义类。
 * <p>
 * 统一管理扑克牌特殊点数（K、A、2、大小王）与空牌占位标识。
 *
 * @author cloud
 */
public class CardConst {

    /**
     * 空牌ID
     */
    public static final int NULL_VAL = 9999;
    /**
     * K的牌面值
     */
    public static final int K_VAL = 13;
    /**
     * A的牌面值
     */
    public static final int ACE_VAL = 14;

    /**
     * 2的牌面值
     */
    public static final int ER_VAL = 15;

    /**
     * 小王的牌面值
     */
    public static final int SMALL_JOKER_VAL = 16;

    /**
     * 大王的牌面值
     */
    public static final int BIG_JOKER_VAL = 17;
}
