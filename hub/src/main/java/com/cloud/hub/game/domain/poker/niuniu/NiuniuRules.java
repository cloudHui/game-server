package com.cloud.hub.game.domain.poker.niuniu;

import java.util.Comparator;
import java.util.List;

import com.cloud.hub.game.domain.cards.Card;

/**
 * 牛牛纯函数规则计算器：负责 5 张牌的特殊牌型识别、3+2 最优凑十求解与庄闲比牌。
 */
public final class NiuniuRules implements Comparator<NiuniuHand> {

    /** 全局无状态比牌器单例 */
    public static final NiuniuRules INSTANCE = new NiuniuRules();

    /** C(5, 3) = 10 种三张牛身下标组合 */
    private static final int[][] COMBINATIONS_3 = {
            {0, 1, 2}, {0, 1, 3}, {0, 1, 4}, {0, 2, 3}, {0, 2, 4},
            {0, 3, 4}, {1, 2, 3}, {1, 2, 4}, {1, 3, 4}, {2, 3, 4}
    };

    private NiuniuRules() {
    }

    /**
     * 计算单张扑克在牛牛中的凑十点数（A=1，2=2，3~9 按牌面，10/J/Q/K/王计 10 点）。
     *
     * @param card 扑克牌对象
     * @return 牛牛点数（0~10）
     */
    public static int getNiuPoint(Card card) {
        if (card == null) {
            return 0;
        }
        int val = card.getCardVal();
        if (val == 14) {
            return 1;
        }
        if (val == 15) {
            return 2;
        }
        return val >= 10 ? 10 : val;
    }

    /**
     * 计算单张牌在比大小中的点数权重（A=1 最小，2=2，3~K 为 3~13，小王=14，大王=15）。
     *
     * @param card 扑克牌对象
     * @return 点数比较权重
     */
    public static int getCardRank(Card card) {
        if (card == null) {
            return 0;
        }
        int val = card.getCardVal();
        if (val == 14) {
            return 1;
        }
        if (val == 15) {
            return 2;
        }
        return val >= 16 ? val - 2 : val;
    }

    /**
     * 获取牌的花色比较权重（黑桃 4 &gt; 红桃 3 &gt; 梅花 2 &gt; 方块 1）。
     *
     * @param card 扑克牌对象
     * @return 花色权重（0~4）
     */
    public static int getCardSuitWeight(Card card) {
        return (card == null || card.getCardSuit() == null) ? 0 : card.getCardSuit().getId();
    }

    /**
     * 比较两张单牌大小（先比点数权重，同点数再比花色权重）。
     *
     * @param c1 单牌 1
     * @param c2 单牌 2
     * @return 正数表示 c1 大，负数表示 c2 大，0 表示相等
     */
    public static int compareSingleCard(Card c1, Card c2) {
        if (c1 == c2) {
            return 0;
        }
        if (c1 == null || c2 == null) {
            return c1 == null ? -1 : 1;
        }
        int cmp = Integer.compare(getCardRank(c1), getCardRank(c2));
        return cmp != 0 ? cmp : Integer.compare(getCardSuitWeight(c1), getCardSuitWeight(c2));
    }

    /**
     * 找出牌组中点数与花色综合最大的单牌。
     *
     * @param cards 手牌列表
     * @return 最大单牌
     */
    public static Card findMaxCard(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        Card max = cards.get(0);
        for (int i = 1; i < cards.size(); i++) {
            if (compareSingleCard(cards.get(i), max) > 0) {
                max = cards.get(i);
            }
        }
        return max;
    }

    /**
     * 求解 5 张手牌的最优牛牛牌型。
     *
     * @param cards 5 张完整手牌
     * @return 最优手牌评估模型
     */
    public static NiuniuHand evaluate(List<Card> cards) {
        if (cards == null || cards.size() < 5) {
            throw new IllegalArgumentException("牛牛算牌必须提供 5 张扑克牌");
        }
        Card maxCard = findMaxCard(cards);

        int total = 0;
        boolean allUnderFive = true;
        for (Card card : cards) {
            int pt = getNiuPoint(card);
            total += pt;
            if (pt >= 5) {
                allUnderFive = false;
            }
        }
        if (allUnderFive && total <= 10) {
            return new NiuniuHand(cards, NiuniuCardType.FIVE_SMALL, maxCard);
        }

        // 炸弹牛直接以四条主牌作为代表牌，后续同牌型比大小无需二次统计炸弹点数
        Card quadCard = findQuadCard(cards);
        if (quadCard != null) {
            return new NiuniuHand(cards, NiuniuCardType.BOMB, quadCard);
        }

        NiuniuCardType flowerType = detectFlowerType(cards);
        if (flowerType != null) {
            return new NiuniuHand(cards, flowerType, maxCard);
        }

        // 任意 3 张凑成 10 的倍数时，其余 2 张牛头点数之和即为 total - sum3，无需内层循环创建临时集合
        int bestLevel = 0;
        for (int[] c : COMBINATIONS_3) {
            int sum3 = getNiuPoint(cards.get(c[0])) + getNiuPoint(cards.get(c[1])) + getNiuPoint(cards.get(c[2]));
            if (sum3 % 10 == 0) {
                int rem = (total - sum3) % 10;
                bestLevel = Math.max(bestLevel, rem == 0 ? 10 : rem);
            }
        }
        return new NiuniuHand(cards, NiuniuCardType.ofLevel(bestLevel), maxCard);
    }

    @Override
    public int compare(NiuniuHand h1, NiuniuHand h2) {
        if (h1 == h2) {
            return 0;
        }
        if (h1 == null || h2 == null) {
            return h1 == null ? -1 : 1;
        }
        int cmp = Integer.compare(h1.getCardType().getLevel(), h2.getCardType().getLevel());
        return cmp != 0 ? cmp : compareSingleCard(h1.getMaxCard(), h2.getMaxCard());
    }

    /**
     * 判定闲家相对庄家是否获胜（平手庄赢）。
     *
     * @param playerHand 闲家手牌
     * @param bankerHand 庄家手牌
     * @return true 表示闲家获胜
     */
    public static boolean isPlayerWin(NiuniuHand playerHand, NiuniuHand bankerHand) {
        return INSTANCE.compare(playerHand, bankerHand) > 0;
    }

    private static Card findQuadCard(List<Card> cards) {
        // 5 张牌若有四条，前 2 张中必至少包含 1 张四条主牌
        for (int i = 0; i < 2; i++) {
            int rank = getCardRank(cards.get(i));
            int same = 0;
            for (Card c : cards) {
                if (getCardRank(c) == rank) {
                    same++;
                }
            }
            if (same >= 4) {
                return cards.get(i);
            }
        }
        return null;
    }

    private static NiuniuCardType detectFlowerType(List<Card> cards) {
        int jqk = 0;
        int ten = 0;
        for (Card card : cards) {
            int val = card.getCardVal();
            if (val >= 11 && val <= 13) {
                jqk++;
            } else if (val == 10) {
                ten++;
            } else {
                return null;
            }
        }
        if (jqk == 5) {
            return NiuniuCardType.FIVE_FLOWER;
        }
        return (jqk == 4 && ten == 1) ? NiuniuCardType.FOUR_FLOWER : null;
    }
}
