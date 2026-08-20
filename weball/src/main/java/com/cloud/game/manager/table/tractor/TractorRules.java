package com.cloud.game.manager.table.tractor;

import com.cloud.game.manager.table.card.CardConst;
import com.cloud.game.manager.table.cards.Card;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 拖拉机规则：主牌判定、牌力、单/对/拖拉机识别、跟牌校验、比大、亮主强度。
 * <p>
 * 级牌点数由当局 levelRank 决定（2=15…A=14）；trumpSuit=0 表示无主。
 * 手牌展示序：副牌方梅红黑（组内小到大）→主花色→副级牌方梅红黑
 * →主级牌→小王→大王。
 */
public final class TractorRules {

	public enum ComboType { SINGLE, PAIR, TRACTOR, THROW }

	/** 亮主强度：单张 &lt; 级牌对 &lt; 小王对(无主) &lt; 大王对(无主) */
	public static final int BID_NONE = 0;
	public static final int BID_SINGLE = 1;
	public static final int BID_REINFORCED = 2;
	public static final int BID_PAIR = 3;
	public static final int BID_SMALL_JOKER = 4;
	public static final int BID_BIG_JOKER = 5;

	public static final class Combo {
		public final ComboType type;
		public final List<Card> cards;
		public final int strength; // 主比较键
		public final int suitId;   // 0=主牌组，否则花色
		public final int tractorLen;

		Combo(ComboType type, List<Card> cards, int strength, int suitId, int tractorLen) {
			this.type = type;
			this.cards = Collections.unmodifiableList(new ArrayList<>(cards));
			this.strength = strength;
			this.suitId = suitId;
			this.tractorLen = tractorLen;
		}
	}

	public static final class BidDeclare {
		public final int strength;
		public final int suit; // 1-4 或 0=无主
		public final List<Card> cards;

		public BidDeclare(int strength, int suit, List<Card> cards) {
			this.strength = strength;
			this.suit = suit;
			this.cards = Collections.unmodifiableList(new ArrayList<>(cards));
		}
	}

	private TractorRules() {}

	public static int scoreOf(Card c) {
		int v = c.getCardVal();
		if (v == 5) return 5;
		if (v == 10 || v == CardConst.K_VAL) return 10;
		return 0;
	}

	public static boolean isJoker(Card c) {
		return c.isSmallJoker() || c.isBigJoker();
	}

	public static boolean isLevelCard(Card c, int levelRank) {
		return !isJoker(c) && c.getCardVal() == levelRank;
	}

	public static boolean isTrump(Card c, int levelRank, int trumpSuit) {
		if (isJoker(c)) return true;
		if (isLevelCard(c, levelRank)) return true;
		if (trumpSuit <= 0) return false; // 无主：仅王与级牌为主
		return c.getCardSuit() != null && c.getCardSuit().getId() == trumpSuit;
	}

	/** 花色组：主牌统一 suitGroup=0，副牌为花色 id */
	public static int suitGroup(Card c, int levelRank, int trumpSuit) {
		return isTrump(c, levelRank, trumpSuit) ? 0 : c.getId() / 100;
	}

	/** 手牌排序键，返回值越小越靠左。 */
	public static int handOrderKey(Card c, int levelRank, int trumpSuit) {
		if (c.isSmallJoker()) return 800000;
		if (c.isBigJoker()) return 900000;
		int suit = c.getId() / 100;
		if (isLevelCard(c, levelRank)) {
			return suit == trumpSuit ? 700000 : 600000 + suit * 100;
		}
		if (trumpSuit > 0 && suit == trumpSuit) {
			return 500000 + c.getCardVal();
		}
		return suit * 10000 + c.getCardVal();
	}

	public static void sortHand(List<Card> cards, int levelRank, int trumpSuit) {
		if (cards == null || cards.size() < 2) return;
		cards.sort((a, b) -> Integer.compare(
				handOrderKey(a, levelRank, trumpSuit),
				handOrderKey(b, levelRank, trumpSuit)));
	}

