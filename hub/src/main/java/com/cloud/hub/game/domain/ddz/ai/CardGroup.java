package com.cloud.hub.game.domain.ddz.ai;

import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.ddz.DdzRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 拆牌后的一个出牌单元（不一定已是合法统一牌型，需经 {@link DdzRules#analyze} 解析）。
 * <p>
 * 封装了一组牌及这组牌的保留价值评分，用于 AI 评估手牌拆分方案优劣。
 *
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @since 1.0
 */
public final class CardGroup {

    /** 该组包含的扑克牌列表（不可变列表） */
    private final List<Card> cards;
    /** 该组牌的保留价值评分（分数越高说明牌力越强，尽量不拆） */
    private final int preserveScore;

    /**
     * 构造一个牌组单元。
     *
     * @param cards         包含的牌列表
     * @param preserveScore 该组牌的保留分
     */
    public CardGroup(List<Card> cards, int preserveScore) {
        this.cards = Collections.unmodifiableList(new ArrayList<>(cards));
        this.preserveScore = preserveScore;
    }

    /**
     * 获取牌组单元内的扑克牌列表。
     *
     * @return 不可变的扑克牌列表
     */
    public List<Card> getCards() {
        return cards;
    }

    /**
     * 获取该组牌的保留评分。
     *
     * @return 保留分分值
     */
    public int getPreserveScore() {
        return preserveScore;
    }
}
