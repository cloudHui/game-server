package com.cloud.hub.game.domain.poker.niuniu;

import java.util.Collections;
import java.util.List;

import com.cloud.hub.game.domain.cards.Card;

/**
 * 玩家 5 张手牌的算牛评估结果模型。
 */
public class NiuniuHand {

    /** 5 张完整原始手牌列表 */
    private final List<Card> allCards;
    /** 最终认定的牛牛牌型 */
    private final NiuniuCardType cardType;
    /** 凑成 10 倍数的 3 张牛身牌（无牛时为空，特殊牌型时为全部 5 张） */
    private final List<Card> bodyCards;
    /** 决定牛几的 2 张牛头余牌（特殊牌型时为空，无牛时为全部 5 张） */
    private final List<Card> headCards;
    /** 同牌型比大小使用的关键代表牌（普通牌型取最大单张，炸弹牛取四条主牌） */
    private final Card maxCard;

    /**
     * 构造不可变的牛牛手牌评估结果。
     *
     * @param allCards  5 张完整手牌
     * @param cardType  认定的牛牛牌型
     * @param bodyCards 牛身牌列表
     * @param headCards 牛头牌列表
     * @param maxCard   同牌型比大小使用的关键代表牌
     */
    public NiuniuHand(List<Card> allCards, NiuniuCardType cardType,
            List<Card> bodyCards, List<Card> headCards, Card maxCard) {
        this.allCards = allCards != null ? Collections.unmodifiableList(allCards) : Collections.emptyList();
        this.cardType = cardType;
        this.bodyCards = bodyCards != null ? Collections.unmodifiableList(bodyCards) : Collections.emptyList();
        this.headCards = headCards != null ? Collections.unmodifiableList(headCards) : Collections.emptyList();
        this.maxCard = maxCard;
    }

    /**
     * 获取全部 5 张完整手牌。
     *
     * @return 不可变完整手牌列表
     */
    public List<Card> getAllCards() {
        return allCards;
    }

    /**
     * 获取最终牌型枚举。
     *
     * @return 牛牛牌型
     */
    public NiuniuCardType getCardType() {
        return cardType;
    }

    /**
     * 获取凑十牛身牌列表。
     *
     * @return 牛身牌列表
     */
    public List<Card> getBodyCards() {
        return bodyCards;
    }

    /**
     * 获取牛头余牌列表。
     *
     * @return 牛头牌列表
     */
    public List<Card> getHeadCards() {
        return headCards;
    }

    /**
     * 获取同牌型比大小的关键代表牌。
     *
     * @return 代表单牌
     */
    public Card getMaxCard() {
        return maxCard;
    }

    @Override
    public String toString() {
        return "NiuniuHand{type=" + (cardType != null ? cardType.getName() : "null") + ", max=" + maxCard + '}';
    }
}
