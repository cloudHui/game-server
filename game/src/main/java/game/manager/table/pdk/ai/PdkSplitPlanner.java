package game.manager.table.pdk.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import game.manager.table.card.CardConst;
import game.manager.table.cards.Card;
import game.manager.table.ddz.ai.CardGroup;

/**
 * 跑得快手牌启发式拆牌规划器。
 * <p>
 * 拆牌优先级流水线：
 * <ol>
 *   <li><b>炸弹抽取：</b>识别 4 张同点数的天然炸弹并打上最高保留权值；</li>
 *   <li><b>三带结构抽取：</b>三带二优先，其次三带一，翅膀优先选取最小且张数吻合的零散牌；</li>
 *   <li><b>长顺抽取：</b>寻找 3~A 之间最长的单顺（>=5 张）；</li>
 *   <li><b>连对抽取：</b>寻找 3~A 之间最长的双顺/连对（>=2 对）；</li>
 *   <li><b>三不带、对子与单牌：</b>收尾提纯，顶张大单（K/A/2）附加高保留分以防被轻易作为垫牌打出。</li>
 * </ol>
 * <p>
 * 通过 {@link #planBest(List)} 还可以尝试“主动拆开部分炸弹”的探索分支，在某些情况下（如炸弹拆开后能多凑出大顺子或三带），
 * 选取总出牌手数（组数）更少的变体方案。
 */
public final class PdkSplitPlanner {

    private PdkSplitPlanner() {}

    /**
     * 默认基准拆牌规划（保留所有天然炸弹不拆）。
     *
     * @param hand 待拆解手牌
     * @return 拆出的卡牌组合列表
     */
    public static List<CardGroup> plan(List<Card> hand) {
        return planWithSplitBombs(hand, Collections.emptySet());
    }

    /**
     * 全局最优拆牌规划。
     * <p>
     * 对比不拆炸弹、单拆某个炸弹、以及全拆炸弹的所有候选方案，选取综合代价（组数最少且结构保留价值最高）最优解。
     *
     * @param hand 当前手牌
     * @return 最优卡牌分组方案
     */
    public static List<CardGroup> planBest(List<Card> hand) {
        // 1. 基准方案：保留所有炸弹
        List<CardGroup> best = plan(hand);
        double bestCost = splitCost(best);

        // 2. 变体搜索：尝试单独拆开每一个炸弹，观察能否将散牌组成更长顺子或三带
        List<Integer> bombRanks = bombRanksOf(hand);
        for (int rank : bombRanks) {
            List<CardGroup> alt = planWithSplitBombs(hand, Collections.singleton(rank));
            double cost = splitCost(alt);
            if (cost < bestCost) {
                bestCost = cost;
                best = alt;
            }
        }

        // 3. 极限变体：当有多个炸弹时，尝试同时允许拆解全部炸弹进行全局重组
        if (bombRanks.size() > 1) {
            List<CardGroup> alt = planWithSplitBombs(hand, new HashSet<>(bombRanks));
            if (splitCost(alt) < bestCost) {
                best = alt;
            }
        }
        return best;
    }

    /**
     * 计算拆牌方案的综合评价值。
     * <p>
     * 目标：组数（出牌轮次）越少越优；在组数相同时，手牌中保留的高价值牌型分值越小表示出牌更顺畅。
     *
     * @param groups 拆出的卡牌分组列表
     * @return 方案评估代价值（越低越优）
     */
    private static double splitCost(List<CardGroup> groups) {
        double preserve = 0;
        for (CardGroup g : groups) {
            preserve += g.getPreserveScore();
        }
        // 组数占据绝对主导权（权重 20.0），保留分仅微调 tie-breaker（权重 0.01）
        return groups.size() * 20.0 + preserve * 0.01;
    }

