package game.manager.table.pdk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import game.manager.table.card.CardConst;
import game.manager.table.cards.Card;
import game.manager.table.ddz.DdzHand;
import proto.ConstProto;

/**
 * 跑得快牌型识别与比大小。
 * <p>
 * 支持：单、对、三不带、三带二、顺≥5、连对≥2、炸弹(四张)。2 与 A 不进顺/连对。
 */
public final class PdkRules {

	private PdkRules() {}

	public static Optional<DdzHand> analyze(List<Card> cards) {
		if (cards == null || cards.isEmpty()) return Optional.empty();
		List<Card> sorted = new ArrayList<>(cards);
		sorted.sort(Collections.reverseOrder());
		int n = sorted.size();
		Map<Integer, Long> cnt = sorted.stream()
				.collect(Collectors.groupingBy(Card::getCardVal, TreeMap::new, Collectors.counting()));

		Optional<DdzHand> bomb = tryBomb(sorted, cnt);
		if (bomb.isPresent()) return bomb;

		if (cnt.size() == 1 && n == 1) {
			return Optional.of(new DdzHand(ConstProto.CardType.SINGLE, sorted, false, false,
					sorted.get(0).getCardVal(), 0));
		}
		if (cnt.size() == 1 && n == 2) {
			return Optional.of(new DdzHand(ConstProto.CardType.DOUBLE, sorted, false, false,
					sorted.get(0).getCardVal(), 0));
		}
		if (cnt.size() == 1 && n == 3) {
			return Optional.of(new DdzHand(ConstProto.CardType.TRIPLE, sorted, false, false,
					sorted.get(0).getCardVal(), 0));
		}

		Optional<DdzHand> straight = tryStraight(sorted, cnt);
		if (straight.isPresent()) return straight;

		Optional<DdzHand> dStraight = tryDoubleStraight(sorted, cnt);
		if (dStraight.isPresent()) return dStraight;

		Optional<DdzHand> t2 = tryTriplePair(cnt, sorted);
		if (t2.isPresent()) return t2;

		return Optional.empty();
	}

	public static boolean beats(DdzHand incoming, DdzHand last) {
		if (incoming == null) return false;
		if (last == null || last.getCards().isEmpty()) return true;
		if (incoming.isBomb()) {
			if (!last.isBomb()) return true;
			return incoming.getStrengthKey() > last.getStrengthKey();
		}
		if (last.isBomb()) return false;
		if (incoming.getType() != last.getType()) return false;
		if (incoming.getType() == ConstProto.CardType.STRAIGHT
				|| incoming.getType() == ConstProto.CardType.STRAIGHT_DOUBLE) {
			if (incoming.getStraightLen() != last.getStraightLen()) return false;
		}
		return incoming.getStrengthKey() > last.getStrengthKey();
	}

	/** 查找手牌中一组能压过上一手的牌；跑得快据此执行“有牌必管”。短路返回，供 canBeat 热路径。 */
	public static Optional<List<Card>> findBeatingCards(List<Card> cards, DdzHand last) {
		if (cards == null || cards.isEmpty() || last == null) return Optional.empty();
		Map<Integer, List<Card>> groups = groupByValue(cards);
		if (!last.isBomb()) {
			Optional<List<Card>> sameType = findSameTypeBeat(groups, last);
			if (sameType.isPresent()) return sameType;
		}
		return findBomb(groups, last.isBomb() ? last.getStrengthKey() : 2);
	}

	/** 枚举所有能压过上一手的合法牌型（同型由小到大，炸弹在后）。AI 选牌用。 */
	public static List<DdzHand> findAllBeatingHands(List<Card> cards, DdzHand last) {
		List<DdzHand> result = new ArrayList<>();
		if (cards == null || cards.isEmpty() || last == null) return result;
		Map<Integer, List<Card>> groups = groupByValue(cards);
		if (!last.isBomb()) {
			collectSameTypeBeats(groups, last, result);
		}
		int bombMin = last.isBomb() ? last.getStrengthKey() : 2;
		for (int value = bombMin + 1; value <= CardConst.ER_VAL; value++) {
			List<Card> group = groups.get(value);
			if (group != null && group.size() == 4) {
				result.add(handOf(ConstProto.CardType.BOOM, group, value, 0, true));
			}
		}
		return result;
	}

