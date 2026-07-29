package game.manager.table.state;

import game.manager.table.Table;
import game.manager.table.TableUser;
import game.manager.table.tractor.TractorTable;
import msg.annotation.ProcessEnum;
import msg.registor.enums.TableState;

/**
 * 等地主明牌 / 拖拉机庄家扣底（拿底后 30 秒内放回 8 张，然后出牌）。
 */
@ProcessEnum(TableState.IDLE_SHOW_CARD)
public class IdleShowCard extends AbstractTableHandle {

	/** 拖拉机扣底时限（秒）：拿上来后需在此时限内放回 8 张 */
	public static final int TRACTOR_BURY_SECONDS = 30;

	@Override
	public boolean handle(Table table) {
		if (!(table instanceof TractorTable)) {
			return super.handle(table);
		}
		TractorTable t = (TractorTable) table;
		int seat = t.getTractor().getBankerSeat();
		TableUser u = table.getSeatUser(seat);
		long now = System.currentTimeMillis();
		long deadline = table.getStateStartTime() + TRACTOR_BURY_SECONDS * 1000L;

		// 机器人到点自动扣底
		if (u != null && u.isRobot()
				&& now >= table.getStateStartTime() + randomRobotDelay()) {
			finishBuryAndPlay(t, seat);
			return false;
		}
		// 30 秒超时：自动扣底并开出
		if (now >= deadline) {
			finishBuryAndPlay(t, seat);
			return false;
		}
		return false;
	}

	private long randomRobotDelay() {
		return java.util.concurrent.ThreadLocalRandom.current().nextLong(800, 5001);
	}

	@Override
	public void overTime(Table table) {
		if (table instanceof TractorTable) {
			TractorTable t = (TractorTable) table;
			finishBuryAndPlay(t, t.getTractor().getBankerSeat());
			return;
		}
		table.upNextState(TableState.CARD);
	}

	private static void finishBuryAndPlay(TractorTable t, int seat) {
		if (t.getTractor().getBuriedCards().isEmpty()) {
			t.getCardPool().autoBury(t, seat);
		}
		t.getOp().setCurrOpSeat(seat);
		t.getTractor().setTrickLeader(seat);
		t.upNextState(TableState.CARD);
	}
}