    /**
     * 统计手牌中达到 4 张及以上的所有炸弹点数。
     *
     * @param hand 当前手牌
     * @return 炸弹点数列表
     */
    private static List<Integer> bombRanksOf(List<Card> hand) {
        TreeMap<Integer, Integer> cnt = new TreeMap<>();
        for (Card c : hand) {
            cnt.merge(c.getCardVal(), 1, Integer::sum);
        }
        List<Integer> ranks = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : cnt.entrySet()) {
            if (e.getValue() >= 4) {
                ranks.add(e.getKey());
            }
        }
        return ranks;
    }

    /**
     * 执行指定可拆炸弹集合的流水线拆牌。
     *
     * @param hand       当前手牌
     * @param splitBombs 允许拆解的炸弹点数集合（集合内的炸弹不会被当作整炸弹优先提取）
     * @return 最终拆解出的牌组列表
     */
    private static List<CardGroup> planWithSplitBombs(List<Card> hand, Set<Integer> splitBombs) {
        List<CardGroup> groups = new ArrayList<>();
        // 按卡牌点数桶装分发，利用 TreeMap 保持点数天然升序
        TreeMap<Integer, List<Card>> pool = new TreeMap<>();
        for (Card c : hand) {
            pool.computeIfAbsent(c.getCardVal(), k -> new ArrayList<>()).add(c);
        }

        // 依序流水线提取各类结构牌
        extractBombs(pool, groups, splitBombs);
        extractTripleWings(pool, groups, 2); // 跑得快三带二优先
        extractTripleWings(pool, groups, 1); // 其次三带一
        extractStraights(pool, groups);      // 单顺
        extractDoubleStraights(pool, groups);// 连对
        extractTriples(pool, groups);        // 剩余三不带
        extractPairs(pool, groups);          // 剩余对子
        extractSingles(pool, groups);        // 剩余散单
        return groups;
    }

    /**
     * 从点数池中提取完整 4 张炸弹并加入牌组。
     *
     * @param pool       点数卡牌池
     * @param groups     拆牌收集列表
     * @param splitBombs 允许拆解的点数集合（跳过提取）
     */
    private static void extractBombs(
            TreeMap<Integer, List<Card>> pool, List<CardGroup> groups, Set<Integer> splitBombs) {
        for (Integer rank : new ArrayList<>(pool.keySet())) {
            if (splitBombs.contains(rank)) {
                continue;
            }
            List<Card> lst = pool.get(rank);
            while (lst != null && lst.size() >= 4) {
                groups.add(new CardGroup(take(lst, 4), PdkAiConstants.SPLIT_WEIGHT_BOMB));
            }
            removeEmpty(pool, rank);
        }
    }

    /**
     * 提取三带牌型（三带二或三带一）。
     *
     * @param pool     点数卡牌池
     * @param groups   拆牌收集列表
     * @param wingNeed 翅膀张数（2 为三带二，1 为三带一）
     */
    private static void extractTripleWings(
            TreeMap<Integer, List<Card>> pool, List<CardGroup> groups, int wingNeed) {
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Integer tripleRank : new ArrayList<>(pool.keySet())) {
                List<Card> triples = pool.get(tripleRank);
                if (triples == null || triples.size() < 3) {
                    continue;
                }

                // 寻找最合适的带牌（翅膀）点数
                Integer wingRank = findWingRank(pool, tripleRank, wingNeed);
                if (wingRank == null) {
                    continue;
                }

                // 组装三带牌型
                List<Card> combo = new ArrayList<>(3 + wingNeed);
                combo.addAll(take(triples, 3));
                combo.addAll(take(pool.get(wingRank), wingNeed));
                removeEmpty(pool, tripleRank);
                removeEmpty(pool, wingRank);

                int weight = wingNeed == 2
                        ? PdkAiConstants.SPLIT_WEIGHT_TRIPLE_DOUBLE
                        : PdkAiConstants.SPLIT_WEIGHT_TRIPLE_ONE;
                groups.add(new CardGroup(combo, weight));
                progress = true;
                break;
            }
        }
    }

    /**
     * 为三张主体寻找最优质的翅膀点数。
     * <p>
     * 策略：严格避开 4 张天然炸弹；优先精确匹配刚好等于 need 张数的散牌点数（不破坏其他结构）；退而求其次选择最小点数。
     *
     * @param pool       点数卡牌池
     * @param tripleRank 三张主体的点数
     * @param need       所需的翅膀张数
     * @return 选中的翅膀点数，若无合法翅膀则返回 null
     */
    private static Integer findWingRank(TreeMap<Integer, List<Card>> pool, int tripleRank, int need) {
        Integer exact = null;
        Integer any = null;
        for (Map.Entry<Integer, List<Card>> e : pool.entrySet()) {
            int rank = e.getKey();
            int size = e.getValue().size();
            if (rank == tripleRank || size < need) {
                continue;
            }
            if (size == 4) {
                continue; // 炸弹坚决不作为翅膀牺牲
            }
            if (size == need) {
                // 刚好吻合所需张数，优先采纳并选较小点数
                if (exact == null || rank < exact) {
                    exact = rank;
                }
            } else if (any == null || rank < any) {
                any = rank;
            }
        }
        return exact != null ? exact : any;
    }

    /**
     * 提取最长单顺子（>=5 张连续）。
     *
     * @param pool   点数卡牌池
     * @param groups 拆牌收集列表
     */
    private static void extractStraights(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
        boolean progress = true;
        while (progress) {
            progress = false;
            List<Integer> run = longestSingleRun(pool);
            if (run.size() < 5) {
                return;
            }
            List<Card> seq = new ArrayList<>(run.size());
            for (int r : run) {
                seq.add(pool.get(r).remove(pool.get(r).size() - 1));
                removeEmpty(pool, r);
            }
            int w = PdkAiConstants.SPLIT_WEIGHT_STRAIGHT_MIN_BONUS
                    + seq.size() * PdkAiConstants.SPLIT_WEIGHT_STRAIGHT_PER_CARD;
            groups.add(new CardGroup(seq, w));
            progress = true;
        }
    }

    /**
     * 提取最长双顺/连对（>=2 对连续）。
     *
     * @param pool   点数卡牌池
     * @param groups 拆牌收集列表
     */
    private static void extractDoubleStraights(
            TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
        boolean progress = true;
        while (progress) {
            progress = false;
            List<Integer> run = longestPairRun(pool);
            if (run.size() < 2) {
                return;
            }
            List<Card> seq = new ArrayList<>(run.size() * 2);
            for (int r : run) {
                seq.addAll(take(pool.get(r), 2));
                removeEmpty(pool, r);
            }
            int w = PdkAiConstants.SPLIT_WEIGHT_STRAIGHT_DOUBLE_MIN_BONUS
                    + run.size() * PdkAiConstants.SPLIT_WEIGHT_STRAIGHT_DOUBLE_PER_PAIR;
            groups.add(new CardGroup(seq, w));
            progress = true;
        }
    }

    /**
     * 在点数池中搜索 3 到 A 的最长连续单张段（2 不能连入顺子）。
     *
     * @param pool 点数卡牌池
     * @return 最长连续点数列表，若不足 5 张则返回空列表
     */
    private static List<Integer> longestSingleRun(TreeMap<Integer, List<Card>> pool) {
        List<Integer> best = Collections.emptyList();
        List<Integer> cur = new ArrayList<>();
        for (int r = 3; r < CardConst.ER_VAL; r++) {
            List<Card> g = pool.get(r);
            if (g != null && !g.isEmpty()) {
                cur.add(r);
            } else {
                if (cur.size() > best.size()) {
                    best = new ArrayList<>(cur);
                }
                cur.clear();
            }
        }
        if (cur.size() > best.size()) {
            best = cur;
        }
        return best.size() >= 5 ? best : Collections.emptyList();
    }

    /**
     * 在点数池中搜索 3 到 A 的最长连续对子段（2 不能连入连对）。
     *
     * @param pool 点数卡牌池
     * @return 最长连续对子点数列表，若不足 2 对则返回空列表
     */
    private static List<Integer> longestPairRun(TreeMap<Integer, List<Card>> pool) {
        List<Integer> best = Collections.emptyList();
        List<Integer> cur = new ArrayList<>();
        for (int r = 3; r < CardConst.ER_VAL; r++) {
            List<Card> g = pool.get(r);
            if (g != null && g.size() >= 2) {
                cur.add(r);
            } else {
                if (cur.size() > best.size()) {
                    best = new ArrayList<>(cur);
                }
                cur.clear();
            }
        }
        if (cur.size() > best.size()) {
            best = cur;
        }
        return best.size() >= 2 ? best : Collections.emptyList();
    }

    /**
     * 提取无法成顺成带的剩余三张牌（三不带）。
     *
     * @param pool   点数卡牌池
     * @param groups 拆牌收集列表
     */
    private static void extractTriples(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
        for (Integer rank : new ArrayList<>(pool.keySet())) {
            List<Card> lst = pool.get(rank);
            while (lst != null && lst.size() >= 3) {
                groups.add(new CardGroup(take(lst, 3), PdkAiConstants.SPLIT_WEIGHT_TRIPLE));
            }
            removeEmpty(pool, rank);
        }
    }

    /**
     * 提取剩余对子牌型。
     *
     * @param pool   点数卡牌池
     * @param groups 拆牌收集列表
     */
    private static void extractPairs(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
        for (Integer rank : new ArrayList<>(pool.keySet())) {
            List<Card> lst = pool.get(rank);
            while (lst != null && lst.size() >= 2) {
                groups.add(new CardGroup(take(lst, 2), PdkAiConstants.SPLIT_WEIGHT_PAIR));
            }
            removeEmpty(pool, rank);
        }
    }

    /**
     * 提取剩余散牌，并对大顶单（K/A/2）增加保留权重。
     *
     * @param pool   点数卡牌池
     * @param groups 拆牌收集列表
     */
    private static void extractSingles(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
        for (Integer rank : new ArrayList<>(pool.keySet())) {
            List<Card> lst = pool.get(rank);
            while (lst != null && !lst.isEmpty()) {
                Card c = lst.remove(lst.size() - 1);
                int w = PdkAiConstants.SPLIT_WEIGHT_SINGLE;
                // 大顶单牌具备重要的控场与压制价值，赋予额外保留分
                if (PdkAiConstants.isTopSingle(c.getCardVal())) {
                    w += PdkAiConstants.SPLIT_WEIGHT_SINGLE_TOP_EXTRA;
                }
                groups.add(new CardGroup(Collections.singletonList(c), w));
            }
            removeEmpty(pool, rank);
        }
    }

    /**
     * 从列表中弹出最后 n 张卡牌。
     *
     * @param lst 来源卡牌列表
     * @param n   取出数量
     * @return 弹出的卡牌子集
     */
    private static List<Card> take(List<Card> lst, int n) {
        List<Card> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(lst.remove(lst.size() - 1));
        }
        return out;
    }

    /**
     * 若指定点数的卡牌已被取空，则从桶中移除该点数键。
     *
     * @param pool 点数卡牌池
     * @param rank 点数值
     */
    private static void removeEmpty(TreeMap<Integer, List<Card>> pool, int rank) {
        List<Card> l = pool.get(rank);
        if (l != null && l.isEmpty()) {
            pool.remove(rank);
        }
    }
}
