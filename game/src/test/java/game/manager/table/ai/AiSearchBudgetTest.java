package game.manager.table.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiSearchBudgetTest {
	@Test
	public void nodeBudgetStopsSearchDeterministically() {
		AiSearchBudget budget = new AiSearchBudget(1000, 2);
		assertTrue(budget.tryVisit());
		assertTrue(budget.tryVisit());
		assertFalse(budget.tryVisit());
		assertTrue(budget.isExhausted());
		assertEquals(2, budget.getVisitedNodes());
	}

	@Test
	public void invalidLimitsStillAllowOneNode() {
		AiSearchBudget budget = new AiSearchBudget(1000, 0);
		assertTrue(budget.tryVisit());
		assertFalse(budget.tryVisit());
	}
}
