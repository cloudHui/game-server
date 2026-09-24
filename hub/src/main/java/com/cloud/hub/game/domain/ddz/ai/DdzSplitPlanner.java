package com.cloud.hub.game.domain.ddz.ai;

import com.cloud.hub.game.domain.card.CardConst;
import com.cloud.hub.game.domain.cards.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 斗地主智能拆牌规划器（Split Planner）。
 * <p>
 * 按照贪心启发顺序依次提取：火箭 → 炸弹 → 飞机带对 → 飞机带单 → 三条 → 顺子 → 连对 → 对子 → 散牌单张。
 * <p>
 * 并支持“炸弹变体拆解搜索”（{@link #planBest}）：评估是否应当将 4 张炸弹拆成 3+1 补足飞机或顺子，以换取全局更少的手数与更低的总代价。
 * 单个方法行数均在 35 行以内。
 *
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @since 1.0
 */
public final class DdzSplitPlanner {

    private DdzSplitPlanner() {
    }

    /**
     * 执行标准贪心单次拆牌规划。
     *
     * @param hand 当前手牌列表
     * @return 拆解划分出的牌组单元列表（CardGroup 列表）
     */
    public static List<CardGroup> plan(List<Card> hand) {
        return planWithSplitBombs(hand, Collections.emptySet());
    }

    /**
     * 多分支变体拆牌搜索：在默认拆法与尝试拆解各个炸弹之间进行对比，选取综合代价最低的最优规划方案。
     *
     * @param hand 当前手牌列表
     * @return 最优拆牌方案
     */
    public static List<CardGroup> planBest(List<Card> hand) {
        List<CardGroup> best = plan(hand);
        double bestCost = splitCost(best);

        // 收集手牌中所有可供拆解测试的 4 张炸弹点数
        TreeMap<Integer, List<Card>> pool = new TreeMap<>();
        for (Card c : hand) {
            pool.computeIfAbsent(c.getCardVal(), k -> new ArrayList<>()).add(c);
        }
        List<Integer> bombRanks = new ArrayList<>();
        for (Map.Entry<Integer, List<Card>> e : pool.entrySet()) {
            if (e.getValue().size() >= 4 && !isJoker(e.getKey())) {
                bombRanks.add(e.getKey());
            }
        }

        // 分别尝试单拆每一种炸弹
        for (int rank : bombRanks) {
            Set<Integer> splitSet = new HashSet<>();
            splitSet.add(rank);
            List<CardGroup> alt = planWithSplitBombs(hand, splitSet);
            double cost = splitCost(alt);
            if (cost < bestCost) {
                bestCost = cost;
                best = alt;
            }
        }

        // 尝试将所有炸弹全部拆解作为三带散牌
        if (bombRanks.size() > 1) {
            Set<Integer> splitSet = new HashSet<>(bombRanks);
            List<CardGroup> alt = planWithSplitBombs(hand, splitSet);
            double cost = splitCost(alt);
            if (cost < bestCost) {
                best = alt;
            }
        }

        return best;
    }

    /**
     * 计算拆牌方案的综合评价值（手牌组数惩罚 + 保留代价，越低代表越容易跑牌）。
     *
     * @param groups 拆牌后的组列表
     * @return 综合代价分
     */
    private static double splitCost(List<CardGroup> groups) {
        double groupPenalty = groups.size() * 20.0;
        double preserve = 0;
        for (CardGroup g : groups) {
            preserve += g.getPreserveScore();
        }
        return groupPenalty + preserve * 0.01;
    }

    /**
     * 判断点数是否为王牌（大王或小王）。
     *
     * @param rank 点数值
     * @return true 为王牌
     */
    private static boolean isJoker(int rank) {
        return rank == CardConst.SMALL_JOKER_VAL
                || rank == CardConst.BIG_JOKER_VAL;
    }

    /**
     * 支持指定炸弹点数暂不当炸弹处理的管线式拆牌。
     *
     * @param hand           手牌
     * @param splitBombRanks 允许拆开用于三带/飞机的炸弹点数集合
     * @return 拆解后的牌组单元集合
     */
    private static List<CardGroup> planWithSplitBombs(List<Card> hand, Set<Integer> splitBombRanks) {
        List<CardGroup> groups = new ArrayList<>();
        TreeMap<Integer, List<Card>> pool = new TreeMap<>();
        for (Card c : hand) {
            pool.computeIfAbsent(c.getCardVal(), k -> new ArrayList<>()).add(c);
        }

        extractRocket(pool, groups);
        if (splitBombRanks.isEmpty()) {
            extractBombs(pool, groups);
        } else {
            extractBombsExcept(pool, groups, splitBombRanks);
        }
        extractPlaneDoubles(pool, groups);
        extractPlaneOnes(pool, groups);
        extractTriples(pool, groups);
        extractStraights(pool, groups);
        extractStraightDoubles(pool, groups);
        extractPairs(pool, groups);
        extractSingles(pool, groups);
        return groups;
    }

    /**
     * 抽取炸弹，但跳过指定的拆解点数。
     */
    private static void extractBombsExcept(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups,
                                           Set<Integer> except) {
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Map.Entry<Integer, List<Card>> e : new ArrayList<>(pool.entrySet())) {
                int r = e.getKey();
                if (except.contains(r) || isJoker(r)) {
                    continue;
                }
                List<Card> lst = e.getValue();
                while (lst != null && lst.size() >= 4) {
                    List<Card> bomb = new ArrayList<>();
                    for (int i = 0; i < 4; i++) {
                        bomb.add(lst.remove(lst.size() - 1));
                    }
                    groups.add(new CardGroup(bomb, DdzAiConstants.SPLIT_WEIGHT_BOMB));
                    progress = true;
                }
                removeEmpty(pool, r);
            }
        }
    }

    /**
     * 抽取双王火箭（王炸）。
     */
    private static void extractRocket(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
        List<Card> sj = pool.get(CardConst.SMALL_JOKER_VAL);
        List<Card> bj = pool.get(CardConst.BIG_JOKER_VAL);
        if (sj != null && !sj.isEmpty() && bj != null && !bj.isEmpty()) {
            List<Card> rocket = new ArrayList<>();
            rocket.add(sj.remove(sj.size() - 1));
            rocket.add(bj.remove(bj.size() - 1));
            removeEmpty(pool, CardConst.SMALL_JOKER_VAL);
            removeEmpty(pool, CardConst.BIG_JOKER_VAL);
            groups.add(new CardGroup(rocket, DdzAiConstants.SPLIT_WEIGHT_ROCKET));
        }
    }

    /**
     * 牌池中若该点数已无可用牌，则从字典中移除该键。
     */
    private static void removeEmpty(TreeMap<Integer, List<Card>> pool, int rank) {
        List<Card> l = pool.get(rank);
        if (l != null && l.isEmpty()) {
            pool.remove(rank);
        }
    }

    /**
     * 贪心抽取所有 4 张同点数的普通炸弹。
     */
    private static void extractBombs(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Map.Entry<Integer, List<Card>> e : new ArrayList<>(pool.entrySet())) {
                int r = e.getKey();
                if (r == CardConst.SMALL_JOKER_VAL || r == CardConst.BIG_JOKER_VAL) {
                    continue;
                }
                List<Card> lst = e.getValue();
                while (lst != null && lst.size() >= 4) {
                    List<Card> bomb = new ArrayList<>();
                    for (int i = 0; i < 4; i++) {
                        bomb.add(lst.remove(lst.size() - 1));
                    }
                    groups.add(new CardGroup(bomb, DdzAiConstants.SPLIT_WEIGHT_BOMB));
                    progress = true;
                }
                removeEmpty(pool, r);
            }
        }
    }

    /**
     * 抽取飞机带翅膀（带对子）。
     */
    private static void extractPlaneDoubles(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
        boolean progress = true;
        while (progress) {
            progress = false;
            found:
            for (int k = 12; k >= 2; k--) {
                for (int start = 3; start <= 14 - k + 1; start++) {
                    List<Card> taken = tryTakePlaneDouble(pool, start, k);
                    if (taken != null) {
                        int score = DdzAiConstants.splitPlaneGroupScore(k);
                        groups.add(new CardGroup(taken, score));
                        progress = true;
                        break found;
                    }
                }
            }
        }
    }

    /**
     * 抽取飞机带翅膀（带单牌）。
     */
    private static void extractPlaneOnes(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
        boolean progress = true;
        while (progress) {
            progress = false;
            found:
            for (int k = 12; k >= 2; k--) {
                for (int start = 3; start <= 14 - k + 1; start++) {
                    List<Card> taken = tryTakePlaneOne(pool, start, k);
                    if (taken != null) {
                        int score = DdzAiConstants.splitPlaneGroupScore(k);
                        groups.add(new CardGroup(taken, score));
                        progress = true;
                        break found;
                    }
                }
            }
        }
    }

    /**
     * 尝试提取指定起始位置、连续 k 段的飞机带对。
     */
    private static List<Card> tryTakePlaneDouble(TreeMap<Integer, List<Card>> pool, int start, int k) {
        if (!hasTripleBody(pool, start, k)) {
            return null;
        }
        TreeMap<Integer, List<Card>> sim = clonePool(pool);
        takeTripleBody(sim, start, k, new ArrayList<>());
        if (totalCards(sim) < 2 * k || maxPairCount(sim) < k) {
            return null;
        }
        List<Card> out = new ArrayList<>(5 * k);
        takeTripleBody(pool, start, k, out);
        takeKPairsGreedy(pool, k, out);
        return out;
    }

    /**
     * 尝试提取指定起始位置、连续 k 段的飞机带单。
     */
    private static List<Card> tryTakePlaneOne(TreeMap<Integer, List<Card>> pool, int start, int k) {
        if (!hasTripleBody(pool, start, k)) {
            return null;
        }
        TreeMap<Integer, List<Card>> sim = clonePool(pool);
        takeTripleBody(sim, start, k, new ArrayList<>());
        if (totalCards(sim) < k) {
            return null;
        }
        List<Card> out = new ArrayList<>(4 * k);
        takeTripleBody(pool, start, k, out);
        takeKSinglesGreedy(pool, k, out);
        return out;
    }

    /**
     * 深度克隆点数牌池。
     */
    private static TreeMap<Integer, List<Card>> clonePool(TreeMap<Integer, List<Card>> pool) {
        TreeMap<Integer, List<Card>> c = new TreeMap<>();
        for (Map.Entry<Integer, List<Card>> e : pool.entrySet()) {
            c.put(e.getKey(), new ArrayList<>(e.getValue()));
        }
        return c;
    }

    /**
     * 统计牌池中的全部余牌总张数。
     */
    private static int totalCards(TreeMap<Integer, List<Card>> pool) {
        int t = 0;
        for (List<Card> lst : pool.values()) {
            t += lst.size();
        }
        return t;
    }

    /**
     * 统计牌池中最多能够凑出的对子总数。
     */
    private static int maxPairCount(TreeMap<Integer, List<Card>> pool) {
        int p = 0;
        for (List<Card> lst : pool.values()) {
            p += lst.size() / 2;
        }
        return p;
    }

    /**
     * 预检从 start 起始、连续 k 段是否均具有 ≥3 张牌构筑机身。
     */
    private static boolean hasTripleBody(TreeMap<Integer, List<Card>> pool, int start, int k) {
        for (int i = 0; i < k; i++) {
            int r = start + i;
            List<Card> lst = pool.get(r);
            if (lst == null || lst.size() < 3) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从牌池中移除并取出连续 k 段的飞机机身三张牌。
     */
    private static void takeTripleBody(TreeMap<Integer, List<Card>> pool, int start, int k, List<Card> sink) {
        for (int i = 0; i < k; i++) {
            int r = start + i;
            List<Card> lst = pool.get(r);
            for (int j = 0; j < 3; j++) {
                sink.add(lst.remove(lst.size() - 1));
            }
            removeEmpty(pool, r);
        }
    }

    /**
     * 贪心挑出 pairsNeeded 个最小对子作为飞机翅膀。
     */
    private static void takeKPairsGreedy(TreeMap<Integer, List<Card>> pool, int pairsNeeded, List<Card> sink) {
        for (int done = 0; done < pairsNeeded; done++) {
            boolean moved = false;
            for (Integer rank : new ArrayList<>(pool.keySet())) {
                List<Card> lst = pool.get(rank);
                if (lst != null && lst.size() >= 2) {
                    sink.add(lst.remove(lst.size() - 1));
                    sink.add(lst.remove(lst.size() - 1));
                    removeEmpty(pool, rank);
                    moved = true;
                    break;
                }
            }
            if (!moved) {
                return;
            }
        }
    }

    /**
     * 贪心挑出 singlesNeeded 张最小单牌作为飞机翅膀。
     */
    private static void takeKSinglesGreedy(TreeMap<Integer, List<Card>> pool, int singlesNeeded, List<Card> sink) {
        for (int done = 0; done < singlesNeeded; done++) {
            boolean moved = false;
            for (Integer rank : new ArrayList<>(pool.keySet())) {
                List<Card> lst = pool.get(rank);
                if (lst != null && !lst.isEmpty()) {
                    sink.add(lst.remove(lst.size() - 1));
                    removeEmpty(pool, rank);
                    moved = true;
                    break;
                }
            }
            if (!moved) {
                return;
            }
        }
    }

    /**
     * 抽取纯三张（三不带）。
     */
    private static void extractTriples(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
        for (Map.Entry<Integer, List<Card>> e : new ArrayList<>(pool.entrySet())) {
            int r = e.getKey();
            List<Card> lst = e.getValue();
            while (lst != null && lst.size() >= 3) {
                List<Card> t = new ArrayList<>();
                for (int kk = 0; kk < 3; kk++) {
                    t.add(lst.remove(lst.size() - 1));
                }
                groups.add(new CardGroup(t, DdzAiConstants.SPLIT_WEIGHT_TRIPLE));
            }
            removeEmpty(pool, r);
        }
    }

    /**
     * 抽取单顺子（长度从 12 贪心递减至 5）。
     */
    private static void extractStraights(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
        boolean progress = true;
        while (progress) {
            progress = false;
            for (int len = 12; len >= 5 && !progress; len--) {
                for (int start = 3; start <= 14 - len + 1 && !progress; start++) {
                    if (canTakeStraight(pool, start, len)) {
                        List<Card> straight = takeStraight(pool, start, len);
                        int score = DdzAiConstants.SPLIT_WEIGHT_STRAIGHT_MIN_BONUS
                                + len * DdzAiConstants.SPLIT_WEIGHT_STRAIGHT_PER_CARD;
                        groups.add(new CardGroup(straight, score));
                        progress = true;
                    }
                }
            }
        }
    }

    /**
     * 预检从 start 起始、长度为 len 的单顺子能否完整抽取。
     */
    private static boolean canTakeStraight(TreeMap<Integer, List<Card>> pool, int start, int len) {
        for (int r = start; r < start + len; r++) {
            if (!isStraightRank(r)) {
                return false;
            }
            List<Card> lst = pool.get(r);
            if (lst == null || lst.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从牌池中取出长度为 len 的单顺子牌组。
     */
    private static List<Card> takeStraight(TreeMap<Integer, List<Card>> pool, int start, int len) {
        List<Card> straight = new ArrayList<>();
        for (int r = start; r < start + len; r++) {
            List<Card> lst = pool.get(r);
            straight.add(lst.remove(lst.size() - 1));
            removeEmpty(pool, r);
        }
        return straight;
    }

    /**
     * 判断点数是否在合法顺子点数区间内（3 ~ A 即 3~14）。
     */
    private static boolean isStraightRank(int r) {
        return r >= 3 && r <= 14;
    }

    /**
     * 抽取双顺（连对，长度从 8 对递减至 3 对）。
     */
    private static void extractStraightDoubles(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
        boolean progress = true;
        while (progress) {
            progress = false;
            for (int pairs = 8; pairs >= 3 && !progress; pairs--) {
                for (int start = 3; start <= 14 - pairs + 1 && !progress; start++) {
                    if (canTakeStraightPair(pool, start, pairs)) {
                        List<Card> sd = takeStraightPair(pool, start, pairs);
                        int score = DdzAiConstants.SPLIT_WEIGHT_STRAIGHT_DOUBLE_MIN_BONUS
                                + pairs * DdzAiConstants.SPLIT_WEIGHT_STRAIGHT_DOUBLE_PER_PAIR;
                        groups.add(new CardGroup(sd, score));
                        progress = true;
                    }
                }
            }
        }
    }

    /**
     * 预检从 start 起始、长度为 pairs 对的连对能否完整抽取。
     */
    private static boolean canTakeStraightPair(TreeMap<Integer, List<Card>> pool, int start, int pairs) {
        for (int i = 0; i < pairs; i++) {
            int r = start + i;
            if (!isStraightRank(r)) {
                return false;
            }
            List<Card> lst = pool.get(r);
            if (lst == null || lst.size() < 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从牌池中取出 pairs 对连对牌组。
     */
    private static List<Card> takeStraightPair(TreeMap<Integer, List<Card>> pool, int start, int pairs) {
        List<Card> out = new ArrayList<>();
        for (int i = 0; i < pairs; i++) {
            int r = start + i;
            List<Card> lst = pool.get(r);
            out.add(lst.remove(lst.size() - 1));
            out.add(lst.remove(lst.size() - 1));
            removeEmpty(pool, r);
        }
        return out;
    }

    /**
     * 抽取普通对子。
     */
    private static void extractPairs(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
        for (Map.Entry<Integer, List<Card>> e : new ArrayList<>(pool.entrySet())) {
            List<Card> lst = e.getValue();
            while (lst != null && lst.size() >= 2) {
                List<Card> p = new ArrayList<>();
                p.add(lst.remove(lst.size() - 1));
                p.add(lst.remove(lst.size() - 1));
                groups.add(new CardGroup(p, DdzAiConstants.SPLIT_WEIGHT_PAIR));
            }
            removeEmpty(pool, e.getKey());
        }
    }

    /**
     * 抽取散牌单张。
     */
    private static void extractSingles(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
        for (Map.Entry<Integer, List<Card>> e : new ArrayList<>(pool.entrySet())) {
            List<Card> lst = e.getValue();
            while (lst != null && !lst.isEmpty()) {
                List<Card> s = new ArrayList<>();
                s.add(lst.remove(lst.size() - 1));
                int w = DdzAiConstants.SPLIT_WEIGHT_SINGLE + DdzAiConstants.splitSingleExtra(e.getKey());
                groups.add(new CardGroup(s, w));
            }
            removeEmpty(pool, e.getKey());
        }
    }
}
