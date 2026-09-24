package com.cloud.hub.game.inspector;

import com.cloud.hub.game.domain.cards.Card;

import java.util.Comparator;
import java.util.List;

/**
 * 棋牌手牌与余牌排序工具类。
 * <p>
 * 提供与牌局客户端展示完全一致的理牌排序算法：
 * <ul>
 *   <li>扑克牌（斗地主、跑得快、拖拉机）：按点数降序（大牌在左/前），点数相同时按黑红梅方降序；</li>
 *   <li>麻将牌：按花色升序（万1、条2、筒3、风4、箭5），同花色按点数升序（1~9）。</li>
 * </ul>
 * </p>
 *
 * @author cloud
 */
public final class CardSortUtil {

    private CardSortUtil() {
    }

    /**
     * 扑克牌比较器：点数降序、同点数花色降序（黑桃4 > 红桃3 > 梅花2 > 方块1）。
     */
    public static final Comparator<Card> POKER_COMPARATOR = (c1, c2) -> {
        if (c1 == null && c2 == null) return 0;
        if (c1 == null) return 1;
        if (c2 == null) return -1;
        if (c1.getCardVal() != c2.getCardVal()) {
            return c2.getCardVal() - c1.getCardVal();
        }
        return c2.getId() - c1.getId();
    };

    /**
     * 麻将牌比较器：按牌 ID 升序排列（101~109万, 201~209条, 301~309筒, 401~404风, 501~503箭）。
     */
    public static final Comparator<Card> MAHJONG_COMPARATOR = (c1, c2) -> {
        if (c1 == null && c2 == null) return 0;
        if (c1 == null) return 1;
        if (c2 == null) return -1;
        return Integer.compare(c1.getId(), c2.getId());
    };

    /**
     * 针对指定玩法类型的手牌列表就地进行理牌排序。
     *
     * @param gameType 玩法类型（1: 麻将; 2: 斗地主; 3: 跑得快; 4: 拖拉机）
     * @param cards    手牌列表
     */
    public static void sortCards(int gameType, List<Card> cards) {
        if (cards == null || cards.size() <= 1) {
            return;
        }
        if (gameType == 1) {
            cards.sort(MAHJONG_COMPARATOR);
        } else {
            cards.sort(POKER_COMPARATOR);
        }
    }
}
