package com.cloud.hub.game.domain.ddz;

import com.cloud.hub.game.domain.card.CardConst;
import com.cloud.hub.game.domain.cards.Card;
import proto.ConstProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 斗地主出牌规则：识别牌型、比大小。
 * <p>
 * 支持：单张、对子、三张、三带一、三带二、顺子(≥5)、连对(≥3对)、飞机带单、飞机带对、炸弹、王炸。
 * 飞机机身为点数 3～A 的连续若干组三张（每组恰好取三张），不带 2 与王；比较时同类飞机须段数相同，比机身最大点数。
 */
public final class DdzRules {

    private DdzRules() {
    }

    /**
     * 识别一组扑克牌并解析为合法的斗地主牌型。
     *
     * @param cards 待分析的手牌列表
     * @return 若能构成合法牌型则返回对应的 {@link DdzHand}，否则返回 {@link Optional#empty()}
     */
    public static Optional<DdzHand> analyze(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return Optional.empty();
        }
        List<Card> sorted = new ArrayList<>(cards);
        sorted.sort(Collections.reverseOrder());

        int n = sorted.size();
        Map<Integer, Long> cnt = sorted.stream()
                .collect(Collectors.groupingBy(Card::getCardVal, TreeMap::new, Collectors.counting()));

        // 1. 王炸
        if (n == 2 && isRocket(sorted.get(0), sorted.get(1))) {
            return Optional.of(new DdzHand(ConstProto.CardType.BOOM_MAX, sorted, true, false, 10000, 0));
        }

        // 2. 炸弹与单张/对子/三张
        Optional<DdzHand> bomb = tryBomb(sorted, cnt);
        if (bomb.isPresent()) return bomb;

        Optional<DdzHand> singleGroup = trySingleOrPairs(sorted, cnt, n);
        if (singleGroup.isPresent()) return singleGroup;

        // 3. 顺子与连对
        Optional<DdzHand> straight = tryStraight(sorted, cnt);
        if (straight.isPresent()) return straight;

        Optional<DdzHand> straightPair = tryStraightPairs(sorted, cnt);
        if (straightPair.isPresent()) return straightPair;

