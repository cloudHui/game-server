package game.manager.table.tractor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import game.manager.table.card.CardConst;
import game.manager.table.cards.Card;

public class TractorRulesTest {

	@Test
	public void settleTiersMatchClassicTable() {
		assertEquals(3, TractorRules.settleUpgrade(0)[1]);
		assertTrue(TractorRules.settleUpgrade(0)[0] == 1);
		assertEquals(2, TractorRules.settleUpgrade(20)[1]);
		assertEquals(1, TractorRules.settleUpgrade(40)[1]);
		assertEquals(0, TractorRules.settleUpgrade(80)[0]);
		assertEquals(0, TractorRules.settleUpgrade(100)[1]);
		assertEquals(1, TractorRules.settleUpgrade(120)[1]);
		assertEquals(2, TractorRules.settleUpgrade(160)[1]);
		assertEquals(3, TractorRules.settleUpgrade(200)[1]);
	}

	@Test
	public void digMultiplierByLeadType() {
		TractorRules.Combo single = newCombo(TractorRules.ComboType.SINGLE, 0);
		TractorRules.Combo pair = newCombo(TractorRules.ComboType.PAIR, 0);
		TractorRules.Combo tractor2 = newCombo(TractorRules.ComboType.TRACTOR, 2);
		TractorRules.Combo tractor3 = newCombo(TractorRules.ComboType.TRACTOR, 3);
		TractorRules.Combo tractor4 = newCombo(TractorRules.ComboType.TRACTOR, 4);
		assertEquals(2, TractorRules.digMultiplier(single));
		assertEquals(4, TractorRules.digMultiplier(pair));
		assertEquals(8, TractorRules.digMultiplier(tractor2));
		assertEquals(16, TractorRules.digMultiplier(tractor3));
		assertEquals(32, TractorRules.digMultiplier(tractor4));
	}

	@Test
	public void declareSinglePairAndJokerNoTrump() {
		int level = CardConst.ER_VAL;
		TractorRules.BidDeclare single = TractorRules.analyzeDeclare(cards(315), level);
		assertNotNull(single);
		assertEquals(TractorRules.BID_SINGLE, single.strength);
		assertEquals(3, single.suit);

		TractorRules.BidDeclare pair = TractorRules.analyzeDeclare(cards(315, 315), level);
		// 两副牌同 id 可对；单测用两个同值不同实例
		pair = TractorRules.analyzeDeclare(Arrays.asList(new Card(315), new Card(315)), level);
		assertNotNull(pair);
		assertEquals(TractorRules.BID_PAIR, pair.strength);

		TractorRules.BidDeclare big = TractorRules.analyzeDeclare(
				Arrays.asList(new Card(517), new Card(517)), level);
		assertNotNull(big);
		assertEquals(TractorRules.BID_BIG_JOKER, big.strength);
		assertEquals(0, big.suit);
	}

	@Test
	public void noTrumpOnlyJokersAndLevelAreTrump() {
		int level = CardConst.ER_VAL;
		assertTrue(TractorRules.isTrump(new Card(517), level, 0));
		assertTrue(TractorRules.isTrump(new Card(315), level, 0));
		assertFalse(TractorRules.isTrump(new Card(310), level, 0));
		assertTrue(TractorRules.isTrump(new Card(310), level, 3));
	}

	@Test
	public void findBestDeclarePrefersJokerPair() {
		List<Card> hand = Arrays.asList(
				new Card(517), new Card(517), new Card(315), new Card(105));
		TractorRules.BidDeclare best = TractorRules.findBestDeclare(hand, CardConst.ER_VAL, 0);
		assertNotNull(best);
		assertEquals(TractorRules.BID_BIG_JOKER, best.strength);
		assertNull(TractorRules.findBestDeclare(cards(105, 106), CardConst.ER_VAL, 0));
	}

	@Test
	public void singleCanOnlyBeReversedByPairAndPairsUseSuitOrder() {
		int level = CardConst.ER_VAL;
		TractorRules.BidDeclare clubSingle = TractorRules.analyzeDeclare(cards(215), level);
		TractorRules.BidDeclare diamondPair = TractorRules.analyzeDeclare(cards(115, 115), level);
		TractorRules.BidDeclare heartPair = TractorRules.analyzeDeclare(cards(315, 315), level);
		TractorRules.BidDeclare spadePair = TractorRules.analyzeDeclare(cards(415, 415), level);
		TractorRules.BidDeclare smallJokers = TractorRules.analyzeDeclare(cards(516, 516), level);
		TractorRules.BidDeclare bigJokers = TractorRules.analyzeDeclare(cards(517, 517), level);

		assertFalse(TractorRules.beatsDeclare(clubSingle, TractorRules.BID_SINGLE, 1));
		assertTrue(TractorRules.beatsDeclare(diamondPair, TractorRules.BID_SINGLE, 4));
		assertTrue(TractorRules.beatsDeclare(heartPair, TractorRules.BID_PAIR, 2));
		assertFalse(TractorRules.beatsDeclare(heartPair, TractorRules.BID_PAIR, 4));
		assertTrue(TractorRules.beatsDeclare(smallJokers, spadePair.strength, spadePair.suit));
		assertTrue(TractorRules.beatsDeclare(bigJokers, smallJokers.strength, smallJokers.suit));
	}

