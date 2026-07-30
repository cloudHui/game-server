package game.manager.table.state;

import msg.registor.enums.TableState;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TableStateHandleManagerTest {

	@Test
	public void registersMahjongHandlersOutsideCommonStatePackage() {
		assertTrue(TableStateHandleManager.hasHandle(TableState.MJ_DEAL));
		assertTrue(TableStateHandleManager.hasHandle(TableState.MJ_PLAY));
		assertTrue(TableStateHandleManager.hasHandle(TableState.MJ_DISCARD));
		assertTrue(TableStateHandleManager.hasHandle(TableState.MJ_CLAIM));
	}
}
