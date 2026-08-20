package com.cloud.game.manager.table.pdk.ai;

import com.cloud.game.manager.table.TableUser;
import com.cloud.game.manager.table.card.CardConst;
import com.cloud.game.manager.table.cards.Card;
import com.cloud.game.manager.table.ddz.DdzHand;
import com.cloud.game.manager.table.ddz.ai.AiVision;
import com.cloud.game.manager.table.pdk.PdkRules;
import com.cloud.game.manager.table.pdk.PdkTable;
import com.cloud.game.manager.table.pdk.PdkTableContext;
import proto.ConstProto;
import proto.GameProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 跑得快 AI 入口：按 {@link PdkTableContext#getAiLevel()} 分流。
 * <p>
 * 默认大师档走 {@link PdkMasterAi}（拆牌+剩余结构评估，参照斗地主最高级）；
 * 高级/基础走本类启发式；Dumb 出最小合法牌。
 */
public final class PdkSimpleAi {

	private static final int ENDGAME_MAX = 6;
	private static final int ENDGAME_NODE_BUDGET = 350;
	private static final int DANGER_OPP_CARDS = 2;
	/** 跟牌同型候选最多评估数量（已按从小到大排），炸弹另算。 */
	private static final int MAX_SAME_TYPE_EVAL = 8;

	private PdkSimpleAi() {}

	public static GameProto.OpInfo decide(PdkTable table, TableUser user) {
		List<Card> hand = new ArrayList<>(user.getCards());
		if (hand.isEmpty()) return pass();
		Collections.sort(hand);
		int aiLevel = table.getPdk().getAiLevel();
		if (aiLevel >= AiVision.AI_MASTER) {
			return PdkMasterAi.decide(table, user);
		}
		if (aiLevel <= AiVision.AI_DUMB) {
			return decideDumb(hand, table.getPdk().getLastHand());
		}
		return decideBasic(table, user, hand);
	}

	/** 高级/基础：原启发式（终局搜索 + 残局组数估计）。 */
	private static GameProto.OpInfo decideBasic(PdkTable table, TableUser user, List<Card> hand) {
		int minOpp = minOpponentCards(table, user);
		DdzHand last = table.getPdk().getLastHand();
		if (last == null) {
			return playHand(pickLead(hand, minOpp));
		}
		List<DdzHand> beats = PdkRules.findAllBeatingHands(hand, last);
		if (beats.isEmpty()) return pass();
		return playHand(pickBeat(hand, beats, minOpp));
	}

	/** 最低级：能管则出最小压制，首出最小单；管不上才过。 */
	private static GameProto.OpInfo decideDumb(List<Card> hand, DdzHand last) {
		if (last != null) {
			List<DdzHand> beats = PdkRules.findAllBeatingHands(hand, last);
			if (beats.isEmpty()) return pass();
			return playHand(beats.get(0));
		}
		Card smallest = hand.get(hand.size() - 1);
		return playHand(PdkRules.analyze(Collections.singletonList(smallest)).orElse(null));
	}

	static int phaseOf(int handSize) {
		if (handSize >= PdkAiConstants.PHASE_EARLY_MIN_CARDS) return 0;
		if (handSize >= PdkAiConstants.PHASE_MID_MIN_CARDS) return 1;
		return 2;
	}

	static DdzHand pickLead(List<Card> hand, int minOpp) {
		DdzHand whole = PdkRules.analyze(hand).orElse(null);
		if (whole != null) return whole;

		if (hand.size() <= ENDGAME_MAX) {
			DdzHand end = endgameFirst(hand);
			if (end != null) return end;
		}

		List<DdzHand> candidates = PdkRules.enumerateLeadHands(hand);
		if (candidates.isEmpty()) {
			Card smallest = hand.get(hand.size() - 1);
			return PdkRules.analyze(Collections.singletonList(smallest)).orElse(null);
		}

		int[] counts = countByValue(hand);
		DdzHand best = null;
		double bestScore = Double.MAX_VALUE;
		for (DdzHand h : candidates) {
			double sc = scoreLead(hand, h, minOpp, counts);
			if (sc < bestScore) {
				bestScore = sc;
				best = h;
			}
		}
		return best;
	}

	private static double scoreLead(List<Card> hand, DdzHand h, int minOpp, int[] counts) {
		List<Card> left = removeCards(hand, h.getCards());
		int residual = estimateGroups(left);
		double score = residual * 40.0;
		score -= h.getCards().size() * 6.0;
		score += h.getStrengthKey() * 0.35;
		if (h.isBomb()) {
			score += minOpp <= DANGER_OPP_CARDS ? -30 : 80;
		} else if (breaksBomb(counts, h)) {
			score += 25;
		}
		if (left.isEmpty()) score -= 1000;
		if (residual == 1 && !left.isEmpty()) score -= 50;
		if (h.getType() == ConstProto.CardType.SINGLE && h.getStrengthKey() >= 13) {
			score += 12;
		}
		if (h.getType() == ConstProto.CardType.STRAIGHT
				|| h.getType() == ConstProto.CardType.STRAIGHT_DOUBLE
				|| h.getType() == ConstProto.CardType.TRIPLE_ONE
				|| h.getType() == ConstProto.CardType.TRIPLE_DOUBLE) {
			score -= 8;
		}
		return score;
	}

	private static DdzHand pickBeat(List<Card> hand, List<DdzHand> beats, int minOpp) {
		for (DdzHand h : beats) {
			if (h.getCards().size() == hand.size()) return h;
		}

		boolean danger = minOpp <= DANGER_OPP_CARDS;
		int[] counts = countByValue(hand);
		DdzHand best = null;
		double bestScore = Double.MAX_VALUE;
		int sameTypeSeen = 0;
		for (DdzHand h : beats) {
			if (!h.isBomb()) {
				sameTypeSeen++;
				if (sameTypeSeen > MAX_SAME_TYPE_EVAL) continue;
			} else if (!danger && best != null && !best.isBomb()) {
				continue;
			}
			double sc = h.getStrengthKey();
			List<Card> left = removeCards(hand, h.getCards());
			sc += estimateGroups(left) * 18.0;
			sc -= h.getCards().size() * 3.0;
			if (h.isBomb()) {
				sc += danger ? -40 : 120;
			} else if (breaksBomb(counts, h)) {
				sc += 35;
			} else if (h.getType() == ConstProto.CardType.SINGLE && counts[h.getStrengthKey()] >= 2) {
				sc += 8;
			}
			if (left.isEmpty()) sc -= 1000;
			if (sc < bestScore) {
				bestScore = sc;
				best = h;
			}
		}
		return best != null ? best : beats.get(0);
	}

	private static boolean breaksBomb(int[] counts, DdzHand play) {
		if (play.isBomb()) return false;
		for (Card c : play.getCards()) {
			int v = c.getCardVal();
			if (v >= 0 && v < counts.length && counts[v] == 4) return true;
		}
		return false;
	}

	private static int[] countByValue(List<Card> hand) {
		int[] counts = new int[CardConst.ER_VAL + 1];
		for (Card c : hand) {
			int v = c.getCardVal();
			if (v >= 0 && v < counts.length) counts[v]++;
		}
		return counts;
	}

	private static int estimateGroups(List<Card> cards) {
		if (cards == null || cards.isEmpty()) return 0;
		int[] counts = countByValue(cards);
		int groups = 0;
		List<Integer> singles = new ArrayList<>(12);
		List<Integer> pairs = new ArrayList<>(8);
		for (int v = 3; v <= CardConst.ER_VAL; v++) {
			int n = counts[v];
			if (n <= 0) continue;
			if (n >= 4) { groups++; n -= 4; }
			if (n >= 3) { groups++; n -= 3; }
			if (n >= 2) { pairs.add(v); n -= 2; }
			if (n >= 1) singles.add(v);
		}
		groups += takeStraightRuns(singles, 5);
		groups += singles.size();
		groups += takeStraightRuns(pairs, 2);
		groups += pairs.size();
		return Math.max(1, groups);
	}

	private static int takeStraightRuns(List<Integer> vals, int minLen) {
		int runs = 0;
		int i = 0;
		while (i < vals.size()) {
			int j = i + 1;
			while (j < vals.size() && vals.get(j) == vals.get(j - 1) + 1 && vals.get(j) < CardConst.ER_VAL) {
				j++;
			}
			if (j - i >= minLen) {
				runs++;
				vals.subList(i, j).clear();
			} else {
				i = j;
			}
		}
		return runs;
	}

	static DdzHand endgameFirst(List<Card> hand) {
		int[] nodes = { 0 };
		List<DdzHand> plays = PdkRules.enumerateLeadHands(hand);
		DdzHand best = null;
		int bestPlays = Integer.MAX_VALUE;
		for (DdzHand play : plays) {
			if (nodes[0] > ENDGAME_NODE_BUDGET) break;
			List<Card> left = removeCards(hand, play.getCards());
			int need = 1 + minPlays(left, bestPlays - 1, nodes);
			if (need < bestPlays) {
				bestPlays = need;
				best = play;
				if (bestPlays <= 1) break;
			}
		}
		return best;
	}

	private static int minPlays(List<Card> left, int cutoff, int[] nodes) {
		if (left.isEmpty()) return 0;
		if (cutoff <= 0) return cutoff + 1;
		if (++nodes[0] > ENDGAME_NODE_BUDGET) {
			return Math.max(cutoff, estimateGroups(left));
		}
		if (PdkRules.analyze(left).isPresent()) return 1;
		int best = cutoff + 1;
		for (DdzHand play : PdkRules.enumerateLeadHands(left)) {
			int r = 1 + minPlays(removeCards(left, play.getCards()), best - 1, nodes);
			if (r < best) {
				best = r;
				if (best <= 1) break;
			}
			if (nodes[0] > ENDGAME_NODE_BUDGET) break;
		}
		return best;
	}

	static int minOpponentCards(PdkTable table, TableUser me) {
		int min = Integer.MAX_VALUE;
		for (TableUser u : table.getSeatUsers().values()) {
			if (u == null || u.getUserId() == me.getUserId()) continue;
			min = Math.min(min, u.getCards().size());
		}
		return min == Integer.MAX_VALUE ? 99 : min;
	}

	static List<Card> removeCards(List<Card> hand, List<Card> play) {
		List<Card> left = new ArrayList<>(hand.size());
		left.addAll(hand);
		for (Card c : play) left.remove(c);
		return left;
	}

	static GameProto.OpInfo playHand(DdzHand h) {
		if (h == null) return pass();
		return GameProto.OpInfo.newBuilder()
				.setChoice(ConstProto.Operation.PLAY)
				.addOpCards(h.toCardInfo())
				.build();
	}

	static GameProto.OpInfo pass() {
		return GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PASS).build();
	}
}