        // 4. 飞机与三带
        return tryPlanesAndTriples(sorted, cnt);
    }

    /** 检查两张牌是否构成双王王炸火箭 */
    private static boolean isRocket(Card a, Card b) {
        return (a.isSmallJoker() && b.isBigJoker()) || (a.isBigJoker() && b.isSmallJoker());
    }

    /** 尝试单张、对子、三张单牌型识别 */
    private static Optional<DdzHand> trySingleOrPairs(List<Card> sorted, Map<Integer, Long> cnt, int n) {
        if (cnt.size() == 1) {
            int v = sorted.get(0).getCardVal();
            if (n == 1) {
                return Optional.of(new DdzHand(ConstProto.CardType.SINGLE, sorted, false, false, singleStrength(v), 0));
            }
            if (n == 2) {
                return Optional.of(new DdzHand(ConstProto.CardType.DOUBLE, sorted, false, false, pairStrength(v), 0));
            }
            if (n == 3) {
                return Optional.of(new DdzHand(ConstProto.CardType.TRIPLE, sorted, false, false, tripleStrength(v), 0));
            }
        }
        return Optional.empty();
    }

    /** 尝试飞机带对、飞机带单、三带一与三带二复合牌型 */
    private static Optional<DdzHand> tryPlanesAndTriples(List<Card> sorted, Map<Integer, Long> cnt) {
        Optional<DdzHand> planeDouble = tryPlaneDouble(cnt, sorted);
        if (planeDouble.isPresent()) return planeDouble;

        Optional<DdzHand> planeOne = tryPlaneOne(cnt, sorted);
        if (planeOne.isPresent()) return planeOne;

        Optional<DdzHand> t1 = tryTripleSingle(cnt, sorted);
        if (t1.isPresent()) return t1;

        return tryTriplePair(cnt, sorted);
    }

    /**
     * 判断当前出牌是否大于上一手桌面牌（压制规则）。
     *
     * @param incoming 当前待出的牌型
     * @param last     上一手桌面存留牌型
     * @return 若能成功大过上一手牌则返回 true
     */
    public static boolean beats(DdzHand incoming, DdzHand last) {
        if (incoming == null) return false;
        if (incoming.isRocket()) return true;
        if (last == null || last.getCards().isEmpty()) return true;
        if (last.isRocket()) return false;

        // 炸弹对抗普通牌与其他炸弹
        if (incoming.isBomb()) {
            return !last.isBomb() || incoming.getStrengthKey() > last.getStrengthKey();
        }
        if (last.isBomb() || incoming.getType() != last.getType()) {
            return false;
        }

        return beatsSameType(incoming, last);
    }

    /** 同牌型之间的长度与数值比较 */
    private static boolean beatsSameType(DdzHand incoming, DdzHand last) {
        ConstProto.CardType type = incoming.getType();
        // 顺子、连对或飞机必须要求长度与组数完全一致
        if (type == ConstProto.CardType.STRAIGHT || type == ConstProto.CardType.STRAIGHT_DOUBLE
                || type == ConstProto.CardType.PLANE_ONE || type == ConstProto.CardType.PLANE_DOUBLE) {
            if (incoming.getStraightLen() != last.getStraightLen()) {
                return false;
            }
        }
        return incoming.getStrengthKey() > last.getStrengthKey();
    }

    /**
     * 尝试炸弹
     *
     * @param sorted 排序后的牌
     * @param cnt    牌的计数
     * @return 炸弹
     */
    private static Optional<DdzHand> tryBomb(List<Card> sorted, Map<Integer, Long> cnt) {
        if (sorted.size() != 4) {
            return Optional.empty();
        }
        if (cnt.size() != 1) {
            return Optional.empty();
        }
        Map.Entry<Integer, Long> e = cnt.entrySet().iterator().next();
        if (e.getValue() != 4) {
            return Optional.empty();
        }
        int v = e.getKey();
        return Optional.of(new DdzHand(ConstProto.CardType.BOOM, sorted, false, true, bombStrength(v), 0));
    }

    /**
     * 尝试顺子
     *
     * @param sorted 排序后的牌
     * @param cnt    牌的计数
     * @return 顺子
     */
    private static Optional<DdzHand> tryStraight(List<Card> sorted, Map<Integer, Long> cnt) {
        int n = sorted.size();
        if (n < 5) {// 顺子最少5张
            return Optional.empty();
        }
        for (Long c : cnt.values()) {
            // 顺子每张牌只能出现一次
            if (c != 1) {
                return Optional.empty();
            }
        }
        // 顺子每张牌的点数
        List<Integer> ranks = new ArrayList<>(cnt.keySet());
        Collections.sort(ranks);
        for (int r : ranks) {
            // 顺子每张牌的点数必须合法
            if (!isStraightEligibleRank(r)) {
                return Optional.empty();
            }
        }
        // 顺子每张牌的点数必须连续
        for (int i = 1; i < ranks.size(); i++) {
            if (ranks.get(i) != ranks.get(i - 1) + 1) {
                return Optional.empty();
            }
        }
        // 顺子最大点数
        int top = ranks.get(ranks.size() - 1);
        return Optional.of(new DdzHand(ConstProto.CardType.STRAIGHT, sorted, false, false, straightStrength(top), n));
    }

    /**
     * 尝试连对
     *
     * @param sorted 排序后的牌
     * @param cnt    牌的计数
     * @return 连对
     */
    private static Optional<DdzHand> tryStraightPairs(List<Card> sorted, Map<Integer, Long> cnt) {
        int n = sorted.size();
        if (n < 6 || n % 2 != 0) {// 连对最少6张
            return Optional.empty();
        }
        for (Long c : cnt.values()) {
            // 连对每张牌只能出现两次
            if (c != 2) {
                return Optional.empty();
            }
        }
        int pairs = n / 2;
        if (pairs < 3) {// 连对最少3对
            return Optional.empty();
        }
        // 连对每张牌的点数
        List<Integer> ranks = new ArrayList<>(cnt.keySet());
        Collections.sort(ranks);
        for (int r : ranks) {
            // 连对每张牌的点数必须合法
            if (!isStraightEligibleRank(r)) {
                return Optional.empty();
            }
        }
        // 连对每张牌的点数必须连续
        for (int i = 1; i < ranks.size(); i++) {
            if (ranks.get(i) != ranks.get(i - 1) + 1) {
                return Optional.empty();
            }
        }
        // 连对最大点数
        int top = ranks.get(ranks.size() - 1);
        return Optional.of(
                new DdzHand(ConstProto.CardType.STRAIGHT_DOUBLE, sorted, false, false, straightStrength(top), pairs));
    }

    /**
     * 飞机带对：k 组连续三张（3～A）+ k 对翅膀，共 5k 张，k≥2。
     *
     * @param cnt    牌的计数
     * @param sorted 排序后的牌
     * @return 飞机带对
     */
    private static Optional<DdzHand> tryPlaneDouble(Map<Integer, Long> cnt, List<Card> sorted) {
        int n = sorted.size();
        if (n < 10 || n % 5 != 0) {// 飞机带对最少10张
            return Optional.empty();
        }
        int k = n / 5;
        if (k < 2) {// 飞机带对最少2组
            return Optional.empty();
        }
        for (int start = 3; start <= 14 - k + 1; start++) {
            // 飞机带对每组机身
            Map<Integer, Long> rem = remainderAfterPlaneBody(cnt, start, k);
            if (rem == null) {
                continue;
            }
            // 飞机带对每组翅膀
            if (!planeDoubleWingsValid(rem, k)) {
                continue;
            }
            // 飞机带对最大点数
            int topBodyRank = start + k - 1;
            return Optional.of(new DdzHand(ConstProto.CardType.PLANE_DOUBLE, sorted, false, false,
                    tripleStrength(topBodyRank), k));
        }
        return Optional.empty();
    }

    /**
     * 飞机带单：k 组连续三张（3～A）+ k 张单翅膀，共 4k 张，k≥2。
     *
     * @param cnt    牌的计数
     * @param sorted 排序后的牌
     * @return 飞机带单
     */
    private static Optional<DdzHand> tryPlaneOne(Map<Integer, Long> cnt, List<Card> sorted) {
        int n = sorted.size();
        if (n < 8 || n % 4 != 0) {// 飞机带单最少8张
            return Optional.empty();
        }
        int k = n / 4;
        if (k < 2) {// 飞机带单最少2组
            return Optional.empty();
        }
        for (int start = 3; start <= 14 - k + 1; start++) {
            // 飞机带单每组机身
            Map<Integer, Long> rem = remainderAfterPlaneBody(cnt, start, k);
            if (rem == null) {
                continue;
            }
            // 飞机带单每组翅膀
            if (sumCounts(rem) != k) {
                continue;
            }
            // 飞机带单最大点数
            int topBodyRank = start + k - 1;
            return Optional.of(
                    new DdzHand(ConstProto.CardType.PLANE_ONE, sorted, false, false, tripleStrength(topBodyRank), k));
        }
        return Optional.empty();
    }

    /**
     * 从计数里减去机身（连续 k 个名次各取三张），失败返回 null
     *
     * @param cnt   牌的计数
     * @param start 开始点数
     * @param k     组数
     * @return 剩余的牌
     */
    private static Map<Integer, Long> remainderAfterPlaneBody(Map<Integer, Long> cnt, int start, int k) {
        TreeMap<Integer, Long> rem = new TreeMap<>(cnt);
        for (int i = 0; i < k; i++) {
            int r = start + i;
            Long v = rem.get(r);
            if (v == null || v < 3) {
                return null;
            }
            long nv = v - 3;
            if (nv == 0) {
                rem.remove(r);
            } else {
                rem.put(r, nv);
            }
        }
        return rem;
    }

    /**
     * 求和
     *
     * @param m 牌的计数
     * @return 和
     */
    private static long sumCounts(Map<Integer, Long> m) {
        long s = 0;
        for (Long v : m.values()) {
            s += v;
        }
        return s;
    }

    /**
     * 飞机带对翅膀是否合法
     *
     * @param rem 剩余的牌
     * @param k   组数
     * @return 是否合法
     */
    private static boolean planeDoubleWingsValid(Map<Integer, Long> rem, int k) {
        if (sumCounts(rem) != 2L * k) {
            return false;
        }
        for (Long v : rem.values()) {
            if ((v & 1) != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * 尝试三带一
     *
     * @param cnt    牌的计数
     * @param sorted 排序后的牌
     * @return 三带一
     */
    private static Optional<DdzHand> tryTripleSingle(Map<Integer, Long> cnt, List<Card> sorted) {
        if (sorted.size() != 4 || cnt.size() != 2) {
            return Optional.empty();
        }
        int tripleRank = -1;
        int singleRank = -1;
        for (Map.Entry<Integer, Long> e : cnt.entrySet()) {
            if (e.getValue() == 3) {
                tripleRank = e.getKey();
            } else if (e.getValue() == 1) {
                singleRank = e.getKey();
            } else {
                return Optional.empty();
            }
        }
        if (tripleRank < 0 || singleRank < 0) {
            return Optional.empty();
        }
        return Optional
                .of(new DdzHand(ConstProto.CardType.TRIPLE_ONE, sorted, false, false, tripleStrength(tripleRank), 0));
    }

    /**
     * 尝试三带二
     *
     * @param cnt    牌的计数
     * @param sorted 排序后的牌
     * @return 三带二
     */
    private static Optional<DdzHand> tryTriplePair(Map<Integer, Long> cnt, List<Card> sorted) {
        if (sorted.size() != 5 || cnt.size() != 2) {
            return Optional.empty();
        }
        int tripleRank = -1;
        int pairRank = -1;
        for (Map.Entry<Integer, Long> e : cnt.entrySet()) {
            if (e.getValue() == 3) {
                tripleRank = e.getKey();
            } else if (e.getValue() == 2) {
                pairRank = e.getKey();
            } else {
                return Optional.empty();
            }
        }
        if (tripleRank < 0 || pairRank < 0) {
            return Optional.empty();
        }
        return Optional.of(
                new DdzHand(ConstProto.CardType.TRIPLE_DOUBLE, sorted, false, false, tripleStrength(tripleRank), 0));
    }

    /**
     * 是否合法的点数
     *
     * @param cardVal 牌的点数
     * @return 是否合法
     */
    private static boolean isStraightEligibleRank(int cardVal) {
        return cardVal >= 3 && cardVal <= 14;
    }

    /**
     * 单张强度
     *
     * @param cardVal 牌的点数
     * @return 强度
     */
    private static int singleStrength(int cardVal) {
        return normalizePoint(cardVal);
    }

    /**
     * 对子强度
     *
     * @param cardVal 牌的点数
     * @return 强度
     */
    private static int pairStrength(int cardVal) {
        return normalizePoint(cardVal);
    }

    /**
     * 三张强度
     *
     * @param cardVal 牌的点数
     * @return 强度
     */
    private static int tripleStrength(int cardVal) {
        return normalizePoint(cardVal);
    }

    /**
     * 炸弹强度
     *
     * @param cardVal 牌的点数
     * @return 强度
     */
    private static int bombStrength(int cardVal) {
        return normalizePoint(cardVal) + 1000;
    }

    /**
     * 顺子强度
     *
     * @param topRank 最大点数
     * @return 强度
     */
    private static int straightStrength(int topRank) {
        return normalizePoint(topRank);
    }

    /**
     * 3 最小，2 仅次于王；小王、大王最高（用于非火箭单牌比较时的扩展，此处炸弹/火箭已单列）
     */
    public static int normalizePoint(int cardVal) {
        if (cardVal >= 3 && cardVal <= 13) {
            return cardVal - 3;
        }
        if (cardVal == 14) {
            return 11;
        }
        if (cardVal == 15) {
            return 12;
        }
        if (cardVal == CardConstBridge.smallJoker()) {
            return 13;
        }
        if (cardVal == CardConstBridge.bigJoker()) {
            return 14;
        }
        return cardVal;
    }

    /**
     * 避免直接依赖 CardConst 命名冲突的小桥接
     */
    private static final class CardConstBridge {
        static int smallJoker() {
            return CardConst.SMALL_JOKER_VAL;
        }

        static int bigJoker() {
            return CardConst.BIG_JOKER_VAL;
        }
    }
}