	/**
	 * 牌力：越大越大。主牌：大王>小王>正级>副级>主花色其余；副牌按点数。
	 */
	public static int power(Card c, int levelRank, int trumpSuit) {
		if (c.isBigJoker()) return 10000;
		if (c.isSmallJoker()) return 9000;
		if (isLevelCard(c, levelRank)) {
			boolean main = trumpSuit > 0 && c.getCardSuit() != null && c.getCardSuit().getId() == trumpSuit;
			return main ? 8000 + c.getId() % 10 : 7000 + c.getId() % 10;
		}
		if (isTrump(c, levelRank, trumpSuit)) {
			return 5000 + c.getCardVal();
		}
		return c.getCardVal();
	}

	public static Combo analyze(List<Card> cards, int levelRank, int trumpSuit) {
		if (cards == null || cards.isEmpty()) return null;
		List<Card> sorted = new ArrayList<>(cards);
		sorted.sort((a, b) -> Integer.compare(power(b, levelRank, trumpSuit), power(a, levelRank, trumpSuit)));
		int n = sorted.size();
		int g0 = suitGroup(sorted.get(0), levelRank, trumpSuit);
		for (Card c : sorted) {
			if (suitGroup(c, levelRank, trumpSuit) != g0) return null;
		}
		if (n == 1) {
			return new Combo(ComboType.SINGLE, sorted, power(sorted.get(0), levelRank, trumpSuit), g0, 0);
		}
		if (n == 2) {
			if (pairKey(sorted.get(0), levelRank) == pairKey(sorted.get(1), levelRank)
					&& samePairIdentity(sorted.get(0), sorted.get(1), levelRank, trumpSuit)) {
				return new Combo(ComboType.PAIR, sorted, power(sorted.get(0), levelRank, trumpSuit), g0, 0);
			}
			return null;
		}
		if (n >= 4 && n % 2 == 0) {
			Combo tractor = tryTractor(sorted, levelRank, trumpSuit, g0);
			if (tractor != null) return tractor;
		}
		return null;
	}

	/** 将同一门的多张非标准组合识别为甩牌。 */
	public static Combo analyzeThrow(List<Card> cards, int levelRank, int trumpSuit) {
		if (cards == null || cards.size() < 2) return null;
		int group = suitGroup(cards.get(0), levelRank, trumpSuit);
		int maxPower = Integer.MIN_VALUE;
		for (Card card : cards) {
			if (suitGroup(card, levelRank, trumpSuit) != group) return null;
			maxPower = Math.max(maxPower, power(card, levelRank, trumpSuit));
		}
		return new Combo(ComboType.THROW, cards, maxPower, group, 0);
	}

	private static int pairKey(Card c, int levelRank) {
		if (isJoker(c)) return c.getCardVal();
		return c.getCardVal();
	}

	private static boolean samePairIdentity(Card a, Card b, int levelRank, int trumpSuit) {
		if (isJoker(a) || isJoker(b)) return a.getCardVal() == b.getCardVal();
		if (isLevelCard(a, levelRank) && isLevelCard(b, levelRank)) {
			// 副级同点可对；正级同点可对；正副不可混对；无主时级牌同花色才可对
			if (a.getCardSuit() == null || b.getCardSuit() == null) return false;
			if (trumpSuit <= 0) {
				return a.getCardSuit() == b.getCardSuit() && a.getCardVal() == b.getCardVal();
			}
			boolean am = a.getCardSuit().getId() == trumpSuit;
			boolean bm = b.getCardSuit().getId() == trumpSuit;
			return am == bm && a.getCardVal() == b.getCardVal();
		}
		return a.getCardSuit() == b.getCardSuit() && a.getCardVal() == b.getCardVal();
	}