	@Test
	public void sameSuitMixedCardsCanBeRecognizedAsThrow() {
		TractorRules.Combo thrown = TractorRules.analyzeThrow(cards(103, 105, 107), 15, 3);
		assertNotNull(thrown);
		assertEquals(TractorRules.ComboType.THROW, thrown.type);
		assertNull(TractorRules.analyzeThrow(cards(103, 205, 107), 15, 3));
	}

	@Test
	public void handOrderSmallToLargeThenTrumpLevelAndJokers() {
		int level = CardConst.ER_VAL;
		int trump = 4; // 黑桃主
		List<Card> hand = new java.util.ArrayList<>(Arrays.asList(
				new Card(105), // 方块5
				new Card(415), // 黑桃2 级牌主
				new Card(314), // 红桃A
				new Card(410), // 黑桃10 主花
				new Card(115), // 方块2 副级
				new Card(516), // 小王
				new Card(517), // 大王
				new Card(203)  // 梅花3
		));
		TractorRules.sortHand(hand, level, trump);
		// 副牌方梅红黑（小到大）→主花色→副级→主级→小王→大王
		assertEquals(105, hand.get(0).getId());
		assertEquals(203, hand.get(1).getId());
		assertEquals(314, hand.get(2).getId());
		assertEquals(410, hand.get(3).getId());
		assertEquals(115, hand.get(4).getId());
		assertEquals(415, hand.get(5).getId());
		assertEquals(516, hand.get(6).getId());
		assertEquals(517, hand.get(7).getId());
	}

	/** 跟拖拉机：有两对但不成连对时仍须出两对，避免卡死 */
	@Test
	public void followTractorAllowsNonConsecutivePairsWhenHaveEnough() {
		int level = CardConst.ER_VAL;
		int trump = 3;
		TractorRules.Combo lead = TractorRules.analyze(cards(103, 103, 104, 104), level, trump);
		assertNotNull(lead);
		assertEquals(TractorRules.ComboType.TRACTOR, lead.type);
		List<Card> hand = Arrays.asList(
				new Card(105), new Card(105), new Card(107), new Card(107), new Card(109));
		// 105对 + 107对，不连着，仍应合法
		assertTrue(TractorRules.isLegalFollow(
				Arrays.asList(new Card(105), new Card(105), new Card(107), new Card(107)),
				lead, hand, level, trump));
		// 只有一对时必须出该对 + 同色单牌
		List<Card> handOnePair = Arrays.asList(
				new Card(105), new Card(105), new Card(109), new Card(110), new Card(111));
		assertTrue(TractorRules.isLegalFollow(
				Arrays.asList(new Card(105), new Card(105), new Card(109), new Card(110)),
				lead, handOnePair, level, trump));
		assertFalse("有对却全出单牌不合法", TractorRules.isLegalFollow(
				Arrays.asList(new Card(109), new Card(110), new Card(111), new Card(105)),
				lead, handOnePair, level, trump));
	}

	@Test
	public void handOrderResortsWhenTrumpChanges() {
		int level = CardConst.ER_VAL;
		List<Card> hand = new java.util.ArrayList<>(Arrays.asList(
				new Card(310), new Card(410), new Card(110)));
		TractorRules.sortHand(hand, level, 3); // 红桃主 → 110,410 副 | 310 主右
		assertEquals(110, hand.get(0).getId());
		assertEquals(410, hand.get(1).getId());
		assertEquals(310, hand.get(2).getId());
		TractorRules.sortHand(hand, level, 4); // 改黑桃主
		assertEquals(110, hand.get(0).getId());
		assertEquals(310, hand.get(1).getId());
		assertEquals(410, hand.get(2).getId());
	}

	private static TractorRules.Combo newCombo(TractorRules.ComboType type, int tractorLen) {
		return new TractorRules.Combo(type, cards(103), 1, 1, tractorLen);
	}

	private static List<Card> cards(Integer... ids) {
		Card[] arr = new Card[ids.length];
		for (int i = 0; i < ids.length; i++) arr[i] = new Card(ids[i]);
		return Arrays.asList(arr);
	}
}
