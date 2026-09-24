package com.cloud.hub.game.domain.ddz.ai;

import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.ddz.DdzHand;
import com.cloud.hub.game.domain.ddz.DdzRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;

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

/**
 * 斗地主合法压制牌型搜索器（Legal Beat Finder）。
 * <p>
 * 针对上家打出的牌型，从当前手牌中穷举所有合法能管上的牌（同类型更强牌、普通炸弹或王炸）。
 * <p>
 * 核心优化：
 * <ul>
 *   <li>1. 点数桶索引 {@code byRank} 入口计算一次全程复用；</li>
 *   <li>2. 使用高效 64 位 long 整数哈希替代 String 签名去重；</li>
 *   <li>3. DFS 顺子/连对利用点数连续性剪枝跳过空桶；</li>
 *   <li>4. 三带一/飞机拆解为「核心三张 + 最小散牌带牌」剪枝搜索。</li>
 * </ul>
 *
 */
public final class DdzLegalBeatFinder {

    private DdzLegalBeatFinder() {
    }

    private static final Logger logger = LoggerFactory.getLogger(DdzLegalBeatFinder.class);

    @FunctionalInterface
    private interface BeatStrategy {
        void search(List<Card> hand, Map<Integer, List<Card>> rankMap, DdzHand last, List<DdzHand> out, Set<Long> seen);
    }

    private static final Map<ConstProto.CardType, BeatStrategy> STRATEGIES = new EnumMap<>(ConstProto.CardType.class);