	public static boolean canBeat(List<Card> cards, DdzHand last) {
		return findBeatingCards(cards, last).isPresent();
	}

	/**
	 * 枚举手牌中可作为首出的合法牌型。
	 * 直接构造 DdzHand，避免对每个候选再走 analyze。
	 */
	public static List<DdzHand> enumerateLeadHands(List<Card> cards) {
		List<DdzHand> result = new ArrayList<>();
		if (cards == null || cards.isEmpty()) return result;
		Map<Integer, List<Card>> groups = groupByValue(cards);
		Set<Long> seen = new HashSet<>();
		for (Map.Entry<Integer, List<Card>> e : groups.entrySet()) {
			List<Card> g = e.getValue();
			int v = e.getKey();
			int n = g.size();
			if (n >= 1) addHand(result, seen, handOf(ConstProto.CardType.SINGLE, g.subList(0, 1), v, 0, false));
			if (n >= 2) addHand(result, seen, handOf(ConstProto.CardType.DOUBLE, g.subList(0, 2), v, 0, false));
			if (n >= 3) addHand(result, seen, handOf(ConstProto.CardType.TRIPLE, g.subList(0, 3), v, 0, false));
			if (n == 4) addHand(result, seen, handOf(ConstProto.CardType.BOOM, g, v, 0, true));
		}
		for (Map.Entry<Integer, List<Card>> e : groups.entrySet()) {
			if (e.getValue().size() < 3) continue;
			int tripleVal = e.getKey();
			for (Map.Entry<Integer, List<Card>> p : groups.entrySet()) {
				if (p.getKey() == tripleVal || p.getValue().size() < 2) continue;
				List<Card> t2 = new ArrayList<>(5);
				t2.addAll(e.getValue().subList(0, 3));
				t2.addAll(p.getValue().subList(0, 2));
				addHand(result, seen, handOf(ConstProto.CardType.TRIPLE_DOUBLE, t2, tripleVal, 0, false));
			}
		}
		collectSequences(groups, 1, 5, result, seen);
		collectSequences(groups, 2, 2, result, seen);
		return result;
	}

	private static DdzHand handOf(ConstProto.CardType type, List<Card> cards, int strength, int straightLen, boolean bomb) {
		return new DdzHand(type, new ArrayList<>(cards), false, bomb, strength, straightLen);
	}

	private static void addHand(List<DdzHand> out, Set<Long> seen, DdzHand h) {
		long key = ((long) h.getType().getNumber() << 32)
				^ (((long) h.getStrengthKey()) << 16)
				^ h.getStraightLen();
		if (seen.add(key)) out.add(h);
	}

	private static void collectSameTypeBeats(Map<Integer, List<Card>> groups, DdzHand last, List<DdzHand> out) {
		switch (last.getType()) {
		case SINGLE:
			collectGroups(groups, last.getStrengthKey(), 1, ConstProto.CardType.SINGLE, out);
			break;
		case DOUBLE:
			collectGroups(groups, last.getStrengthKey(), 2, ConstProto.CardType.DOUBLE, out);
			break;
		case TRIPLE:
			collectGroups(groups, last.getStrengthKey(), 3, ConstProto.CardType.TRIPLE, out);
			break;
		case TRIPLE_DOUBLE:
			collectTriplePairs(groups, last.getStrengthKey(), out);
			break;
		case STRAIGHT:
			collectSequenceBeats(groups, last.getStrengthKey(), last.getStraightLen(), 1, out);
			break;
		case STRAIGHT_DOUBLE:
			collectSequenceBeats(groups, last.getStrengthKey(), last.getStraightLen(), 2, out);
			break;
		default:
			break;
		}
	}

	private static Optional<List<Card>> findSameTypeBeat(Map<Integer, List<Card>> groups, DdzHand last) {
		switch (last.getType()) {
		case SINGLE:
			return findGroup(groups, last.getStrengthKey(), 1);
		case DOUBLE:
			return findGroup(groups, last.getStrengthKey(), 2);
		case TRIPLE:
			return findGroup(groups, last.getStrengthKey(), 3);
		case TRIPLE_DOUBLE:
			return findTriplePair(groups, last.getStrengthKey());
		case STRAIGHT:
			return findSequence(groups, last.getStrengthKey(), last.getStraightLen(), 1);
		case STRAIGHT_DOUBLE:
			return findSequence(groups, last.getStrengthKey(), last.getStraightLen(), 2);
		default:
			return Optional.empty();
		}
	}

