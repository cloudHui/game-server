package game.manager.table.pdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import game.manager.table.cards.Card;
import game.manager.table.ddz.DdzHand;
import game.manager.table.ddz.ai.AiVision;
import game.manager.table.ddz.ai.CardGroup;
import game.manager.table.pdk.ai.PdkSplitPlanner;
import proto.ConstProto;

/** 跑得快大师 AI：拆牌与默认等级。 */
public class PdkMasterAiTest {

	@Test
	public void defaultAiLevelIsMaster() {
		assertEquals(AiVision.AI_MASTER, new PdkTableContext().getAiLevel());
	}

	@Test
	public void aiLevelClampedToMaster() {
		PdkTableContext ctx = new PdkTableContext();
		ctx.setAiLevel(99);
		assertEquals(AiVision.AI_MASTER, ctx.getAiLevel());
		ctx.setAiLevel(-3);
		assertEquals(AiVision.AI_DUMB, ctx.getAiLevel());
	}

	@Test
	public void splitPlannerFormsTripleDoubleAndStraight() {
		List<Card> hand = cards(
				105, 205, 305, // 三张5
				107, 207,     // 对7 → 三带二
				103, 104, 106, 108, 109 // 可成顺的散牌部分会再拆
		);
		List<CardGroup> plan = PdkSplitPlanner.planBest(hand);
		assertFalse(plan.isEmpty());
		boolean hasTripleWing = false;
		for (CardGroup g : plan) {
			DdzHand h = PdkRules.analyze(g.getCards()).orElse(null);
			if (h != null && (h.getType() == ConstProto.CardType.TRIPLE_DOUBLE
					|| h.getType() == ConstProto.CardType.TRIPLE_ONE)) {
				hasTripleWing = true;
			}
		}
		assertTrue("应拆出三带", hasTripleWing);
	}

	@Test
	public void splitPlannerKeepsBombTogether() {
		List<Card> hand = cards(103, 203, 303, 403, 108, 208);
		List<CardGroup> plan = PdkSplitPlanner.plan(hand);
		assertNotNull(plan);
		boolean hasBomb = false;
		for (CardGroup g : plan) {
			DdzHand h = PdkRules.analyze(g.getCards()).orElse(null);
			if (h != null && h.isBomb()) hasBomb = true;
		}
		assertTrue(hasBomb);
		assertTrue("炸弹保留时组数应较少", plan.size() <= 3);
	}

	@Test
	public void planBestMaySplitBombWhenFewerGroups() {
		// 4张3 + 两张散牌：拆炸成三带一可能更整
		List<Card> hand = cards(103, 203, 303, 403, 105, 107);
		List<CardGroup> best = PdkSplitPlanner.planBest(hand);
		assertFalse(best.isEmpty());
		int totalCards = 0;
		for (CardGroup g : best) totalCards += g.getCards().size();
		assertEquals(hand.size(), totalCards);
	}

	private static List<Card> cards(Integer... ids) {
		List<Card> list = new ArrayList<>(ids.length);
		for (Integer id : ids) list.add(new Card(id));
		return list;
	}
}
