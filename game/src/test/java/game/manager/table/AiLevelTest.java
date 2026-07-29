package game.manager.table;

import game.manager.table.ddz.DdzTableContext;
import game.manager.table.ddz.ai.AiVision;
import game.manager.table.mj.MjTableContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** 第四档大师 AI 配置边界；默认难度保持原高级档。 */
public class AiLevelTest {
	@Test
	public void defaultLevelRemainsAdvanced() {
		assertEquals(AiVision.AI_ADVANCED, new DdzTableContext().getAiLevel());
		assertEquals(AiVision.AI_ADVANCED, new MjTableContext().getAiLevel());
	}

	@Test
	public void masterLevelIsAcceptedAndHigherValuesAreClamped() {
		DdzTableContext ddz = new DdzTableContext();
		ddz.setAiLevel(AiVision.AI_MASTER);
		assertEquals(AiVision.AI_MASTER, ddz.getAiLevel());
		ddz.setAiLevel(99);
		assertEquals(AiVision.AI_MASTER, ddz.getAiLevel());

		MjTableContext mj = new MjTableContext();
		mj.setAiLevel(AiVision.AI_MASTER);
		assertEquals(AiVision.AI_MASTER, mj.getAiLevel());
	}

	@Test
	public void masterAlwaysUsesFairVision() {
		assertEquals(AiVision.LEVEL_NORMAL,
				AiVision.effectiveVisionLevel(AiVision.LEVEL_FULL, AiVision.AI_MASTER));
		assertEquals(AiVision.LEVEL_FULL,
				AiVision.effectiveVisionLevel(AiVision.LEVEL_FULL, AiVision.AI_ADVANCED));
	}
}
