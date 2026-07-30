package game.manager.table.state;

import game.manager.table.ddz.DdzTable;
import game.manager.table.Table;
import game.manager.table.TableUser;
import game.manager.table.RobotOperationDelay;
import game.manager.table.ddz.DdzBidService;
import msg.annotation.ProcessEnum;
import msg.registor.enums.TableState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 等待叫地主；超时视为「不叫」。
 * 
 * @author cloud
 * @date 2026-05-03
 * @version 1.0
 * @since 1.0
 */
@ProcessEnum(TableState.IDLE_ROB)
public class IdleRob extends AbstractTableHandle {

	private static final Logger logger = LoggerFactory.getLogger(IdleRob.class);

	@Override
	public boolean handle(Table table) {
		if (table instanceof game.manager.table.tractor.TractorTable) {
			long now = System.currentTimeMillis();
			int seat = table.getOp().getCurrOpSeat();
			TableUser u = table.getSeatUser(seat);
			if (u != null && u.isRobot()
					&& now >= table.getStateStartTime() + randomRobotDelay()) {
				game.manager.table.tractor.TractorBidService.autoBid(
						(game.manager.table.tractor.TractorTable) table, seat);
				return false;
			}
			if (now >= table.getStateStartTime()
					+ game.manager.table.tractor.TractorBidService.DECLARE_SECONDS * 1000L) {
				overTime(table);
			}
			return false;
		}
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
		return RobotOperationDelay.randomMillis();
	}

	@Override
	public void overTime(Table table) {
		if (table instanceof game.manager.table.tractor.TractorTable) {
			logger.info("拖拉机亮主超时, tableId: {}", table.getTableId());
			game.manager.table.tractor.TractorBidService.onTimeout(
					(game.manager.table.tractor.TractorTable) table);
			return;
		}
		logger.info("叫分/抢地主超时, tableId: {}", table.getTableId());
		DdzTable ddzTable = (DdzTable) table;
		DdzBidService.onBidTimeout(ddzTable);
	}
}
