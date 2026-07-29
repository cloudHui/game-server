package game.manager.table.state;

import game.Game;
import game.manager.table.Table;
import game.manager.table.TableUser;
import game.manager.table.mj.MjDrawService;
import game.manager.table.replay.ReplayRecorder;
import msg.annotation.ProcessEnum;
import msg.registor.enums.TableState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 等待阶段：坐满后开局；无真人则解散；全机器人普通桌可删桌不开局。
 */
@ProcessEnum(TableState.WAITING)
public class Waiting extends AbstractTableHandle {
	private static final Logger logger = LoggerFactory.getLogger(Waiting.class);

	@Override
	public boolean onTiming(Table table) {
		if (table.sitFull()) {
			if (table.isAllRobot() && !table.isRobotRoom()) {
				logger.info("普通桌全机器人不开局，解散桌子, tableId: {}", table.getTableId());
				table.upNextState(TableState.TABLE_DIS);
				return false;
			}
			startGame(table);
			return false;
		}
		if (table.isEmpty() || (!table.hasHumanPlayer() && !table.isRobotRoom())) {
			logger.info("等待阶段无真人，解散桌子, tableId: {}, empty: {}, allRobot: {}",
					table.getTableId(), table.isEmpty(), table.isAllRobot());
			Game.getInstance().getTableManager().removeTableAsync(table.getTableId());
			return true;
		}
		// 未坐满则继续等待；不再使用桌模 waitTimeout 配置。
		return false;
	}

	private void startGame(Table table) {
		table.initGameConfig();
		if (table.getGameType() == 1 && table.getCurrentRound() == 1) {
			game.manager.table.MjTable mjTable = (game.manager.table.MjTable) table;
			mjTable.getMjContext().setDealerSeat(ThreadLocalRandom.current().nextInt(table.getTableModel().getSeatNum()));
		}
		table.dealCards();
		if (table.getGameType() == 1) {
			table.getOp().setCurrOpSeat(((game.manager.table.MjTable) table).getMjContext().getDealerSeat());
		} else if (table.getGameType() == 3 || table.getGameType() == 4) {
			// 跑得快 / 拖拉机：首出座位由各自 dealCards 设定
		} else {
			table.getOp().setCurrOpSeat(0);
		}

		initReplay(table);
		if (table.getGameType() != 4) {
			recordInitHands(table);
		}

		if (table.getGameType() == 1) {
			if (table.getTableModel().getGameSubType() == 1) {
				MjDrawService.flipLaiZi((game.manager.table.MjTable) table);
			}
			table.upNextState(TableState.MJ_DEAL);
		} else if (table.getGameType() == 3) {
			// 跑得快：无叫抢，发完直接出牌
			table.upNextState(TableState.CARD);
		} else if (table.getGameType() == 4) {
			// 拖拉机：START_ANI 中逐张发牌，发牌中可抢主，发完再进亮主回合
			table.upNextState(TableState.START_ANI);
		} else {
			table.upNextState();
		}
	}

	private void initReplay(Table table) {
		ReplayRecorder replay = table.createReplayRecorder();
		if (replay == null) return;

		table.setReplayRecorder(replay);

		Map<Integer, Integer> userIds = new HashMap<>();
		Map<Integer, String> nicknames = new HashMap<>();
		for (Map.Entry<Integer, TableUser> entry : table.getSeatUsers().entrySet()) {
			userIds.put(entry.getKey(), entry.getValue().getUserId());
			nicknames.put(entry.getKey(), entry.getValue().getNick());
		}

		String gameType;
		if (table.getGameType() == 1) {
			switch (table.getTableModel().getGameSubType()) {
				case 1: gameType = "荆门麻将"; break;
				case 2: gameType = "卡五星"; break;
				default: gameType = "麻将"; break;
			}
		} else if (table.getGameType() == 3) {
			gameType = "跑得快";
		} else if (table.getGameType() == 4) {
			gameType = "拖拉机";
		} else {
			gameType = "斗地主";
		}

		replay.writeHeader(gameType, table.getTableModel().getTotalRounds(),
				table.getTableModel().getSeatNum(), userIds, nicknames);
		replay.writeConfig("底分=" + table.getTableModel().getBaseScore()
				+ ", 最大番=" + table.getTableModel().getMaxFan()
				+ ", autoPlay=" + table.getTableModel().getAutoPlay());

		if (table.getGameType() == 1) {
			game.manager.table.MjTable mjTable = (game.manager.table.MjTable) table;
			replay.writeDealerAndLaiZi(mjTable.getMjContext().getDealerSeat(),
					mjTable.getMjContext().getLaiZiTileId(),
					mjTable.getMjContext().getLaiZiFlipTile());
		}
	}

	public static void recordInitHands(Table table) {
		ReplayRecorder replay = table.getReplayRecorder();
		if (replay == null) return;

		Map<Integer, List<Integer>> hands = new HashMap<>();
		for (Map.Entry<Integer, TableUser> entry : table.getSeatUsers().entrySet()) {
			List<Integer> tileIds = new ArrayList<>();
			for (game.manager.table.cards.Card c : entry.getValue().getCards()) {
				tileIds.add(c.getId());
			}
			hands.put(entry.getKey(), tileIds);
		}
		replay.writeInitHands(hands);
	}
}