	private static Combo tryTractor(List<Card> sorted, int levelRank, int trumpSuit, int g0) {
		Map<Integer, Long> cnt = sorted.stream().collect(Collectors.groupingBy(
				c -> tractorStepKey(c, levelRank, trumpSuit), TreeMap::new, Collectors.counting()));
		List<Integer> keys = new ArrayList<>(cnt.keySet());
		for (Long v : cnt.values()) if (v != 2L) return null;
		Collections.sort(keys);
		if (keys.size() < 2) return null;
		for (int i = 1; i < keys.size(); i++) {
			if (keys.get(i) != keys.get(i - 1) + 1) return null;
		}
		int maxPow = 0;
		for (Card c : sorted) maxPow = Math.max(maxPow, power(c, levelRank, trumpSuit));
		return new Combo(ComboType.TRACTOR, sorted, maxPow, g0, keys.size());
	}

	/** 拖拉机相邻步进键：副牌按点数；主牌按主序简化为点数（级牌单独高位） */
	private static int tractorStepKey(Card c, int levelRank, int trumpSuit) {
		if (isJoker(c)) return c.isBigJoker() ? 100 : 99;
		if (isLevelCard(c, levelRank)) {
			boolean main = trumpSuit > 0 && c.getCardSuit() != null && c.getCardSuit().getId() == trumpSuit;
			return main ? 98 : 97;
		}
		return c.getCardVal();
	}

	/**
	 * 跟牌是否合法：同花色同型优先；否则整组垫牌/杀牌（张数相同）。
	 * <p>
	 * 跟拖拉机/对子时：有该门则必须出该门；有几对就必须出几对（上限为首家对数），
	 * 对子不必连着成拖拉机——避免“有两对却卡死出不了”的情况；对子不够再用同色单牌补张。
	 */
	public static boolean isLegalFollow(List<Card> play, Combo lead, List<Card> hand,
                                        int levelRank, int trumpSuit) {
		if (play == null || lead == null || play.size() != lead.cards.size()) return false;
		Combo parsed = analyze(play, levelRank, trumpSuit);
		int gLead = lead.suitId;
		List<Card> inLeadSuit = hand.stream()
				.filter(c -> suitGroup(c, levelRank, trumpSuit) == gLead)
				.collect(Collectors.toList());
		List<Card> playedLeadSuit = play.stream()
				.filter(c -> suitGroup(c, levelRank, trumpSuit) == gLead)
				.collect(Collectors.toList());
		// 有几张同门必须先跟尽几张；同门不足时才可用其他花色补齐。
		if (playedLeadSuit.size() != Math.min(play.size(), inLeadSuit.size())) return false;
		if (lead.type == ComboType.PAIR || lead.type == ComboType.TRACTOR) {
			int needPairs = lead.type == ComboType.PAIR ? 1 : lead.tractorLen;
			if (lead.type == ComboType.TRACTOR
					&& hasTractor(inLeadSuit, needPairs, levelRank, trumpSuit)) {
				// 手牌有足长同门拖拉机时，必须按完整拖拉机跟出。
				return parsed != null && parsed.suitId == gLead
						&& parsed.type == ComboType.TRACTOR && parsed.tractorLen == needPairs;
			}
			int mustPairs = Math.min(needPairs, countPairs(inLeadSuit, levelRank, trumpSuit));
			return countPairs(playedLeadSuit, levelRank, trumpSuit) >= mustPairs;
		}
		if (!inLeadSuit.isEmpty()) return true;
		// 无该门：若整手出主牌杀，须同型（有拖拉机须用拖拉机毙）
		if (parsed != null && parsed.suitId == 0) {
			if (!sameShape(parsed, lead)) return false;
			return true;
		}
		return play.size() == lead.cards.size();
	}

	/** 主牌杀牌也必须保持首牌的牌型、张数和拖拉机长度。 */
	private static boolean sameShape(Combo a, Combo b) {
		if (a == null || b == null || a.type != b.type || a.cards.size() != b.cards.size()) return false;
		return a.type != ComboType.TRACTOR || a.tractorLen == b.tractorLen;
	}

