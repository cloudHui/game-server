package game.manager.table.card.mj;

import model.tablemodel.TableModel;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/** 二人/三人删牌与卡五星牌墙编排测试 */
public class MjWallComposerTest {

	@Test
	public void fourPlayerFullWall() {
		TableModel m = model(4, 0);
		List<Integer> wall = MjWallComposer.compose(m);
		assertEquals(136, wall.size());
		assertTrue(wall.contains(MjConst.encode(MjConst.SUIT_WAN, 5)));
	}

	@Test
	public void twoPlayerRemovesWan() {
		TableModel m = model(2, 0);
		List<Integer> wall = MjWallComposer.compose(m);
		// 条筒风箭 = (9+9+4+3)*4 = 100
		assertEquals(100, wall.size());
		for (int id : wall) {
			assertNotEquals(MjConst.SUIT_WAN, MjConst.suitOf(id));
		}
	}

	@Test
	public void threePlayerKeepsOnlyYaoJiuWan() {
		TableModel m = model(3, 0);
		List<Integer> wall = MjWallComposer.compose(m);
		// 一九万各4 + 条筒风箭100 = 108
		assertEquals(108, wall.size());
		Set<Integer> wanVals = new HashSet<>();
		for (int id : wall) {
			if (MjConst.suitOf(id) == MjConst.SUIT_WAN) {
				wanVals.add(MjConst.valueOf(id));
			}
		}
		assertEquals(new HashSet<>(Arrays.asList(1, 9)), wanVals);
		assertEquals(8, countOf(wall, MjConst.encode(MjConst.SUIT_WAN, 1))
				+ countOf(wall, MjConst.encode(MjConst.SUIT_WAN, 9)));
	}

	@Test
	public void kaWuXingOnlyWanTiao() {
		TableModel m = model(4, 2);
		List<Integer> wall = MjWallComposer.compose(m);
		assertEquals(72, wall.size());
		for (int id : wall) {
			int s = MjConst.suitOf(id);
			assertTrue(s == MjConst.SUIT_WAN || s == MjConst.SUIT_TIAO);
		}
	}

	private static TableModel model(int seatNum, int subType) {
		TableModel m = new TableModel();
		m.setSeatNum(seatNum);
		m.setGameSubType(subType);
		return m;
	}

	private static int countOf(List<Integer> wall, int tileId) {
		int n = 0;
		for (int id : wall) if (id == tileId) n++;
		return n;
	}
}
