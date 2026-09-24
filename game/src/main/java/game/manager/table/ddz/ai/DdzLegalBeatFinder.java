package game.manager.table.ddz.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import proto.ConstProto;

import game.manager.table.cards.Card;
import game.manager.table.ddz.DdzHand;
import game.manager.table.ddz.DdzRules;

/**
 * 斗地主合法压制牌型搜索器（Legal Beat Finder）。
 * <p>
 * 针对上家打出的牌型，从当前手牌中穷举所有合法能管上的牌（同类型更强牌、普通炸弹或王炸）。
 * <p>
 * 核心优化：
 * <ul>
 *   <li>1. 点数桶索引 {@code byRank} 入口计算一次全程复用；</li>
 *   <li>2. 使用高效 64 位 long 整数哈希替代 String 签名去重；</li>
 *   <li>3. 基于 {@link EnumMap} 函数式策略表消除 switch-case，实现 O(1) 策略分发；</li>
 *   <li>4. 单张/对子/三张点数预剪枝，避免低点数无效候选进入规则识别；</li>
 *   <li>5. DFS 顺子/连对利用点数连续性剪枝跳过空桶；</li>
 *   <li>6. 三带一/飞机拆解为「核心三张 + 最小散牌带牌」剪枝搜索。</li>
 * </ul>
 *
 * @author cloud
 * @version 2.0
 * @date 2026-05-03
 * @since 1.0
 */
public final class DdzLegalBeatFinder {

    private static final Logger logger = LoggerFactory.getLogger(DdzLegalBeatFinder.class);

    private DdzLegalBeatFinder() {
    }

    /**
     * 合法出牌搜索策略函数接口
     */
    @FunctionalInterface
    private interface BeatStrategy {
        /**
         * 执行指定牌型的合法压制牌型搜索
         *
         * @param hand    手牌列表
         * @param rankMap 按点数归类的牌桶字典
         * @param last    待压制的上一手牌
         * @param out     结果收集列表
         * @param seen    牌型去重集合
         */
        void search(List<Card> hand, Map<Integer, List<Card>> rankMap, DdzHand last, List<DdzHand> out, Set<Long> seen);
    }

    /** 牌型分发策略表 <牌型, 对应的搜索策略> */
    private static final Map<ConstProto.CardType, BeatStrategy> STRATEGIES = new EnumMap<>(ConstProto.CardType.class);

