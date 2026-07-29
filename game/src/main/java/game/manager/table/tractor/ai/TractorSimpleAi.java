package game.manager.table.tractor.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import game.manager.table.TableUser;
import game.manager.table.TractorTable;
import game.manager.table.cards.Card;
import game.manager.table.tractor.TractorRules;
import game.manager.table.tractor.TractorTableContext;

/**
 * 拖拉机出牌 AI。
 * <p>
 * 闲家：争吃带分墩，队友大时送分；庄家/庄队：尽量拿下带分墩，丢墩时不垫分，队友大时甩分消化。
 * 跟牌用牌力比较替代整墩 winnerSeat 重算，首出/垫牌用线性扫描代替全排序。
 */
public final class TractorSimpleAi {

	private TractorSimpleAi() {}

	public static List<Card> decide(TractorTable table, int seat) {
		TableUser user = table.getSeatUser(seat);
		if (user == null || user.getCards().isEmpty()) return new ArrayList<>();
		TractorTableContext ctx = table.getTractor();
		List<Card> hand = new ArrayList<>(user.getCards());
		if (ctx.getLeadCombo() == null) {
			return pickLead(hand, ctx, seat);
		}
		return pickFollow(hand, ctx, seat, table.getTableModel().getSeatNum());
	}

	private static List<Card> pickLead(List<Card> hand, TractorTableContext ctx, int seat) {
		int level = ctx.getLevelRank();
		int trump = ctx.getTrumpSuit();
		boolean banker = ctx.isBankerTeam(seat);
		boolean endgame = hand.size() <= 8;

		Map<Integer, List<Card>> bySuit = groupBySuit(hand, level, trump);
		int bestSuit = -1;
		int bestLen = -1;
		for (Map.Entry<Integer, List<Card>> e : bySuit.entrySet()) {
			if (e.getKey() == 0) continue;
			if (e.getValue().size() > bestLen) {
				bestLen = e.getValue().size();
				bestSuit = e.getKey();
			}
		}
		List<Card> pool;
		if (endgame && bySuit.containsKey(0) && !bySuit.get(0).isEmpty()) {
			pool = bySuit.get(0);
		} else if (bestSuit > 0) {
			pool = bySuit.get(bestSuit);
		} else {
			pool = hand;
		}

		Card best = null;
		int bestKey = Integer.MAX_VALUE;
		for (Card c : pool) {
			int key = leadPriority(c, level, trump, banker);
			if (key < bestKey) {
				bestKey = key;
				best = c;
			}
		}
		List<Card> out = new ArrayList<>(1);
		out.add(best != null ? best : pool.get(0));
		return out;
	}

	private static int leadPriority(Card c, int level, int trump, boolean banker) {
		int score = TractorRules.scoreOf(c);
		int p = TractorRules.power(c, level, trump);
		if (banker) return score * 1000 + p;
		return p + score * 5;
	}

	private static List<Card> pickFollow(List<Card> hand, TractorTableContext ctx, int seat, int seatNum) {
		TractorRules.Combo lead = ctx.getLeadCombo();
		int need = lead.cards.size();
		int level = ctx.getLevelRank();
		int trump = ctx.getTrumpSuit();
		boolean banker = ctx.isBankerTeam(seat);
		int partner = (seat + 2) % seatNum;
		int points = trickPoints(ctx);
		int curWin = currentWinnerSeat(ctx, level, trump);
		boolean partnerWinning = curWin == partner;
		boolean lastToPlay = ctx.getTrickPlays().size() + 1 >= seatNum;
		TractorRules.Combo currentBest = TractorRules.currentBestCombo(
				ctx.getTrickPlays(), lead, level, trump);

		List<Card> same = filterSuit(hand, lead.suitId, level, trump);
		boolean mustSame = !same.isEmpty();
		List<Card> src = mustSame ? same : hand;

		if (mustSame && (lead.type == TractorRules.ComboType.PAIR || lead.type == TractorRules.ComboType.TRACTOR)) {
			int needPairs = lead.type == TractorRules.ComboType.PAIR ? 1 : lead.tractorLen;
			List<List<Card>> pairs = listPairs(src, level, trump);
			if (pairs.size() >= needPairs) {
				return pickRequiredPairs(pairs, needPairs, hand, lead, currentBest, banker,
						partnerWinning, lastToPlay, points, level, trump);
			}
		}

		if (!mustSame) {
			List<Card> kill = tryKill(hand, lead, currentBest, level, trump, partnerWinning,
					lastToPlay, points);
			if (kill != null) return kill;
		}

		if (mustSame && lead.type == TractorRules.ComboType.SINGLE) {
			List<Card> win = trySameSuitWin(src, lead, currentBest, level, trump, banker,
					partnerWinning, lastToPlay, points);
			if (win != null) return win;
		}

		return pickPad(src, need, level, trump, partnerWinning, lastToPlay);
	}

