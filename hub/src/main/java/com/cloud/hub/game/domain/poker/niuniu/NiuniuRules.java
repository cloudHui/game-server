package com.cloud.hub.game.domain.poker.niuniu;

import com.cloud.hub.game.domain.cards.Card;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 牛牛核心规则计算引擎（纯函数、无状态计算器）。
 * <p>
 * 职责整合：
 * <ul>
 *   <li>手牌点数换算、特殊牌型（五小牛/炸弹/五花/四花）识别与最优 3+2 拼牛求解 (evaluate)；</li>
 *   <li>庄闲比牌裁决、牌型大小判定与单张点数/花色比对 (compare / isPlayerWin)。</li>
 * </ul>
 *
 * @author cloud
 */
public final class NiuniuRules implements Comparator<NiuniuHand> {

    public static final NiuniuRules INSTANCE = new NiuniuRules();

    /** C(5, 3) = 10 种三张牌组合下标索引表 */
    private static final int[][] COMBINATIONS_3 = {
            {0, 1, 2}, {0, 1, 3}, {0, 1, 4}, {0, 2, 3}, {0, 2, 4},
            {0, 3, 4}, {1, 2, 3}, {1, 2, 4}, {1, 3, 4}, {2, 3, 4}
    };

    private NiuniuRules() {
    }

    /**
     * 计算单张扑克在牛牛中的点数（A=1, 2~9按牌面, 10/J/Q/K 计 10 点）。
     */
    public static int getNiuPoint(Card card) {
        if (card == null) {
            return 0;
        }
        int val = card.getCardVal();
        if (val == 14) {
            return 1; // A 计 1 点
        }
        if (val == 15) {
            return 2; // 2 计 2 点
        }
        if (val >= 10 && val <= 13) {
            return 10; // 10, J(11), Q(12), K(13) 均计 10 点
        }
        if (val >= 16) {
            return 10; // 大小王作为普通大牌计 10 点
        }
        return val; // 3 ~ 9
    }

    /**
     * 计算单张牌在单牌比大小中的等级权重（A=1 最小，2=2，... 10=10，J=11，Q=12，K=13 最大，小王=14，大王=15）。
     */
    public static int getCardRank(Card card) {
        if (card == null) {
            return 0;
        }
        int val = card.getCardVal();
        if (val == 14) {
            return 1; // A 最小
        }
        if (val == 15) {
            return 2; // 2 次小
        }
        if (val >= 3 && val <= 13) {
            return val; // 3 ~ 10, J(11), Q(12), K(13)
        }
        if (val == 16) {
            return 14; // 小王
        }
        if (val == 17) {
            return 15; // 大王
        }
        return val;
    }

    /**
     * 获取牌的花色权重（黑桃 4 > 红桃 3 > 梅花 2 > 方块 1）。
     */
    public static int getCardSuitWeight(Card card) {
        if (card == null || card.getCardSuit() == null) {
            return 0;
        }
        return card.getCardSuit().getId();
    }

    /**
     * 比较两张单牌大小（先比点数权重，点数相同比花色权重）
     */
    public static int compareSingleCard(Card c1, Card c2) {
        if (c1 == c2) {
            return 0;
        }
        if (c1 == null) {
            return -1;
        }
        if (c2 == null) {
            return 1;
        }
        int r1 = getCardRank(c1);
        int r2 = getCardRank(c2);
        if (r1 != r2) {
            return Integer.compare(r1, r2);
        }
        return Integer.compare(getCardSuitWeight(c1), getCardSuitWeight(c2));
    }

