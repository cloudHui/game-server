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
 * 跑得快拆牌：炸弹→三带二→三带一→顺→连对→三不带→对→单。
 * planBest 会尝试拆炸弹变体，取组数更少的方案。
 */
public final class PdkSplitPlanner {

	private PdkSplitPlanner() {}

	public static List<CardGroup> plan(List<Card> hand) {
		return planWithSplitBombs(hand, Collections.emptySet());
	}

	/** 多拆法搜索：默认 + 拆每种/全部炸弹，取代价更低者。 */
	public static List<CardGroup> planBest(List<Card> hand) {
		List<CardGroup> best = plan(hand);
		double bestCost = splitCost(best);
		List<Integer> bombRanks = bombRanksOf(hand);
		for (int rank : bombRanks) {
			List<CardGroup> alt = planWithSplitBombs(hand, Collections.singleton(rank));
			double cost = splitCost(alt);
			if (cost < bestCost) {
				bestCost = cost;
				best = alt;
			}
		}
		if (bombRanks.size() > 1) {
			List<CardGroup> alt = planWithSplitBombs(hand, new HashSet<>(bombRanks));
			if (splitCost(alt) < bestCost) best = alt;
		}
		return best;
	}

	private static double splitCost(List<CardGroup> groups) {
		double preserve = 0;
		for (CardGroup g : groups) preserve += g.getPreserveScore();
		return groups.size() * 20.0 + preserve * 0.01;
	}

	private static List<Integer> bombRanksOf(List<Card> hand) {
		TreeMap<Integer, Integer> cnt = new TreeMap<>();
		for (Card c : hand) cnt.merge(c.getCardVal(), 1, Integer::sum);
		List<Integer> ranks = new ArrayList<>();
		for (Map.Entry<Integer, Integer> e : cnt.entrySet()) {
			if (e.getValue() >= 4) ranks.add(e.getKey());
		}
		return ranks;
	}

	private static List<CardGroup> planWithSplitBombs(List<Card> hand, Set<Integer> splitBombs) {
		List<CardGroup> groups = new ArrayList<>();
		TreeMap<Integer, List<Card>> pool = new TreeMap<>();
		for (Card c : hand) {
			pool.computeIfAbsent(c.getCardVal(), k -> new ArrayList<>()).add(c);
		}
		extractBombs(pool, groups, splitBombs);
		extractTripleWings(pool, groups, 2);
		extractTripleWings(pool, groups, 1);
		extractStraights(pool, groups);
		extractDoubleStraights(pool, groups);
		extractTriples(pool, groups);
		extractPairs(pool, groups);
		extractSingles(pool, groups);
		return groups;
	}

	private static void extractBombs(
			TreeMap<Integer, List<Card>> pool, List<CardGroup> groups, Set<Integer> splitBombs) {
		for (Integer rank : new ArrayList<>(pool.keySet())) {
			if (splitBombs.contains(rank)) continue;
			List<Card> lst = pool.get(rank);
			while (lst != null && lst.size() >= 4) {
				groups.add(new CardGroup(take(lst, 4), PdkAiConstants.SPLIT_WEIGHT_BOMB));
			}
			removeEmpty(pool, rank);
		}
	}