	private static List<Card> trySameSuitWin(
			List<Card> same, TractorRules.Combo lead, TractorRules.Combo currentBest,
			int level, int trump, boolean banker, boolean partnerWinning, boolean lastToPlay, int points) {
		if (!shouldTryWin(banker, partnerWinning, lastToPlay, points)) return null;
		Card best = null;
		int bestPow = Integer.MAX_VALUE;
		for (Card c : same) {
			List<Card> play = singleton(c);
			if (!TractorRules.beatsCurrent(play, currentBest, lead, level, trump)) continue;
			int p = TractorRules.power(c, level, trump);
			if (p < bestPow) {
				bestPow = p;
				best = c;
			}
		}
		return best == null ? null : singleton(best);
	}

	private static List<Card> pickRequiredPairs(
			List<List<Card>> pairs, int needPairs, List<Card> hand, TractorRules.Combo lead,
			TractorRules.Combo currentBest, boolean banker, boolean partnerWinning, boolean lastToPlay,
			int points, int level, int trump) {
		List<List<Card>> candidates = lead.type == TractorRules.ComboType.PAIR
				? pairs
				: findTractors(pairs, needPairs, level, trump);
		if (candidates.isEmpty()) {
			return pickPad(filterSuit(hand, lead.suitId, level, trump), lead.cards.size(),
					level, trump, partnerWinning, lastToPlay);
		}

		boolean wantWin = shouldTryWin(banker, partnerWinning, lastToPlay, points);
		List<Card> bestWin = null;
		int bestWinPow = Integer.MAX_VALUE;
		List<Card> bestDump = null;
		int bestDumpKey = Integer.MAX_VALUE;
		for (List<Card> candidate : candidates) {
			int pow = TractorRules.power(candidate.get(0), level, trump);
			if (wantWin && TractorRules.beatsCurrent(candidate, currentBest, lead, level, trump)) {
				if (pow < bestWinPow) {
					bestWinPow = pow;
					bestWin = candidate;
				}
			}
			int key = dumpKey(candidate, level, trump, partnerWinning, lastToPlay);
			if (key < bestDumpKey) {
				bestDumpKey = key;
				bestDump = candidate;
			}
		}
		if (bestWin != null) return bestWin;
		return bestDump != null ? bestDump : candidates.get(0);
	}

	private static List<List<Card>> findTractors(List<List<Card>> pairs, int needPairs, int level, int trump) {
		List<List<Card>> result = new ArrayList<>();
		if (pairs.size() < needPairs) return result;
		int n = pairs.size();
		int[] steps = new int[n];
		for (int i = 0; i < n; i++) {
			steps[i] = tractorStep(pairs.get(i).get(0), level, trump);
		}
		Integer[] order = new Integer[n];
		for (int i = 0; i < n; i++) order[i] = i;
		java.util.Arrays.sort(order, Comparator.comparingInt(i -> steps[i]));

		for (int i = 0; i + needPairs <= n; i++) {
			boolean ok = true;
			for (int j = 1; j < needPairs; j++) {
				if (steps[order[i + j]] != steps[order[i]] + j) {
					ok = false;
					break;
				}
			}
			if (!ok) continue;
			List<Card> combo = new ArrayList<>(needPairs * 2);
			for (int j = 0; j < needPairs; j++) {
				combo.addAll(pairs.get(order[i + j]));
			}
			TractorRules.Combo parsed = TractorRules.analyze(combo, level, trump);
			if (parsed != null && parsed.type == TractorRules.ComboType.TRACTOR
					&& parsed.tractorLen == needPairs) {
				result.add(combo);
			}
		}
		return result;
	}

	private static int tractorStep(Card c, int level, int trump) {
		if (TractorRules.isJoker(c)) return c.isBigJoker() ? 100 : 99;
		if (TractorRules.isLevelCard(c, level)) {
			boolean main = trump > 0 && c.getCardSuit() != null && c.getCardSuit().getId() == trump;
			return main ? 98 : 97;
		}
		return c.getCardVal();
	}

	private static List<Card> tryKill(
			List<Card> hand, TractorRules.Combo lead, TractorRules.Combo currentBest,
			int level, int trump, boolean partnerWinning, boolean lastToPlay, int points) {
		if (partnerWinning && lastToPlay) return null;
		if (points < 5) return null;

		List<Card> trumps = filterSuit(hand, 0, level, trump);
		if (trumps.size() < lead.cards.size()) return null;

		if (lead.type == TractorRules.ComboType.SINGLE) {
			Card best = null;
			int bestPow = Integer.MAX_VALUE;
			for (Card c : trumps) {
				List<Card> play = singleton(c);
				if (!TractorRules.beatsCurrent(play, currentBest, lead, level, trump)) continue;
				int p = TractorRules.power(c, level, trump);
				if (p < bestPow) {
					bestPow = p;
					best = c;
				}
			}
			return best == null ? null : singleton(best);
		}
		if (lead.type == TractorRules.ComboType.PAIR) {
			List<Card> best = null;
			int bestPow = Integer.MAX_VALUE;
			for (List<Card> p : listPairs(trumps, level, trump)) {
				if (!TractorRules.beatsCurrent(p, currentBest, lead, level, trump)) continue;
				int pow = TractorRules.power(p.get(0), level, trump);
				if (pow < bestPow) {
					bestPow = pow;
					best = p;
				}
			}
			return best;
		}
		if (lead.type == TractorRules.ComboType.TRACTOR) {
			List<Card> best = null;
			int bestPow = Integer.MAX_VALUE;
			for (List<Card> cand : findTractors(listPairs(trumps, level, trump), lead.tractorLen, level, trump)) {
				if (!TractorRules.beatsCurrent(cand, currentBest, lead, level, trump)) continue;
				int pow = TractorRules.power(cand.get(0), level, trump);
				if (pow < bestPow) {
					bestPow = pow;
					best = cand;
				}
			}
			return best;
		}
		return null;
	}

