package com.cloud.hub.game.domain.poker.niuniu;

import java.util.Collections;
import java.util.List;

import com.cloud.hub.game.domain.cards.Card;

/**
 * 玩家 5 张手牌的算牛评估结果模型。
 */
public class NiuniuHand {

    /** 5 张完整原始手牌列表（不可变视图） */
    private final List<Card> allCards;
    /** 最终认定的牛牛牌型 */
    private final NiuniuCardType cardType;
    /** 同牌型比大小使用的关键代表牌（普通牌型取最大单张，炸弹牛取四条主牌） */
    private final Card maxCard;

    /**
     * 构造不可变的牛牛手牌评估结果。
     *
     * @param allCards 5 张完整手牌
     * @param cardType 认定的牛牛牌型
     * @param maxCard  同牌型比大小使用的关键代表牌
     */
    public NiuniuHand(List<Card> allCards, NiuniuCardType cardType, Card maxCard) {
        this.allCards = allCards != null ? Collections.unmodifiableList(allCards) : Collections.emptyList();
        this.cardType = cardType;
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