	/** wingNeed=2 三带二；=1 三带一。翅膀优先取最小整组。 */
	private static void extractTripleWings(
			TreeMap<Integer, List<Card>> pool, List<CardGroup> groups, int wingNeed) {
		boolean progress = true;
		while (progress) {
			progress = false;
			for (Integer tripleRank : new ArrayList<>(pool.keySet())) {
				List<Card> triples = pool.get(tripleRank);
				if (triples == null || triples.size() < 3) continue;
				Integer wingRank = findWingRank(pool, tripleRank, wingNeed);
				if (wingRank == null) continue;
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

	private static Integer findWingRank(TreeMap<Integer, List<Card>> pool, int tripleRank, int need) {
		Integer exact = null;
		Integer any = null;
		for (Map.Entry<Integer, List<Card>> e : pool.entrySet()) {
			int rank = e.getKey();
			int size = e.getValue().size();
			if (rank == tripleRank || size < need) continue;
			if (size == 4) continue; // 炸弹留给 extractBombs / 拆炸变体
			if (size == need) {
				if (exact == null || rank < exact) exact = rank;
			} else if (any == null || rank < any) {
				any = rank;
			}
		}
		return exact != null ? exact : any;
	}

	private static void extractStraights(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
		boolean progress = true;
		while (progress) {
			progress = false;
			List<Integer> run = longestSingleRun(pool);
			if (run.size() < 5) return;
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

	private static void extractDoubleStraights(
			TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
		boolean progress = true;
		while (progress) {
			progress = false;
			List<Integer> run = longestPairRun(pool);
			if (run.size() < 2) return;
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

	private static List<Integer> longestSingleRun(TreeMap<Integer, List<Card>> pool) {
		List<Integer> best = Collections.emptyList();
		List<Integer> cur = new ArrayList<>();
		for (int r = 3; r < CardConst.ER_VAL; r++) {
			List<Card> g = pool.get(r);
			if (g != null && !g.isEmpty()) {
				cur.add(r);
			} else {
				if (cur.size() > best.size()) best = new ArrayList<>(cur);
				cur.clear();
			}
		}
		if (cur.size() > best.size()) best = cur;
		return best.size() >= 5 ? best : Collections.emptyList();
	}

	private static List<Integer> longestPairRun(TreeMap<Integer, List<Card>> pool) {
		List<Integer> best = Collections.emptyList();
		List<Integer> cur = new ArrayList<>();
		for (int r = 3; r < CardConst.ER_VAL; r++) {
			List<Card> g = pool.get(r);
			if (g != null && g.size() >= 2) {
				cur.add(r);
			} else {
				if (cur.size() > best.size()) best = new ArrayList<>(cur);
				cur.clear();
			}
		}
		if (cur.size() > best.size()) best = cur;
		return best.size() >= 2 ? best : Collections.emptyList();
	}

	private static void extractTriples(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
		for (Integer rank : new ArrayList<>(pool.keySet())) {
			List<Card> lst = pool.get(rank);
			while (lst != null && lst.size() >= 3) {
				groups.add(new CardGroup(take(lst, 3), PdkAiConstants.SPLIT_WEIGHT_TRIPLE));
			}
			removeEmpty(pool, rank);
		}
	}

	private static void extractPairs(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
		for (Integer rank : new ArrayList<>(pool.keySet())) {
			List<Card> lst = pool.get(rank);
			while (lst != null && lst.size() >= 2) {
				groups.add(new CardGroup(take(lst, 2), PdkAiConstants.SPLIT_WEIGHT_PAIR));
			}
			removeEmpty(pool, rank);
		}
	}

	private static void extractSingles(TreeMap<Integer, List<Card>> pool, List<CardGroup> groups) {
		for (Integer rank : new ArrayList<>(pool.keySet())) {
			List<Card> lst = pool.get(rank);
			while (lst != null && !lst.isEmpty()) {
				Card c = lst.remove(lst.size() - 1);
				int w = PdkAiConstants.SPLIT_WEIGHT_SINGLE;
				if (PdkAiConstants.isTopSingle(c.getCardVal())) {
					w += PdkAiConstants.SPLIT_WEIGHT_SINGLE_TOP_EXTRA;
				}
				groups.add(new CardGroup(Collections.singletonList(c), w));
			}
			removeEmpty(pool, rank);
		}
	}

	private static List<Card> take(List<Card> lst, int n) {
		List<Card> out = new ArrayList<>(n);
		for (int i = 0; i < n; i++) out.add(lst.remove(lst.size() - 1));
		return out;
	}

	private static void removeEmpty(TreeMap<Integer, List<Card>> pool, int rank) {
		List<Card> l = pool.get(rank);
		if (l != null && l.isEmpty()) pool.remove(rank);
	}
}
