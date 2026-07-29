package game.manager.table.ddz.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import game.manager.table.ai.AiSearchBudget;
import game.manager.table.cards.Card;
import game.manager.table.ddz.DdzHand;
import game.manager.table.ddz.DdzRules;
import proto.GameProto;

/** 斗地主大师档的限时候选与剩余手牌结构评估。 */
final class DdzMasterAi {
	private DdzMasterAi() {}

	static GameProto.OpInfo lead(List<Card> hand, int phase, AiSearchBudget budget) {
		List<DdzHand> candidates = leadCandidates(hand, budget);
		DdzHand best = null;
		double bestScore = Double.POSITIVE_INFINITY;
		for (DdzHand candidate : candidates) {
			if (!budget.tryVisit()) break;
			double score = scoreResidual(hand, candidate, phase, budget);
			if (score < bestScore) {
				bestScore = score;
				best = candidate;
			}
		}
		return best == null ? DdzSimpleAi.lead(hand, phase, null) : DdzSimpleAi.playHand(best);
	}

	static DdzHand pickBeat(List<Card> hand, List<DdzHand> beats,
			int minOppCards, AiSearchBudget budget) {
		DdzHand best = DdzSimpleAi.pickCheapestBeat(beats, minOppCards);
		double bestScore = Double.POSITIVE_INFINITY;
		for (DdzHand beat : beats) {
			if (!budget.tryVisit()) break;
			double score = scoreResidual(hand, beat, DdzSimpleAi.phaseOf(hand.size()), budget);
			if (beat.isBomb()) score += minOppCards <= 2 ? 20 : 180;
			if (beat.isRocket()) score += minOppCards <= 2 ? 30 : 260;
			if (score < bestScore) {
				bestScore = score;
				best = beat;
			}
		}
		return best;
	}

	private static double scoreResidual(List<Card> hand, DdzHand play,
			int phase, AiSearchBudget budget) {
		List<Card> remaining = DdzSimpleAi.removeCards(hand, play.getCards());
		if (remaining.isEmpty()) return -100000;
		double score = DdzSimpleAi.scoreLead(play, phase) + DdzSimpleAi.preserveHint(play);
		List<CardGroup> plan = DdzSplitPlanner.planBest(remaining);
		score += plan.size() * 120;
		for (CardGroup group : plan) {
			if (!budget.tryVisit()) break;
			Optional<DdzHand> analyzed = DdzRules.analyze(group.getCards());
			if (analyzed.isPresent()) score += DdzSimpleAi.preserveHint(analyzed.get()) * 0.2;
		}
		return score;
	}

	private static List<DdzHand> leadCandidates(List<Card> hand, AiSearchBudget budget) {
		List<DdzHand> result = new ArrayList<>();
		Set<Long> seen = new HashSet<>();
		for (CardGroup group : DdzSplitPlanner.planBest(hand)) {
			DdzSimpleAi.addLeadCandidate(group.getCards(), result, seen);
		}
		if (hand.size() <= 10) addSubsets(hand, result, seen, budget);
		else for (Card card : hand) {
			DdzSimpleAi.addLeadCandidate(Collections.singletonList(card), result, seen);
		}
		return result;
	}

	private static void addSubsets(List<Card> hand, List<DdzHand> result,
			Set<Long> seen, AiSearchBudget budget) {
		int combinations = 1 << hand.size();
		for (int mask = 1; mask < combinations && !budget.isExhausted(); mask++) {
			DdzHand candidate = analyzeSubset(hand, mask);
			if (candidate != null && seen.add(DdzLegalBeatFinder.hashHand(candidate))) result.add(candidate);
		}
	}

	private static DdzHand analyzeSubset(List<Card> hand, int mask) {
		List<Card> subset = new ArrayList<>();
		for (int i = 0; i < hand.size(); i++) {
			if ((mask & (1 << i)) != 0) subset.add(hand.get(i));
		}
		return DdzRules.analyze(subset).orElse(null);
	}
}