	private static boolean hasTractor(List<Card> cards, int length, int levelRank, int trumpSuit) {
		Map<Integer, Long> cnt = cards.stream().collect(Collectors.groupingBy(
				c -> tractorStepKey(c, levelRank, trumpSuit), TreeMap::new, Collectors.counting()));
		int run = 0;
		Integer previous = null;
		for (Map.Entry<Integer, Long> entry : cnt.entrySet()) {
			if (entry.getValue() < 2) {
				run = 0;
				previous = null;
				continue;
			}
			run = previous != null && entry.getKey() == previous + 1 ? run + 1 : 1;
			if (run >= length) return true;
			previous = entry.getKey();
		}
		return false;
	}

	private static int countPairs(List<Card> cards, int levelRank, int trumpSuit) {
		Map<Integer, Long> cnt = new TreeMap<>();
		for (Card c : cards) {
			int k;
			if (isJoker(c)) {
				k = c.getCardVal();
			} else if (isLevelCard(c, levelRank)) {
				int suitId = c.getCardSuit() != null ? c.getCardSuit().getId() : 0;
				boolean main = trumpSuit > 0 && suitId == trumpSuit;
				k = tractorStepKey(c, levelRank, trumpSuit) * 10 + (main ? 1 : 0);
			} else {
				k = c.getCardSuit().getId() * 100 + c.getCardVal();
			}
			cnt.merge(k, 1L, Long::sum);
		}
		int pairs = 0;
		for (long v : cnt.values()) pairs += v / 2;
		return pairs;
	}

	/** 本轮谁大：必须同型同花色组才可比；主杀副；否则首家大 */
	public static int winnerSeat(List<List<Card>> plays, List<Integer> seats, Combo lead,
                                 int levelRank, int trumpSuit) {
		int bestIdx = 0;
		Combo best = lead.type == ComboType.THROW
				? lead : analyze(plays.get(0), levelRank, trumpSuit);
		if (lead.type == ComboType.THROW) return seats.get(0);
		for (int i = 1; i < plays.size(); i++) {
			Combo cur = analyze(plays.get(i), levelRank, trumpSuit);
			if (beats(cur, best, lead, levelRank, trumpSuit)) {
				best = cur;
				bestIdx = i;
			}
		}
		return seats.get(bestIdx);
	}

	/** 判断 play 是否能压过当前最大的一手（供 AI 跟牌，避免整墩重算 winnerSeat）。 */
	public static boolean beatsCurrent(List<Card> play, Combo currentBest, Combo lead,
                                       int levelRank, int trumpSuit) {
		return beats(analyze(play, levelRank, trumpSuit), currentBest, lead, levelRank, trumpSuit);
	}

	/** 解析墩中当前最大一手；空墩返回 null。 */
	public static Combo currentBestCombo(List<List<Card>> plays, Combo lead, int levelRank, int trumpSuit) {
		if (plays == null || plays.isEmpty()) return null;
		Combo best = analyze(plays.get(0), levelRank, trumpSuit);
		for (int i = 1; i < plays.size(); i++) {
			Combo cur = analyze(plays.get(i), levelRank, trumpSuit);
			if (beats(cur, best, lead, levelRank, trumpSuit)) best = cur;
		}
		return best;
	}

	private static boolean beats(Combo incoming, Combo currentBest, Combo lead,
			int levelRank, int trumpSuit) {
		if (incoming == null) return false;
		if (currentBest == null) return true;
		boolean inTrump = incoming.suitId == 0;
		boolean bestTrump = currentBest.suitId == 0;
		boolean leadTrump = lead.suitId == 0;
		if (!leadTrump) {
			if (inTrump && sameShape(incoming, lead)) {
				if (!bestTrump) return true;
				return incoming.strength > currentBest.strength;
			}
			if (incoming.suitId != lead.suitId) return false;
			if (!sameShape(incoming, lead)) return false;
			return incoming.strength > currentBest.strength;
		}
		// 首家出主
		if (!inTrump) return false;
		if (!sameShape(incoming, lead)) return false;
		return incoming.strength > currentBest.strength;
	}

