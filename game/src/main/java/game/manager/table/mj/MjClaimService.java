package game.manager.table.mj;

import game.manager.table.MjTable;
import game.manager.table.cards.Card;
import proto.GameProto;

import java.util.List;

/**
 * 麻将 claim 门面：对外保持原调用点，内部委托 MjClaimDetector / MjClaimExecutor。
 */
public final class MjClaimService {

	private MjClaimService() {}

	public static MjClaimInfo buildClaimInfo(MjTable table, int seat) {
		return MjClaimDetector.buildClaimInfo(table, seat);
	}

	static MjClaimInfo detectSeatClaim(MjTable table, int seat, List<Card> handTiles,
			List<MjExposedSet> exposedSets, int tileId, int fromSeat) {
		return MjClaimDetector.detectSeatClaim(table, seat, handTiles, exposedSets, tileId, fromSeat);
	}

	public static boolean checkClaim(MjTable table) {
		return MjClaimDetector.checkClaim(table);
	}

	public static boolean applyClaim(MjTable table, int userId, GameProto.OpInfo op) {
		return MjClaimExecutor.applyClaim(table, userId, op);
	}

	public static void clearClaimState(MjTable table) {
		MjClaimExecutor.clearClaimState(table);
	}

	public static void timeoutClaim(MjTable table) {
		MjClaimExecutor.timeoutClaim(table);
	}

	/** 处理胡牌（包内可见，供 MjGangService 抢杠调用） */
	static boolean processHu(MjTable table, int seat, int tileId, int fromSeat, boolean qiangGang) {
		return MjClaimExecutor.processHu(table, seat, tileId, fromSeat, qiangGang);
	}
}