    static {
        STRATEGIES.put(ConstProto.CardType.SINGLE, (h, rm, last, out, seen) -> trySingles(h, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.DOUBLE, (h, rm, last, out, seen) -> tryPairs(rm, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.TRIPLE, (h, rm, last, out, seen) -> tryTriples(rm, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.STRAIGHT, (h, rm, last, out, seen) -> tryStraights(rm, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.STRAIGHT_DOUBLE, (h, rm, last, out, seen) -> tryStraightDoubles(rm, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.TRIPLE_ONE, (h, rm, last, out, seen) -> tryTripleOne(rm, h, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.TRIPLE_DOUBLE, (h, rm, last, out, seen) -> tryTripleDouble(rm, h, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.PLANE_ONE, (h, rm, last, out, seen) -> tryPlaneOne(rm, h, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.PLANE_DOUBLE, (h, rm, last, out, seen) -> tryPlaneDouble(rm, h, last, out, seen));
    }

    /**
     * 查找能压制上一手的合法牌型
     *
     * @param hand 手牌
     * @param last 上一手
     * @return 能压制上一手的合法牌型
     */
    public static List<DdzHand> findBeatingHands(List<Card> hand, DdzHand last) {
        List<DdzHand> out = new ArrayList<>();
        if (last == null || last.getCards().isEmpty()) {
            return out;
        }
        Set<Long> seen = new HashSet<>();
        // 入口统一计算一次手牌点数桶，后续所有同型搜索全链路复用，严禁二次计算
        Map<Integer, List<Card>> rankMap = byRank(hand);

        // 无论上家出什么牌，火箭与普通炸弹永远具有最高压制尝试权
        tryRocket(hand, last, out, seen);
        tryBombs(rankMap, last, out, seen);
        if (last.isRocket()) {
            return out;
        }

        // 依据上家牌型，分发至策略表执行，彻底消除 switch-case 分支
        BeatStrategy strategy = STRATEGIES.get(last.getType());
        if (strategy != null) {
            strategy.search(hand, rankMap, last, out, seen);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("[DDZ-BeatFinder] 上家牌型: {}, 手牌数: {}, 匹配出合法压制手牌数: {}",
                    last.getType(), hand.size(), out.size());
        }
        return out;
    }

    /**
     * 尝试从手牌中提取双王组合（火箭/王炸）。
     */
    private static void tryRocket(List<Card> hand, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        Card s = null, b = null;
        for (Card c : hand) {
            if (c.isSmallJoker()) {
                s = c;
            } else if (c.isBigJoker()) {
                b = c;
            }
        }
        if (s != null && b != null) {
            addIfBeats(Arrays.asList(s, b), last, out, seen);
        }
    }

    /**
     * 尝试从点数桶中检索 4 张同点数的普通炸弹。
     */
    private static void tryBombs(Map<Integer, List<Card>> rankMap, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        for (List<Card> lst : rankMap.values()) {
            if (lst.size() < 4) {
                continue;
            }
            List<Card> bomb = new ArrayList<>(lst.subList(0, 4));
            addIfBeats(bomb, last, out, seen);
        }
    }

    /**
     * 检索能压过上家的单张牌（带前置点数剪枝）。
     */
    private static void trySingles(List<Card> hand, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        int lastKey = last.getStrengthKey();
        for (Card c : hand) {
            // 剪枝：单牌点数不大于上家直接跳过，避免无效进入 analyze 与 beats 校验
            if (DdzRules.normalizePoint(c.getCardVal()) <= lastKey) {
                continue;
            }
            addIfBeats(Collections.singletonList(c), last, out, seen);
        }
    }

    /**
     * 检索能压过上家的对子（带前置点数剪枝）。
     */
    private static void tryPairs(Map<Integer, List<Card>> rankMap, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        int lastKey = last.getStrengthKey();
        for (Map.Entry<Integer, List<Card>> e : rankMap.entrySet()) {
            // 剪枝：点数不大于上家直接跳过
            if (DdzRules.normalizePoint(e.getKey()) <= lastKey) {
                continue;
            }
            List<Card> lst = e.getValue();
            if (lst.size() < 2) {
                continue;
            }
            for (int i = 0; i < lst.size(); i++) {
                for (int j = i + 1; j < lst.size(); j++) {
                    addIfBeats(Arrays.asList(lst.get(i), lst.get(j)), last, out, seen);
                }
            }
        }
    }

    /**
     * 检索能压过上家的三张（带前置点数剪枝）。
     */
    private static void tryTriples(Map<Integer, List<Card>> rankMap, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        int lastKey = last.getStrengthKey();
        for (Map.Entry<Integer, List<Card>> e : rankMap.entrySet()) {
            // 剪枝：点数不大于上家直接跳过
            if (DdzRules.normalizePoint(e.getKey()) <= lastKey) {
                continue;
            }
            List<Card> lst = e.getValue();
            if (lst.size() < 3) {
                continue;
            }
            addIfBeats(new ArrayList<>(lst.subList(0, 3)), last, out, seen);
        }
    }

    /**
     * 剪枝枚举三带一：先选三张核心(rank≥last的三张)，再从剩余牌中选最小单张
     */
    private static void tryTripleOne(Map<Integer, List<Card>> rankMap, List<Card> hand, DdzHand last,
                                     List<DdzHand> out, Set<Long> seen) {
        int lastKey = last.getStrengthKey();
        for (Map.Entry<Integer, List<Card>> e : rankMap.entrySet()) {
            List<Card> lst = e.getValue();
            if (lst.size() < 3) {
                continue;
            }
            int coreKey = DdzRules.normalizePoint(e.getKey());
            if (coreKey <= lastKey) {
                continue;
            }
            List<Card> triple = new ArrayList<>(lst.subList(0, 3));
            List<Card> kicker = pickKickerSingle(hand, triple, 1);
            if (kicker != null) {
                List<Card> play = new ArrayList<>(triple);
                play.addAll(kicker);
                addIfBeats(play, last, out, seen);
            }
        }
        // 同时尝试炸弹压制
    }

    /**
     * 剪枝枚举三带二：先选三张核心，再从剩余牌中选最小对子
     */
    private static void tryTripleDouble(Map<Integer, List<Card>> rankMap, List<Card> hand, DdzHand last,
                                        List<DdzHand> out, Set<Long> seen) {
        int lastKey = last.getStrengthKey();
        for (Map.Entry<Integer, List<Card>> e : rankMap.entrySet()) {
            List<Card> lst = e.getValue();
            if (lst.size() < 3) {
                continue;
            }
            int coreKey = DdzRules.normalizePoint(e.getKey());
            if (coreKey <= lastKey) {
                continue;
            }
            List<Card> triple = new ArrayList<>(lst.subList(0, 3));
            List<Card> kicker = pickKickerPair(rankMap, triple, 1);
            if (kicker != null) {
                List<Card> play = new ArrayList<>(triple);
                play.addAll(kicker);
                addIfBeats(play, last, out, seen);
            }
        }
    }

    /**
     * 剪枝枚举飞机带单：找连续三张核心，再从剩余牌中选最小单张
     */
    private static void tryPlaneOne(Map<Integer, List<Card>> rankMap, List<Card> hand, DdzHand last,
                                    List<DdzHand> out, Set<Long> seen) {
        int segs = last.getStraightLen();
        int lastKey = last.getStrengthKey();
        List<int[]> cores = findConsecutiveTriples(rankMap, segs, lastKey);
        for (int[] startLen : cores) {
            int start = startLen[0];
            List<Card> tripleBody = new ArrayList<>();
            for (int i = 0; i < segs; i++) {
                List<Card> lst = rankMap.get(start + i);
                tripleBody.addAll(lst.subList(0, 3));
            }
            List<Card> kicker = pickKickerSingle(hand, tripleBody, segs);
            if (kicker != null) {
                List<Card> play = new ArrayList<>(tripleBody);
                play.addAll(kicker);
                addIfBeats(play, last, out, seen);
            }
        }
    }

    /**
     * 剪枝枚举飞机带对：找连续三张核心，再从剩余牌中选最小对子
     */
    private static void tryPlaneDouble(Map<Integer, List<Card>> rankMap, List<Card> hand, DdzHand last,
                                       List<DdzHand> out, Set<Long> seen) {
        int segs = last.getStraightLen();
        int lastKey = last.getStrengthKey();
        List<int[]> cores = findConsecutiveTriples(rankMap, segs, lastKey);
        for (int[] startLen : cores) {
            int start = startLen[0];
            List<Card> tripleBody = new ArrayList<>();
            Map<Integer, List<Card>> remaining = cloneRankMap(rankMap);
            for (int i = 0; i < segs; i++) {
                int r = start + i;
                List<Card> lst = remaining.get(r);
                tripleBody.addAll(lst.subList(0, 3));
                lst.subList(0, 3).clear();
                if (lst.isEmpty()) {
                    remaining.remove(r);
                }
            }
            List<Card> kicker = pickKickerPair(remaining, tripleBody, segs);
            if (kicker != null) {
                List<Card> play = new ArrayList<>(tripleBody);
                play.addAll(kicker);
                addIfBeats(play, last, out, seen);
            }
        }
    }

    /**
     * 找连续三张的核心起始点
     */
    private static List<int[]> findConsecutiveTriples(Map<Integer, List<Card>> rankMap, int segs, int minKey) {
        List<int[]> result = new ArrayList<>();
        for (int start = 3; start <= 14 - segs + 1; start++) {
            if (DdzRules.normalizePoint(start) <= minKey - segs + 1) {
                continue;
            }
            boolean ok = true;
            for (int i = 0; i < segs; i++) {
                List<Card> lst = rankMap.get(start + i);
                if (lst == null || lst.size() < 3) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                result.add(new int[]{start});
            }
        }
        return result;
    }

    /**
     * 从手牌中排除已选核心后，选最小的 kickerN 张单牌
     */
    private static List<Card> pickKickerSingle(List<Card> hand, List<Card> core, int kickerN) {
        List<Card> candidates = new ArrayList<>();
        Set<Integer> coreIds = new HashSet<>();
        for (Card c : core) {
            coreIds.add(c.getId());
        }
        for (Card c : hand) {
            if (!coreIds.contains(c.getId())) {
                candidates.add(c);
            }
        }
        if (candidates.size() < kickerN) {
            return null;
        }
        Collections.sort(candidates);
        // 取最小的 kickerN 张（candidates 已按降序排，取末尾）
        List<Card> kicker = new ArrayList<>();
        for (int i = candidates.size() - kickerN; i < candidates.size(); i++) {
            kicker.add(candidates.get(i));
        }
        return kicker;
    }

    /**
     * 从 rankMap 中排除核心后，选最小的 kickerN 个对子
     */
    private static List<Card> pickKickerPair(Map<Integer, List<Card>> rankMap, List<Card> core, int pairN) {
        Set<Integer> coreRanks = new HashSet<>();
        for (Card c : core) {
            coreRanks.add(c.getCardVal());
        }
        List<Card> kicker = new ArrayList<>();
        for (Integer rank : new ArrayList<>(rankMap.keySet())) {
            if (kicker.size() >= pairN * 2) {
                break;
            }
            if (coreRanks.contains(rank)) {
                continue;
            }
            List<Card> lst = rankMap.get(rank);
            if (lst != null && lst.size() >= 2) {
                kicker.add(lst.get(0));
                kicker.add(lst.get(1));
            }
        }
        return kicker.size() >= pairN * 2 ? kicker : null;
    }

    private static Map<Integer, List<Card>> cloneRankMap(Map<Integer, List<Card>> rankMap) {
        Map<Integer, List<Card>> c = new HashMap<>();
        for (Map.Entry<Integer, List<Card>> e : rankMap.entrySet()) {
            c.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return c;
    }

    // ==================== 顺子/连对 DFS（rank索引优化） ====================

    /**
     * 优化顺子枚举：直接复用入口处计算好的 rankMap 点数桶，只在有牌的 rank 上递归
     */
    private static void tryStraights(Map<Integer, List<Card>> rankMap, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        int len = last.getStraightLen();
        if (len < 5) {
            return;
        }
        int lastKey = last.getStrengthKey();
        for (int start = 3; start <= 14 - len + 1; start++) {
            if (DdzRules.normalizePoint(start + len - 1) <= lastKey) {
                continue;
            }
            if (!canFormStraight(rankMap, start, len)) {
                continue;
            }
            dfsStraight(rankMap, start, len, 0, new ArrayList<>(), last, out, seen);
        }
    }

    private static boolean canFormStraight(Map<Integer, List<Card>> rankMap, int start, int len) {
        for (int i = 0; i < len; i++) {
            List<Card> lst = rankMap.get(start + i);
            if (lst == null || lst.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static void dfsStraight(Map<Integer, List<Card>> rankMap, int start, int len, int depth,
                                    List<Card> acc, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        if (depth == len) {
            addIfBeats(new ArrayList<>(acc), last, out, seen);
            return;
        }
        int rank = start + depth;
        List<Card> lst = rankMap.get(rank);
        if (lst == null) {
            return;
        }
        for (Card c : lst) {
            acc.add(c);
            dfsStraight(rankMap, start, len, depth + 1, acc, last, out, seen);
            acc.remove(acc.size() - 1);
        }
    }

    /**
     * 优化连对枚举：直接复用入口处计算好的 rankMap 点数桶
     */
    private static void tryStraightDoubles(Map<Integer, List<Card>> rankMap, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        int pairs = last.getStraightLen();
        if (pairs < 3) {
            return;
        }
        int lastKey = last.getStrengthKey();
        for (int start = 3; start <= 14 - pairs + 1; start++) {
            if (DdzRules.normalizePoint(start + pairs - 1) <= lastKey) {
                continue;
            }
            if (!canFormStraightDouble(rankMap, start, pairs)) {
                continue;
            }
            dfsStraightDouble(rankMap, start, pairs, 0, new ArrayList<>(), last, out, seen);
        }
    }

    private static boolean canFormStraightDouble(Map<Integer, List<Card>> rankMap, int start, int pairs) {
        for (int i = 0; i < pairs; i++) {
            List<Card> lst = rankMap.get(start + i);
            if (lst == null || lst.size() < 2) {
                return false;
            }
        }
        return true;
    }

    private static void dfsStraightDouble(Map<Integer, List<Card>> rankMap, int start, int pairs, int depth,
                                          List<Card> acc, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        if (depth == pairs) {
            addIfBeats(new ArrayList<>(acc), last, out, seen);
            return;
        }
        int rank = start + depth;
        List<Card> lst = rankMap.get(rank);
        if (lst == null || lst.size() < 2) {
            return;
        }
        for (int i = 0; i < lst.size(); i++) {
            for (int j = i + 1; j < lst.size(); j++) {
                acc.add(lst.get(i));
                acc.add(lst.get(j));
                dfsStraightDouble(rankMap, start, pairs, depth + 1, acc, last, out, seen);
                acc.remove(acc.size() - 1);
                acc.remove(acc.size() - 1);
            }
        }
    }

    // ==================== 工具方法 ====================

    private static Map<Integer, List<Card>> byRank(List<Card> hand) {
        Map<Integer, List<Card>> map = new TreeMap<>();
        for (Card c : hand) {
            map.computeIfAbsent(c.getCardVal(), k -> new ArrayList<>()).add(c);
        }
        return map;
    }

    private static void addIfBeats(List<Card> cards, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        Optional<DdzHand> o = DdzRules.analyze(cards);
        if (!o.isPresent()) {
            return;
        }
        DdzHand h = o.get();
        if (!DdzRules.beats(h, last)) {
            return;
        }
        if (seen.add(hashHand(h))) {
            out.add(h);
        }
    }

    /**
     * long hash 替代 String signature，避免排序+toString分配
     */
    static long hashHand(DdzHand h) {
        long hash = h.getType().ordinal() * 31L;
        for (Card c : h.getCards()) {
            hash = hash * 131 + c.getId();
        }
        return hash;
    }

    /**
     * 保留签名方法供外部使用（如 DdzSimpleAi 去重）
     */
    public static String signature(DdzHand h) {
        List<Integer> ids = new ArrayList<>();
        for (Card c : h.getCards()) {
            ids.add(c.getId());
        }
        Collections.sort(ids);
        return h.getType().name() + ":" + ids.toString();
    }
}