	public static int nextLevelRank(int levelRank) {
		// 2(15)->3->...->A(14)->2
		if (levelRank == CardConst.ER_VAL) return 3;
		if (levelRank == CardConst.ACE_VAL) return CardConst.ER_VAL;
		return levelRank + 1;
	}

	public static String levelName(int levelRank) {
		if (levelRank == 11) return "J";
		if (levelRank == 12) return "Q";
		if (levelRank == 13) return "K";
		if (levelRank == 14) return "A";
		if (levelRank == 15) return "2";
		return String.valueOf(levelRank);
	}

	/** 抠底倍数：按赢家该手最大牌型。单×2，对×4，拖拉机 2×2^len（len=2→8 … 封顶64）。
	 * 甩牌时只取其中最大子牌型（有对按对、有拖拉机按拖拉机）。 */
	public static int digMultiplier(Combo lastLead) {
		return digMultiplierOfType(lastLead);
	}

	public static int digMultiplierForPlay(List<Card> play, int levelRank, int trumpSuit) {
		if (play == null || play.isEmpty()) return 2;
		Combo whole = analyze(play, levelRank, trumpSuit);
		if (whole != null) return digMultiplierOfType(whole);
		// 甩牌：只看所含最大牌型（拖拉机 > 对 > 单）
		int best = 2;
		Map<Integer, List<Card>> byGroup = new HashMap<>();
		for (Card c : play) {
			byGroup.computeIfAbsent(suitGroup(c, levelRank, trumpSuit), k -> new ArrayList<>()).add(c);
		}
		for (List<Card> group : byGroup.values()) {
			Combo tractor = tryTractor(
					sortedByPower(group, levelRank, trumpSuit), levelRank, trumpSuit,
					suitGroup(group.get(0), levelRank, trumpSuit));
			if (tractor != null) best = Math.max(best, digMultiplierOfType(tractor));
			else if (countPairs(group, levelRank, trumpSuit) >= 1) best = Math.max(best, 4);
		}
		return Math.min(64, best);
	}

	private static List<Card> sortedByPower(List<Card> cards, int levelRank, int trumpSuit) {
		List<Card> sorted = new ArrayList<>(cards);
		sorted.sort((a, b) -> Integer.compare(power(b, levelRank, trumpSuit), power(a, levelRank, trumpSuit)));
		return sorted;
	}

	private static int digMultiplierOfType(Combo combo) {
		if (combo == null) return 2;
		if (combo.type == ComboType.PAIR) return 4;
		if (combo.type == ComboType.TRACTOR) {
			// 2 × 2^len ：4455(len2)=8，445566(len3)=16，封顶 64
			int mult = 2 * (1 << combo.tractorLen);
			return Math.min(64, Math.max(8, mult));
		}
		return 2;
	}

	/**
	 * 闲家得分 → 庄升/闲升级数。返回 [bankerWin(1/0), upgradeLevels]。
	 * 0大光庄+3；5-35小光庄+2；40-75庄+1；80-115换庄不升；120起闲升，每多40多一级。
	 */
	public static int[] settleUpgrade(int defenderScore) {
		int def = Math.max(0, defenderScore);
		if (def == 0) return new int[] { 1, 3 };
		if (def < 40) return new int[] { 1, 2 };
		if (def < 80) return new int[] { 1, 1 };
		if (def < 120) return new int[] { 0, 0 };
		return new int[] { 0, 1 + (def - 120) / 40 };
	}

	/** 解析亮主/反主牌：1张级牌；2张同花色级牌对；小王对；大王对 */
	public static BidDeclare analyzeDeclare(List<Card> cards, int levelRank) {
		if (cards == null || cards.isEmpty()) return null;
		if (cards.size() == 1) {
			Card c = cards.get(0);
			if (!isLevelCard(c, levelRank) || c.getCardSuit() == null) return null;
			return new BidDeclare(BID_SINGLE, c.getCardSuit().getId(), cards);
		}
		if (cards.size() != 2) return null;
		Card a = cards.get(0), b = cards.get(1);
		if (a.isBigJoker() && b.isBigJoker()) {
			return new BidDeclare(BID_BIG_JOKER, 0, cards);
		}
		if (a.isSmallJoker() && b.isSmallJoker()) {
			return new BidDeclare(BID_SMALL_JOKER, 0, cards);
		}
		if (isLevelCard(a, levelRank) && isLevelCard(b, levelRank)
				&& a.getCardSuit() != null && a.getCardSuit() == b.getCardSuit()) {
			return new BidDeclare(BID_PAIR, a.getCardSuit().getId(), cards);
		}
		return null;
	}

