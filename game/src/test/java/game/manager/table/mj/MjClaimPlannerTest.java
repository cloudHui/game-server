package game.manager.table.mj;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 吃碰杠胡优先级与截胡顺序。
 */
public class MjClaimPlannerTest {

	@Test
	public void sidePriority_gangOverPengOverChi() {
		MjClaimInfo hu = new MjClaimInfo(0, true, false, false, false, 101, 0, null);
		MjClaimInfo gang = new MjClaimInfo(1, false, true, true, false, 101, 101, null);
		MjClaimInfo peng = new MjClaimInfo(2, false, false, true, false, 101, 0, null);
		MjClaimInfo chi = new MjClaimInfo(3, false, false, false, true, 101, 0,
				Arrays.asList(new int[]{102, 103}));
		assertTrue(MjClaimPlanner.priority(hu) > MjClaimPlanner.priority(gang));
		assertTrue(MjClaimPlanner.sidePriority(gang) > MjClaimPlanner.sidePriority(peng));
		assertTrue(MjClaimPlanner.sidePriority(peng) > MjClaimPlanner.sidePriority(chi));
	}

	@Test
	public void huSeatsKeepCcwOrder() {
		List<MjClaimInfo> claims = Arrays.asList(
				new MjClaimInfo(1, true, false, false, false, 101, 0, null),
				new MjClaimInfo(2, false, false, true, false, 101, 0, null),
				new MjClaimInfo(3, true, false, false, false, 101, 0, null));
		assertEquals(Arrays.asList(1, 3), MjClaimPlanner.huSeatsInOrder(claims));
	}

	@Test
	public void sideCandidates_priorityThenCcw() {
		// fromSeat=0：下家1仅碰，对家2可杠 → 杠优先于碰
		Map<Integer, MjClaimInfo> bySeat = new LinkedHashMap<>();
		bySeat.put(1, new MjClaimInfo(1, false, false, true, false, 101, 0, null));
		bySeat.put(2, new MjClaimInfo(2, false, true, false, false, 101, 101, null));
		List<MjClaimInfo> sides = MjClaimPlanner.sideCandidates(bySeat, 0, 4);
		assertEquals(2, sides.get(0).getSeat());
		assertEquals(1, sides.get(1).getSeat());
	}

	@Test
	public void ccwDistance_xiaJiaIsOne() {
		assertEquals(1, MjClaimPlanner.ccwDistance(0, 1, 4));
		assertEquals(2, MjClaimPlanner.ccwDistance(0, 2, 4));
		assertEquals(3, MjClaimPlanner.ccwDistance(0, 3, 4));
	}

	@Test
	public void claimTimeoutIsFifteenSeconds() {
		assertEquals(15, msg.registor.enums.TableState.MJ_CLAIM.getOverTime());
		assertEquals(15, msg.registor.enums.TableState.MJ_DISCARD.getOverTime());
	}

	@Test
	public void chiSeat_followsServerNextSeatDirection() {
		assertTrue(MjClaimDetector.isNextSeat(0, 1, 4));
		assertTrue(MjClaimDetector.isNextSeat(3, 0, 4));
		assertFalse(MjClaimDetector.isNextSeat(0, 3, 4));
		assertFalse(MjClaimDetector.isNextSeat(2, 1, 4));
	}

	@Test
	public void chiSeat_supportsTwoAndThreePlayerTables() {
		assertTrue(MjClaimDetector.isNextSeat(1, 0, 2));
		assertTrue(MjClaimDetector.isNextSeat(2, 0, 3));
		assertFalse(MjClaimDetector.isNextSeat(0, 2, 3));
	}
}
