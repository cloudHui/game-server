package game.manager.table.state;

import game.manager.table.ddz.DdzTable;
import game.manager.table.Table;
import game.manager.table.TableUser;
import game.manager.table.ddz.DdzPlayService;
import msg.annotation.ProcessEnum;
import msg.registor.enums.TableState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 等待玩家出牌；超时自动处理（首家出最小单张，否则视为过）。
 * 
 * @author cloud
 * @date 2026-05-03
 * @version 1.0
 * @since 1.0
 */
@ProcessEnum(TableState.IDLE_CARD)
public class IdleCardPlay extends AbstractTableHandle {

	private static final Logger logger = LoggerFactory.getLogger(IdleCardPlay.class);

	@Override
	public boolean handle(Table table) {
		int seat = table.getOp().getCurrOpSeat();
		TableUser u = table.getSeatUser(seat);
		if (u != null && u.isRobot()
				&& System.currentTimeMillis() >= table.getStateStartTime() + randomRobotDelay()) {
			overTime(table);
			return false;
		}
		return super.handle(table);
	}

	private long randomRobotDelay() {
		return java.util.concurrent.ThreadLocalRandom.current().nextLong(250, 3001);
	}

	@Override
	public void overTime(Table table) {
		if (table instanceof game.manager.table.pdk.PdkTable) {
			overTimePdk((game.manager.table.pdk.PdkTable) table);
			return;
		}
		if (table instanceof game.manager.table.tractor.TractorTable) {
			game.manager.table.tractor.TractorPlayService.autoPlay(
					(game.manager.table.tractor.TractorTable) table, table.getOp().getCurrOpSeat());
			return;
		}
		DdzTable ddzTable = (DdzTable) table;
		int seat = table.getOp().getCurrOpSeat();
		TableUser u = table.getSeatUser(seat);
		if (u == null) {
			return;
		}
		logger.info("出牌超时自动处理, tableId: {}, seat: {}, userId: {}", table.getTableId(), seat, u.getUserId());
		if (DdzPlayService.autoPlayAi(ddzTable, u.getUserId())) {
			return;
		}
		if (ddzTable.getDdz().getLastHand() == null) {
			DdzPlayService.autoPlaySmallest(ddzTable, u.getUserId());
		} else {
			DdzPlayService.apply(ddzTable, u.getUserId(),
					proto.GameProto.OpInfo.newBuilder().setChoice(proto.ConstProto.Operation.PASS).build());
		}
	}

	private void overTimePdk(game.manager.table.pdk.PdkTable table) {
		int seat = table.getOp().getCurrOpSeat();
		TableUser u = table.getSeatUser(seat);
		if (u == null) return;
		logger.info("跑得快出牌超时, tableId: {}, seat: {}, userId: {}", table.getTableId(), seat, u.getUserId());
		if (game.manager.table.pdk.PdkPlayService.autoPlayAi(table, u.getUserId())) return;
		if (table.getPdk().getLastHand() == null) {
			game.manager.table.pdk.PdkPlayService.autoPlaySmallest(table, u.getUserId());
			return;
		}
		// 有牌必管：能压则不能 PASS，只能再走 AI/出牌；关不上才允许过
		if (table.canCurrentPlayerPass()) {
			game.manager.table.pdk.PdkPlayService.apply(table, u.getUserId(),
					proto.GameProto.OpInfo.newBuilder().setChoice(proto.ConstProto.Operation.PASS).build());
		}
	}
}