	/** 按连续段扫描，直接构造顺/连对，避免对每个子集再 analyze。 */
	private static void collectSequences(
			Map<Integer, List<Card>> groups, int copies, int minLen,
			List<DdzHand> out, Set<Long> seen) {
		ConstProto.CardType type = copies == 1
				? ConstProto.CardType.STRAIGHT : ConstProto.CardType.STRAIGHT_DOUBLE;
		int start = 3;
		while (start < CardConst.ER_VAL) {
			List<Card> head = groups.get(start);
			if (head == null || head.size() < copies) {
				start++;
				continue;
			}
			int end = start + 1;
			while (end < CardConst.ER_VAL) {
				List<Card> g = groups.get(end);
				if (g == null || g.size() < copies) break;
				end++;
			}
			int runLen = end - start;
			if (runLen >= minLen) {
				for (int len = minLen; len <= runLen; len++) {
					for (int s = start; s + len <= end; s++) {
						List<Card> seq = new ArrayList<>(len * copies);
						for (int v = s; v < s + len; v++) {
							seq.addAll(groups.get(v).subList(0, copies));
						}
						addHand(out, seen, handOf(type, seq, s + len - 1, len, false));
					}
				}
			}
			start = end;
		}
	}

	private static Map<Integer, List<Card>> groupByValue(List<Card> cards) {
		Map<Integer, List<Card>> groups = new HashMap<>();
		for (Card card : cards) {
			groups.computeIfAbsent(card.getCardVal(), key -> new ArrayList<>()).add(card);
		}
		return groups;
	}

	private static void collectGroups(
			Map<Integer, List<Card>> groups, int minimum, int count,
			ConstProto.CardType type, List<DdzHand> out) {
		for (int value = minimum + 1; value <= CardConst.ER_VAL; value++) {
			List<Card> group = groups.get(value);
			if (group != null && group.size() >= count) {
				out.add(handOf(type, group.subList(0, count), value, 0, false));
			}
		}
	}

	private static Optional<List<Card>> findGroup(Map<Integer, List<Card>> groups, int minimum, int count) {
		for (int value = minimum + 1; value <= CardConst.ER_VAL; value++) {
			List<Card> group = groups.get(value);
			if (group != null && group.size() >= count) {
				return Optional.of(new ArrayList<>(group.subList(0, count)));
			}
		}
		return Optional.empty();
	}

	private static void collectTriplePairs(Map<Integer, List<Card>> groups, int minimum, List<DdzHand> out) {
		for (int triple = minimum + 1; triple <= CardConst.ER_VAL; triple++) {
			List<Card> tripleCards = groups.get(triple);
			if (tripleCards == null || tripleCards.size() < 3) continue;
			for (Map.Entry<Integer, List<Card>> entry : groups.entrySet()) {
				if (entry.getKey() == triple || entry.getValue().size() < 2) continue;
				List<Card> result = new ArrayList<>(5);
				result.addAll(tripleCards.subList(0, 3));
				result.addAll(entry.getValue().subList(0, 2));
				out.add(handOf(ConstProto.CardType.TRIPLE_DOUBLE, result, triple, 0, false));
			}
		}
	}

	private static Optional<List<Card>> findTriplePair(Map<Integer, List<Card>> groups, int minimum) {
		for (int triple = minimum + 1; triple <= CardConst.ER_VAL; triple++) {
			List<Card> tripleCards = groups.get(triple);
			if (tripleCards == null || tripleCards.size() < 3) continue;
			for (Map.Entry<Integer, List<Card>> entry : groups.entrySet()) {
				if (entry.getKey() == triple || entry.getValue().size() < 2) continue;
				List<Card> result = new ArrayList<>(5);
				result.addAll(tripleCards.subList(0, 3));
				result.addAll(entry.getValue().subList(0, 2));
				return Optional.of(result);
			}
		}
		return Optional.empty();
	}

