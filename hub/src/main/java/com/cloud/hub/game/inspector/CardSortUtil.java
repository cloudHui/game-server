package com.cloud.hub.game.inspector;

import java.util.Comparator;
import java.util.List;

import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.poker.niuniu.NiuniuRules;

/**
 * 棋牌手牌与余牌排序工具类。
 * <p>
 * 提供与牌局客户端展示一致的理牌排序算法：
 * <ul>
 *   <li>扑克牌（斗地主、跑得快、拖拉机）：按点数降序（2 &gt; A &gt; K ...），同点数按黑红梅方降序；</li>
 *   <li>牛牛牌：按牛牛单张权重降序（K &gt; Q &gt; ... &gt; 2 &gt; A），同点数按黑红梅方降序；</li>
 *   <li>麻将牌：按牌 ID 升序（万、条、筒、风、箭）。</li>
 * </ul>
 * </p>
 */
public final class CardSortUtil {

    private CardSortUtil() {
    }

    /** 传统争上游扑克牌比较器：点数降序、同点数花色降序 */
    public static final Comparator<Card> POKER_COMPARATOR = (c1, c2) -> {
        if (c1 == c2) {
            return 0;
        }
        if (c1 == null || c2 == null) {
            return c1 == null ? 1 : -1;
        }
        return c1.getCardVal() != c2.getCardVal()
                ? c2.getCardVal() - c1.getCardVal()
                : c2.getId() - c1.getId();
    };

    /** 牛牛牌比较器：K 最大、A 最小，同点数黑桃压红桃 */
    public static final Comparator<Card> NIUNIU_COMPARATOR = (c1, c2) -> NiuniuRules.compareSingleCard(c2, c1);

    /** 麻将牌比较器：按牌 ID 升序排列 */
    public static final Comparator<Card> MAHJONG_COMPARATOR = (c1, c2) -> {
        if (c1 == c2) {
            return 0;
        }
        if (c1 == null || c2 == null) {
            return c1 == null ? 1 : -1;
        }
        return Integer.compare(c1.getId(), c2.getId());
    };

    /**
     * 针对指定玩法类型的手牌列表就地进行理牌排序。
     *
     * @param gameType 玩法类型（1: 麻将; 2: 斗地主; 3: 跑得快; 4: 拖拉机; 5: 牛牛）
     * @param cards    手牌列表
     */
    public static void sortCards(int gameType, List<Card> cards) {
        if (cards == null || cards.size() <= 1) {
            return;
        }
        if (gameType == 1) {
            cards.sort(MAHJONG_COMPARATOR);
        } else if (gameType == 5) {
            cards.sort(NIUNIU_COMPARATOR);
        } else {
            cards.sort(POKER_COMPARATOR);
        }
    }
}
