package com.cloud.game.manager.table.ddz.ai;

import com.cloud.game.manager.table.cards.Card;
import com.cloud.game.manager.table.ddz.DdzRules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 拆牌后的一个出牌单元（不一定已是合法统一牌型，需经 {@link DdzRules#analyze}）。
 *
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @since 1.0
 */
public final class CardGroup {

    private final List<Card> cards;
    private final int preserveScore;

    public CardGroup(List<Card> cards, int preserveScore) {
        this.cards = Collections.unmodifiableList(new ArrayList<>(cards));
        this.preserveScore = preserveScore;
    }

    public List<Card> getCards() {
        return cards;
    }

    public int getPreserveScore() {
        return preserveScore;
    }
}