	private static List<Card> pickPad(
			List<Card> src, int need, int level, int trump,
			boolean partnerWinning, boolean lastToPlay) {
		if (src.size() <= need) return new ArrayList<>(src);
		if (need == 1) {
			Card best = null;
			int bestKey = Integer.MAX_VALUE;
			for (Card c : src) {
				int key = dumpCardKey(c, level, trump, partnerWinning, lastToPlay);
				if (key < bestKey) {
					bestKey = key;
					best = c;
				}
			}
			return singleton(best);
		}
		List<Card> ranked = new ArrayList<>(src);
		ranked.sort(Comparator.comparingInt(c -> dumpCardKey(c, level, trump, partnerWinning, lastToPlay)));
		return new ArrayList<>(ranked.subList(0, need));
	}

	private static int dumpKey(List<Card> cards, int level, int trump,
			boolean partnerWinning, boolean lastToPlay) {
		int score = 0;
		int power = 0;
		for (Card c : cards) {
			score += TractorRules.scoreOf(c);
			power += TractorRules.power(c, level, trump);
		}
		if (partnerWinning && lastToPlay) return -score * 100 + power;
		return score * 1000 + power;
	}

	private static int dumpCardKey(Card c, int level, int trump,
			boolean partnerWinning, boolean lastToPlay) {
		int score = TractorRules.scoreOf(c);
		int power = TractorRules.power(c, level, trump);
		if (partnerWinning && lastToPlay) return -score * 100 + power;
		return score * 1000 + power;
	}

	private static boolean shouldTryWin(boolean banker, boolean partnerWinning, boolean lastToPlay, int points) {
		if (partnerWinning && lastToPlay) return false;
		if (partnerWinning && points < 10) return false;
		return points >= 5 || !partnerWinning;
	}

	private static int currentWinnerSeat(TractorTableContext ctx, int level, int trump) {
		if (ctx.getTrickPlays().isEmpty()) return ctx.getTrickLeader();
		return TractorRules.winnerSeat(ctx.getTrickPlays(), ctx.getTrickSeats(),
				ctx.getLeadCombo(), level, trump);
	}

	private static int trickPoints(TractorTableContext ctx) {
		int s = 0;
		for (List<Card> play : ctx.getTrickPlays()) {
			for (Card c : play) s += TractorRules.scoreOf(c);
		}
		return s;
	}

	private static Map<Integer, List<Card>> groupBySuit(List<Card> hand, int level, int trump) {
		Map<Integer, List<Card>> m = new HashMap<>(8);
		for (Card c : hand) {
			m.computeIfAbsent(TractorRules.suitGroup(c, level, trump), k -> new ArrayList<>()).add(c);
		}
		return m;
	}

	private static List<Card> filterSuit(List<Card> hand, int suitId, int level, int trump) {
		List<Card> out = new ArrayList<>();
		for (Card c : hand) {
			if (TractorRules.suitGroup(c, level, trump) == suitId) out.add(c);
		}
		return out;
	}

	private static List<List<Card>> listPairs(List<Card> cards, int level, int trump) {
		Map<Integer, List<Card>> buckets = new TreeMap<>();
		for (Card c : cards) {
			buckets.computeIfAbsent(pairBucket(c, level, trump), k -> new ArrayList<>()).add(c);
		}
		List<List<Card>> pairs = new ArrayList<>();
		for (List<Card> g : buckets.values()) {
			for (int i = 0; i + 1 < g.size(); i += 2) {
				List<Card> p = new ArrayList<>(2);
				p.add(g.get(i));
				p.add(g.get(i + 1));
				pairs.add(p);
			}
		}
		return pairs;
	}

	private static int pairBucket(Card c, int level, int trump) {
		if (TractorRules.isJoker(c)) return 10000 + c.getCardVal();
		if (TractorRules.isLevelCard(c, level)) {
			boolean main = trump > 0 && c.getCardSuit() != null && c.getCardSuit().getId() == trump;
			return 9000 + (main ? 10 : 0) + (c.getCardSuit() != null ? c.getCardSuit().getId() : 0);
		}
		int suit = c.getCardSuit() != null ? c.getCardSuit().getId() : 0;
		return suit * 100 + c.getCardVal();
	}

	private static List<Card> singleton(Card c) {
		List<Card> out = new ArrayList<>(1);
		out.add(c);
		return out;
	}
}