    /**
     * 判定 5 张手牌的最终最大牛牛牌型组合。
     *
     * @param cards  5 张完整手牌
     * @param config 房间规则配置
     * @return 最佳牌型与分牌模型封装
     */
    public static NiuniuHand evaluate(List<Card> cards, NiuniuConfig config) {
        if (cards == null || cards.size() < 5) {
            throw new IllegalArgumentException("牛牛算牌必须恰好提供 5 张扑克牌");
        }

        // 寻找手牌中的绝对最大单张（用于同牌型比大小）
        Card maxCard = findMaxCard(cards);

        // 1. 五小牛检测（5张牌均小于5且总点数 <= 10）
        if (config.isEnableFiveSmall() && isFiveSmall(cards)) {
            return new NiuniuHand(NiuniuCardType.FIVE_SMALL, cards, Collections.emptyList(), maxCard);
        }

        // 2. 炸弹牛检测（4张牌点数相同）
        if (config.isEnableBomb() && isBomb(cards)) {
            Card bombCard = findBombCard(cards);
            return new NiuniuHand(NiuniuCardType.BOMB, cards, Collections.emptyList(), bombCard != null ? bombCard : maxCard);
        }

        // 3. 五花牛检测（5张牌全为 J/Q/K）
        if (config.isEnableFiveFlower() && isFiveFlower(cards)) {
            return new NiuniuHand(NiuniuCardType.FIVE_FLOWER, cards, Collections.emptyList(), maxCard);
        }

        // 4. 四花牛检测（4张 J/Q/K + 1张 10）
        if (config.isEnableFourFlower() && isFourFlower(cards)) {
            return new NiuniuHand(NiuniuCardType.FOUR_FLOWER, cards, Collections.emptyList(), maxCard);
        }

        // 5. 经典 3+2 组合寻优判定
        NiuniuCardType bestType = NiuniuCardType.NO_NIU;
        List<Card> bestBody = new ArrayList<>();
        List<Card> bestHead = new ArrayList<>();

        for (int[] combo : COMBINATIONS_3) {
            Card c1 = cards.get(combo[0]);
            Card c2 = cards.get(combo[1]);
            Card c3 = cards.get(combo[2]);

            int sum3 = getNiuPoint(c1) + getNiuPoint(c2) + getNiuPoint(c3);
            if (sum3 % 10 == 0) {
                // 找到牛身，提取剩余2张牛头
                List<Card> currentHead = new ArrayList<>(2);
                for (int i = 0; i < 5; i++) {
                    if (i != combo[0] && i != combo[1] && i != combo[2]) {
                        currentHead.add(cards.get(i));
                    }
                }
                int remainder = (getNiuPoint(currentHead.get(0)) + getNiuPoint(currentHead.get(1))) % 10;
                NiuniuCardType currentType = (remainder == 0) ? NiuniuCardType.NIU_NIU : getNiuTypeByRemainder(remainder);

                // 择优录取（选取等级更高的牌型）
                if (currentType.getLevel() > bestType.getLevel()) {
                    bestType = currentType;
                    bestBody = Arrays.asList(c1, c2, c3);
                    bestHead = currentHead;
                }
            }
        }

        if (bestType.hasNiu()) {
            return new NiuniuHand(bestType, bestBody, bestHead, maxCard);
        }

        // 6. 无牛保底
        return new NiuniuHand(NiuniuCardType.NO_NIU, Collections.emptyList(), cards, maxCard);
    }

    /**
     * 比较两手牌大小
     */
    @Override
    public int compare(NiuniuHand h1, NiuniuHand h2) {
        if (h1 == h2) {
            return 0;
        }
        if (h1 == null) {
            return -1;
        }
        if (h2 == null) {
            return 1;
        }

        // 1. 牌型等级比对
        int l1 = h1.getCardType().getLevel();
        int l2 = h2.getCardType().getLevel();
        if (l1 != l2) {
            return Integer.compare(l1, l2);
        }

        // 2. 同为炸弹牛：优先比较炸弹主牌点数
        if (h1.getCardType() == NiuniuCardType.BOMB) {
            int bombRank1 = getBombMainRank(h1);
            int bombRank2 = getBombMainRank(h2);
            if (bombRank1 != bombRank2) {
                return Integer.compare(bombRank1, bombRank2);
            }
        }

        // 3. 比较双方各自的最大单牌（先比点数，点数相同比花色）
        return compareSingleCard(h1.getMaxCard(), h2.getMaxCard());
    }