	/**
	 * 单张只能首次亮主，不能用另一张单牌反。
	 * 级牌对可反单张；级牌对之间按方块&lt;梅花&lt;红桃&lt;黑桃；
	 * 小王对压黑桃级牌对，大王对压小王对。
	 */
	public static boolean beatsDeclare(BidDeclare incoming, int currentStrength, int currentSuit) {
		if (incoming == null) return false;
		if (currentStrength <= BID_NONE) return true;
		if (incoming.strength == BID_SINGLE) return false;
		if (incoming.strength != currentStrength) return incoming.strength > currentStrength;
		return incoming.strength == BID_PAIR && incoming.suit > currentSuit;
	}

	/** 从手牌找能压过当前声明的最优声明（优先王对&gt;级牌对&gt;单张，同级取大花色） */
	public static BidDeclare findBestDeclare(List<Card> hand, int levelRank,
                                             int currentStrength, int currentSuit) {
		if (hand == null || hand.isEmpty()) return null;
		List<Card> big = new ArrayList<>();
		List<Card> small = new ArrayList<>();
		Map<Integer, List<Card>> levelBySuit = new HashMap<>();
		Map<Integer, Integer> suitCount = new HashMap<>();
		for (Card c : hand) {
			if (!isJoker(c) && c.getCardSuit() != null) {
				suitCount.merge(c.getCardSuit().getId(), 1, Integer::sum);
			}
			if (c.isBigJoker()) big.add(c);
			else if (c.isSmallJoker()) small.add(c);
			else if (isLevelCard(c, levelRank) && c.getCardSuit() != null) {
				levelBySuit.computeIfAbsent(c.getCardSuit().getId(), k -> new ArrayList<>()).add(c);
			}
		}
		BidDeclare bigPair = big.size() >= 2
				? new BidDeclare(BID_BIG_JOKER, 0, big.subList(0, 2)) : null;
		if (beatsDeclare(bigPair, currentStrength, currentSuit)) {
			return bigPair;
		}
		BidDeclare smallPair = small.size() >= 2
				? new BidDeclare(BID_SMALL_JOKER, 0, small.subList(0, 2)) : null;
		if (beatsDeclare(smallPair, currentStrength, currentSuit)) {
			return smallPair;
		}
		List<Integer> suitOrder = new ArrayList<>();
		for (int suit = 1; suit <= 4; suit++) suitOrder.add(suit);
		suitOrder.sort((a, b) -> Integer.compare(suitCount.getOrDefault(b, 0), suitCount.getOrDefault(a, 0)));
		for (int suit : suitOrder) {
			List<Card> cards = levelBySuit.get(suit);
			BidDeclare pair = cards != null && cards.size() >= 2
					? new BidDeclare(BID_PAIR, suit, cards.subList(0, 2)) : null;
			if (beatsDeclare(pair, currentStrength, currentSuit)) {
				return pair;
			}
		}
		for (int suit : suitOrder) {
			List<Card> cards = levelBySuit.get(suit);
			BidDeclare single = cards != null && !cards.isEmpty()
					? new BidDeclare(BID_SINGLE, suit, Collections.singletonList(cards.get(0))) : null;
			if (beatsDeclare(single, currentStrength, currentSuit)) {
				return single;
			}
		}
		return null;
	}

	public static BidDeclare findBestDeclare(List<Card> hand, int levelRank, int currentStrength) {
		return findBestDeclare(hand, levelRank, currentStrength, 0);
	}
}