    static {
        STRATEGIES.put(ConstProto.CardType.SINGLE, (h, rm, last, out, seen) -> trySingles(h, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.DOUBLE, (h, rm, last, out, seen) -> tryPairs(rm, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.TRIPLE, (h, rm, last, out, seen) -> tryTriples(rm, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.STRAIGHT, (h, rm, last, out, seen) -> tryStraights(h, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.STRAIGHT_DOUBLE, (h, rm, last, out, seen) -> tryStraightDoubles(h, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.TRIPLE_ONE, (h, rm, last, out, seen) -> tryTripleOne(rm, h, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.TRIPLE_DOUBLE, (h, rm, last, out, seen) -> tryTripleDouble(rm, h, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.PLANE_ONE, (h, rm, last, out, seen) -> tryPlaneOne(rm, h, last, out, seen));
        STRATEGIES.put(ConstProto.CardType.PLANE_DOUBLE, (h, rm, last, out, seen) -> tryPlaneDouble(rm, h, last, out, seen));
    }

    /**
     * 从当前手牌中查找所有能够压过上家牌型的合法出牌选项。
     *
     * @param hand 当前玩家自身手牌
     * @param last 桌面上一手待压制的牌型
     * @return 能够合法压制的所有牌型候选列表
     */
    public static List<DdzHand> findBeatingHands(List<Card> hand, DdzHand last) {
        List<DdzHand> out = new ArrayList<>();
        if (last == null || last.getCards().isEmpty()) {
            return out;
        }
        Set<Long> seen = new HashSet<>();
        Map<Integer, List<Card>> rankMap = byRank(hand);

        // 无论上家出什么牌，火箭与普通炸弹永远具有最高压制尝试权
        tryRocket(hand, last, out, seen);
        tryBombs(rankMap, last, out, seen);
        // 上家若是王炸，全场无人能管，直接返回
        if (last.isRocket()) {
            return out;
        }

        // 依据上家牌型，直接分流到策略表执行，彻底消除 switch-case
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
     *
     * @param hand 手牌列表
     * @param last 上家牌
     * @param out  输出候选集合
     * @param seen 去重集合
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
     *
     * @param rankMap 点数桶
     * @param last    上家牌
     * @param out     输出候选集合
     * @param seen    去重集合
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
     * 检索能压过上家的单张牌。
     *
     * @param hand 手牌列表
     * @param last 上家牌
     * @param out  输出候选
     * @param seen 去重集合
     */
    private static void trySingles(List<Card> hand, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        for (Card c : hand) {
            addIfBeats(Collections.singletonList(c), last, out, seen);
        }
    }

    /**
     * 检索能压过上家的对子。
     *
     * @param rankMap 点数桶
     * @param last    上家牌
     * @param out     输出候选
     * @param seen    去重集合
     */
    private static void tryPairs(Map<Integer, List<Card>> rankMap, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        for (List<Card> lst : rankMap.values()) {
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
     * 检索能压过上家的三张（三不带）。
     *
     * @param rankMap 点数桶
     * @param last    上家牌
     * @param out     输出候选
     * @param seen    去重集合
     */
    private static void tryTriples(Map<Integer, List<Card>> rankMap, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        for (List<Card> lst : rankMap.values()) {
            if (lst.size() < 3) {
                continue;
            }
            addIfBeats(new ArrayList<>(lst.subList(0, 3)), last, out, seen);
        }
    }

    /**
     * 剪枝枚举三带一：先选点数大于上家的三张核心，再从剩余牌中贪心选最小单张作为带牌。
     *
     * @param rankMap 点数桶
     * @param hand    手牌
     * @param last    上家牌
     * @param out     输出候选
     * @param seen    去重集合
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
    }

    /**
     * 剪枝枚举三带二：先选三张核心，再从剩余点数桶中选最小对子作为带牌。
     *
     * @param rankMap 点数桶
     * @param hand    手牌
     * @param last    上家牌
     * @param out     输出候选
     * @param seen    去重集合
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
     * 剪枝枚举飞机带单：检索连续的三张机身核心，再从剩余牌中选最小单牌作为翅膀。
     *
     * @param rankMap 点数桶
     * @param hand    手牌
     * @param last    上家牌
     * @param out     输出候选
     * @param seen    去重集合
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
     * 剪枝枚举飞机带对：检索连续的三张机身核心，再从剩余牌中选最小对子作为翅膀。
     *
     * @param rankMap 点数桶
     * @param hand    手牌
     * @param last    上家牌
     * @param out     输出候选
     * @param seen    去重集合
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
     * 寻找点数连续且数量均 ≥3 的机身起始点。
     *
     * @param rankMap 点数桶
     * @param segs    飞机连续段数
     * @param minKey  必须大于的最小点数基准
     * @return 符合要求的起始点数列表
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
     * 从手牌中排除已选核心牌后，挑出最小的 kickerN 张单牌。
     *
     * @param hand    手牌列表
     * @param core    已选中的核心牌
     * @param kickerN 需要挑出的单牌数量
     * @return 选出的带牌列表；若不足则返回 null
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
        List<Card> kicker = new ArrayList<>();
        for (int i = candidates.size() - kickerN; i < candidates.size(); i++) {
            kicker.add(candidates.get(i));
        }
        return kicker;
    }

    /**
     * 从点数桶中排除核心牌后，挑出最小的 pairN 个对子。
     *
     * @param rankMap 点数桶
     * @param core    已选中的核心牌
     * @param pairN   需要挑选的对子数量
     * @return 选出的对子牌列表；若不足则返回 null
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

    /**
     * 浅拷贝点数桶字典（保持内部 List 隔离）。
     *
     * @param rankMap 源字典
     * @return 拷贝后的新字典
     */
    private static Map<Integer, List<Card>> cloneRankMap(Map<Integer, List<Card>> rankMap) {
        Map<Integer, List<Card>> c = new HashMap<>();
        for (Map.Entry<Integer, List<Card>> e : rankMap.entrySet()) {
            c.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return c;
    }

    // ==================== 顺子/连对 DFS（rank 索引优化） ====================

    /**
     * 利用点数桶索引进行顺子合法压制搜索。
     *
     * @param hand 手牌
     * @param last 上家牌
     * @param out  输出候选
     * @param seen 去重集合
     */
    private static void tryStraights(List<Card> hand, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        int len = last.getStraightLen();
        if (len < 5) {
            return;
        }
        int lastKey = last.getStrengthKey();
        Map<Integer, List<Card>> rankMap = byRank(hand);
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

    /**
     * 预检从 start 起始能否拼成长度为 len 的顺子。
     */
    private static boolean canFormStraight(Map<Integer, List<Card>> rankMap, int start, int len) {
        for (int i = 0; i < len; i++) {
            List<Card> lst = rankMap.get(start + i);
            if (lst == null || lst.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * DFS 回溯枚举指定点数区间的顺子花色组合。
     */
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
     * 利用点数桶索引进行连对（双顺）合法压制搜索。
     *
     * @param hand 手牌
     * @param last 上家牌
     * @param out  输出候选
     * @param seen 去重集合
     */
    private static void tryStraightDoubles(List<Card> hand, DdzHand last, List<DdzHand> out, Set<Long> seen) {
        int pairs = last.getStraightLen();
        if (pairs < 3) {
            return;
        }
        int lastKey = last.getStrengthKey();
        Map<Integer, List<Card>> rankMap = byRank(hand);
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

    /**
     * 预检从 start 起始能否拼成长度为 pairs 对的双顺。
     */
    private static boolean canFormStraightDouble(Map<Integer, List<Card>> rankMap, int start, int pairs) {
        for (int i = 0; i < pairs; i++) {
            List<Card> lst = rankMap.get(start + i);
            if (lst == null || lst.size() < 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * DFS 回溯枚举连对花色组合。
     */
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

    // ==================== 工具辅助方法 ====================

    /**
     * 将扑克牌列表按点数 cardVal 归类建立有序点数桶。
     *
     * @param hand 手牌列表
     * @return TreeMap 点数桶
     */
    private static Map<Integer, List<Card>> byRank(List<Card> hand) {
        Map<Integer, List<Card>> map = new TreeMap<>();
        for (Card c : hand) {
            map.computeIfAbsent(c.getCardVal(), k -> new ArrayList<>()).add(c);
        }
        return map;
    }

    /**
     * 判定指定牌列表是否构成合法牌型且能压制上家；若是则去重并加入候选池。
     *
     * @param cards 候选扑克牌集合
     * @param last  上家牌
     * @param out   输出列表
     * @param seen  去重集合
     */
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
     * 对牌型生成高效 64 位 long 整数哈希特征，避免字符串拼接与堆内存分配开销。
     *
     * @param h 牌型对象
     * @return 64 位哈希值
     */
    static long hashHand(DdzHand h) {
        long hash = h.getType().ordinal() * 31L;
        for (Card c : h.getCards()) {
            hash = hash * 131 + c.getId();
        }
        return hash;
    }

    /**
     * 字符串签名方法，供外部模块去重或排查日志使用。
     *
     * @param h 牌型对象
     * @return 文本签名
     */
    public static String signature(DdzHand h) {
        List<Integer> ids = new ArrayList<>();
        for (Card c : h.getCards()) {
            ids.add(c.getId());
        }
        Collections.sort(ids);
        return h.getType().name() + ":" + ids;
    }
}