	private static void collectSequenceBeats(
			Map<Integer, List<Card>> groups, int minimumEnd, int length, int copies, List<DdzHand> out) {
		ConstProto.CardType type = copies == 1
				? ConstProto.CardType.STRAIGHT : ConstProto.CardType.STRAIGHT_DOUBLE;
		for (int end = minimumEnd + 1; end < CardConst.ER_VAL; end++) {
			int start = end - length + 1;
			if (start < 3) continue;
			List<Card> result = new ArrayList<>(length * copies);
			boolean ok = true;
			for (int value = start; value <= end; value++) {
				List<Card> group = groups.get(value);
				if (group == null || group.size() < copies) {
					ok = false;
					break;
				}
				result.addAll(group.subList(0, copies));
			}
			if (ok) out.add(handOf(type, result, end, length, false));
		}
	}

	private static Optional<List<Card>> findSequence(
			Map<Integer, List<Card>> groups, int minimumEnd, int length, int copies) {
		for (int end = minimumEnd + 1; end < CardConst.ER_VAL; end++) {
			int start = end - length + 1;
			if (start < 3) continue;
			List<Card> result = new ArrayList<>(length * copies);
			boolean ok = true;
			for (int value = start; value <= end; value++) {
				List<Card> group = groups.get(value);
				if (group == null || group.size() < copies) {
					ok = false;
					break;
				}
				result.addAll(group.subList(0, copies));
			}
			if (ok) return Optional.of(result);
		}
		return Optional.empty();
	}

	private static Optional<List<Card>> findBomb(Map<Integer, List<Card>> groups, int minimum) {
		for (int value = minimum + 1; value <= CardConst.ER_VAL; value++) {
			List<Card> group = groups.get(value);
			if (group != null && group.size() == 4) {
				return Optional.of(new ArrayList<>(group));
			}
		}
		return Optional.empty();
	}

	private static Optional<DdzHand> tryBomb(List<Card> sorted, Map<Integer, Long> cnt) {
		if (sorted.size() != 4 || cnt.size() != 1) return Optional.empty();
		int v = sorted.get(0).getCardVal();
		return Optional.of(new DdzHand(ConstProto.CardType.BOOM, sorted, false, true, v, 0));
	}

	/** 顺子：点数连续且不含 2；长度 ≥5 */
	private static Optional<DdzHand> tryStraight(List<Card> sorted, Map<Integer, Long> cnt) {
		if (sorted.size() < 5 || cnt.size() != sorted.size()) return Optional.empty();
		List<Integer> vals = new ArrayList<>(cnt.keySet());
		Collections.sort(vals);
		if (vals.contains(CardConst.ER_VAL)) return Optional.empty();
		for (int i = 1; i < vals.size(); i++) {
			if (vals.get(i) != vals.get(i - 1) + 1) return Optional.empty();
		}
		return Optional.of(new DdzHand(ConstProto.CardType.STRAIGHT, sorted, false, false,
				vals.get(vals.size() - 1), vals.size()));
	}

	/** 连对：≥2 对相邻，不含 2 */
	private static Optional<DdzHand> tryDoubleStraight(List<Card> sorted, Map<Integer, Long> cnt) {
		if (sorted.size() < 4 || sorted.size() % 2 != 0) return Optional.empty();
		List<Integer> vals = new ArrayList<>();
		for (Map.Entry<Integer, Long> e : cnt.entrySet()) {
			if (e.getValue() != 2L) return Optional.empty();
			vals.add(e.getKey());
		}
		Collections.sort(vals);
		if (vals.size() < 2 || vals.contains(CardConst.ER_VAL)) return Optional.empty();
		for (int i = 1; i < vals.size(); i++) {
			if (vals.get(i) != vals.get(i - 1) + 1) return Optional.empty();
		}
		return Optional.of(new DdzHand(ConstProto.CardType.STRAIGHT_DOUBLE, sorted, false, false,
				vals.get(vals.size() - 1), vals.size()));
	}

	private static Optional<DdzHand> tryTriplePair(Map<Integer, Long> cnt, List<Card> sorted) {
		if (sorted.size() != 5) return Optional.empty();
		Integer triple = null, pair = null;
		for (Map.Entry<Integer, Long> e : cnt.entrySet()) {
			if (e.getValue() == 3L) triple = e.getKey();
			else if (e.getValue() == 2L) pair = e.getKey();
			else return Optional.empty();
		}
		if (triple == null || pair == null) return Optional.empty();
		return Optional.of(new DdzHand(ConstProto.CardType.TRIPLE_DOUBLE, sorted, false, false, triple, 0));
	}
}
