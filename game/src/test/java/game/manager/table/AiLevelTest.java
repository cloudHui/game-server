package game.manager.table;

import game.manager.table.ddz.DdzTableContext;
import game.manager.table.ddz.ai.AiVision;
import game.manager.table.mj.MjTableContext;
import game.manager.table.pdk.PdkTableContext;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** 第四档大师 AI 配置边界；斗地主/麻将默认高级，跑得快默认大师。 */
public class AiLevelTest {
	@Test
	public void defaultLevelRemainsAdvanced() {
		assertEquals(AiVision.AI_ADVANCED, new DdzTableContext().getAiLevel());
		assertEquals(AiVision.AI_ADVANCED, new MjTableContext().getAiLevel());
		assertEquals(AiVision.AI_MASTER, new PdkTableContext().getAiLevel());
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