    /**
     * 判定闲家相对庄家是否获胜
     *
     * @param playerHand 闲家手牌
     * @param bankerHand 庄家手牌
     * @return true 闲家赢；false 闲家输（平手视同庄赢）
     */
    public static boolean isPlayerWin(NiuniuHand playerHand, NiuniuHand bankerHand) {
        return INSTANCE.compare(playerHand, bankerHand) > 0;
    }

    private static int getBombMainRank(NiuniuHand h) {
        Map<Integer, Integer> countMap = new HashMap<>();
        for (Card c : h.getAllCards()) {
            int rank = getCardRank(c);
            countMap.put(rank, countMap.getOrDefault(rank, 0) + 1);
        }
        for (Map.Entry<Integer, Integer> e : countMap.entrySet()) {
            if (e.getValue() >= 4) {
                return e.getKey();
            }
        }
        return 0;
    }

    /**
     * 找出 5 张牌中最大的单张牌（先比点数，点数相同比花色）
     */
    public static Card findMaxCard(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return null;
        }
        Card max = cards.get(0);
        for (int i = 1; i < cards.size(); i++) {
            Card current = cards.get(i);
            int rMax = getCardRank(max);
            int rCur = getCardRank(current);
            if (rCur > rMax) {
                max = current;
            } else if (rCur == rMax) {
                if (getCardSuitWeight(current) > getCardSuitWeight(max)) {
                    max = current;
                }
            }
        }
        return max;
    }

    private static boolean isFiveSmall(List<Card> cards) {
        int sum = 0;
        for (Card card : cards) {
            int pt = getNiuPoint(card);
            if (pt >= 5) {
                return false;
            }
            sum += pt;
        }
        return sum <= 10;
    }

    private static boolean isBomb(List<Card> cards) {
        Map<Integer, Integer> countMap = new HashMap<>();
        for (Card card : cards) {
            int rank = getCardRank(card);
            countMap.put(rank, countMap.getOrDefault(rank, 0) + 1);
        }
        for (int count : countMap.values()) {
            if (count >= 4) {
                return true;
            }
        }
        return false;
    }

    private static Card findBombCard(List<Card> cards) {
        Map<Integer, Integer> countMap = new HashMap<>();
        for (Card card : cards) {
            int rank = getCardRank(card);
            countMap.put(rank, countMap.getOrDefault(rank, 0) + 1);
        }
        for (Card card : cards) {
            int rank = getCardRank(card);
            if (countMap.getOrDefault(rank, 0) >= 4) {
                return card;
            }
        }
        return null;
    }

    private static boolean isFiveFlower(List<Card> cards) {
        for (Card card : cards) {
            int val = card.getCardVal();
            if (val < 11 || val > 13) {
                return false; // 非 J/Q/K
            }
        }
        return true;
    }

    private static boolean isFourFlower(List<Card> cards) {
        int jqkCount = 0;
        int tenCount = 0;
        for (Card card : cards) {
            int val = card.getCardVal();
            if (val >= 11 && val <= 13) {
                jqkCount++;
            } else if (val == 10) {
                tenCount++;
            } else {
                return false;
            }
        }
        return jqkCount == 4 && tenCount == 1;
    }

    private static NiuniuCardType getNiuTypeByRemainder(int remainder) {
        switch (remainder) {
            case 1: return NiuniuCardType.NIU_1;
            case 2: return NiuniuCardType.NIU_2;
            case 3: return NiuniuCardType.NIU_3;
            case 4: return NiuniuCardType.NIU_4;
            case 5: return NiuniuCardType.NIU_5;
            case 6: return NiuniuCardType.NIU_6;
            case 7: return NiuniuCardType.NIU_7;
            case 8: return NiuniuCardType.NIU_8;
            case 9: return NiuniuCardType.NIU_9;
            default: return NiuniuCardType.NIU_NIU;
        }
    }
}
