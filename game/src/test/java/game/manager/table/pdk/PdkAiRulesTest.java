package game.manager.table.pdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import game.manager.table.cards.Card;
import game.manager.table.ddz.DdzHand;

public class PdkAiRulesTest {

	@Test
	public void enumerateLeadPrefersLongerStructures() {
		List<DdzHand> leads = PdkRules.enumerateLeadHands(cards(103, 104, 105, 106, 107, 203));
		assertTrue(leads.stream().anyMatch(h -> h.getType() == proto.ConstProto.CardType.STRAIGHT));
		assertTrue(leads.stream().anyMatch(h -> h.getCards().size() == 1));
	}

	@Test
	public void findAllBeatsIncludesMinimalAndBomb() {
		DdzHand last = PdkRules.analyze(cards(105)).get();
		List<DdzHand> beats = PdkRules.findAllBeatingHands(cards(106, 103, 203, 303, 403), last);
		assertFalse(beats.isEmpty());
		assertTrue(beats.stream().anyMatch(h -> h.getType() == proto.ConstProto.CardType.SINGLE));
		assertTrue(beats.stream().anyMatch(DdzHand::isBomb));
		assertEquals(106 % 100, beats.get(0).getStrengthKey());
	}

	private static List<Card> cards(Integer... ids) {
		Card[] cards = new Card[ids.length];
		for (int i = 0; i < ids.length; i++) cards[i] = new Card(ids[i]);
		return Arrays.asList(cards);
	}
}
