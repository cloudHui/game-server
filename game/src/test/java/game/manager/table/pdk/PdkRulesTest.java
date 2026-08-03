package game.manager.table.pdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import game.manager.table.cards.Card;
import game.manager.table.ddz.DdzHand;

public class PdkRulesTest {

	@Test
	public void detectsRequiredSingleAndPairBeats() {
		DdzHand single = PdkRules.analyze(cards(103)).get();
		DdzHand pair = PdkRules.analyze(cards(105, 205)).get();

		assertTrue(PdkRules.canBeat(cards(104, 106, 206), single));
		assertTrue(PdkRules.canBeat(cards(106, 206, 109), pair));
		assertFalse(PdkRules.canBeat(cards(104, 107, 208), pair));
	}

	@Test
	public void detectsRequiredSequenceBeat() {
		DdzHand straight = PdkRules.analyze(cards(103, 104, 105, 106, 107)).get();
		DdzHand doubleStraight = PdkRules.analyze(cards(103, 203, 104, 204)).get();

		assertTrue(PdkRules.canBeat(cards(104, 105, 106, 107, 108, 113), straight));
		assertTrue(PdkRules.canBeat(cards(104, 204, 105, 205, 113), doubleStraight));
		assertFalse(PdkRules.canBeat(cards(103, 104, 105, 106, 107, 113), straight));
	}

	@Test
	public void bombRequiresPlayAgainstOrdinaryHand() {
		DdzHand pair = PdkRules.analyze(cards(114, 214)).get();
		assertTrue(PdkRules.canBeat(cards(103, 203, 303, 403), pair));
	}

	/** 有牌必管 / 管不上仅不出：能压则不能过，压不上则只能过。 */
	@Test
	public void mustBeatMeansCannotPassWhenHandCovers() {
		DdzHand last = PdkRules.analyze(cards(105)).get();
		assertTrue("能管上单张时必须管，不下发不出", PdkRules.canBeat(cards(106, 108, 210), last));
		assertFalse("管不上时只能不出，不下发出牌", PdkRules.canBeat(cards(103, 104, 203), last));
	}

	@Test
	public void supportsTripleOneAndTripleTwo() {
		DdzHand t1 = PdkRules.analyze(cards(105, 205, 305, 107)).get();
		DdzHand t2 = PdkRules.analyze(cards(106, 206, 306, 108, 208)).get();
		assertEquals(proto.ConstProto.CardType.TRIPLE_ONE, t1.getType());
		assertEquals(proto.ConstProto.CardType.TRIPLE_DOUBLE, t2.getType());
		assertTrue(PdkRules.canBeat(cards(106, 206, 306, 109), t1));
		assertFalse("三带二不能压三带一", PdkRules.beats(t2, t1));
		assertTrue(PdkRules.canBeat(cards(107, 207, 307, 110, 210), t2));
	}

	private static List<Card> cards(Integer... ids) {
		Card[] cards = new Card[ids.length];
		for (int i = 0; i < ids.length; i++) cards[i] = new Card(ids[i]);
		return Arrays.asList(cards);
	}
}
