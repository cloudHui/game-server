package com.cloud.hub.game.domain.poker.niuniu;

import com.cloud.hub.game.domain.cards.Card;

import java.util.Collections;
import java.util.List;

/**
 * 玩家 5 张手牌的领域运算模型。
 * <p>
 * 封装原始手牌、牌型结论、3张牛身（底牌）与2张牛头（余牌），
 * 以及用于同牌型比对的关键最大牌及花色。
 *
 * @author cloud
 */
public class NiuniuHand {

    /** 5 张完整手牌 */
    private final List<Card> allCards;

    /** 最终认定的牌型等级 */
    private final NiuniuCardType cardType;

    /** 凑成10倍数的3张牛身牌（无牛或特殊牌型时可为空或全体） */
    private final List<Card> bodyCards;

    /** 决定牛几的2张牛头余牌（无牛或特殊牌型时可为空） */
    private final List<Card> headCards;

    /** 5张牌中点数与花色综合最大的单牌（用于同牌型裁判比大小） */
    private final Card maxCard;

    public NiuniuHand(List<Card> allCards, NiuniuCardType cardType,
            List<Card> bodyCards, List<Card> headCards, Card maxCard) {
        this.allCards = allCards != null ? Collections.unmodifiableList(allCards) : Collections.emptyList();
        this.cardType = cardType;
        this.bodyCards = bodyCards != null ? Collections.unmodifiableList(bodyCards) : Collections.emptyList();
        this.headCards = headCards != null ? Collections.unmodifiableList(headCards) : Collections.emptyList();
        this.maxCard = maxCard;
    }

    public NiuniuHand(NiuniuCardType cardType, List<Card> bodyCards, List<Card> headCards, Card maxCard) {
        this(bodyCards, cardType, bodyCards, headCards, maxCard);
    }

    public List<Card> getAllCards() {
        return allCards;
    }

    public NiuniuCardType getCardType() {
        return cardType;
    }

    public List<Card> getBodyCards() {
        return bodyCards;
    }

    public List<Card> getHeadCards() {
        return headCards;
    }

    public Card getMaxCard() {
        return maxCard;
    }

    @Override
    public String toString() {
        return "NiuniuHand{" +
                "cardType=" + (cardType != null ? cardType.getName() : "null") +
                ", maxCard=" + maxCard +
                ", body=" + bodyCards +
                ", head=" + headCards +
                '}';
    }
}
