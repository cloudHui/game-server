package com.cloud.hub.game.domain.tractor;

import com.cloud.hub.game.db.ScoreRepository;
import com.cloud.hub.game.domain.table.GameResult;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.replay.ReplayRecorder;
import com.google.protobuf.ByteString;
import utils.registry.enums.TableState;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.GameProto;

import java.util.ArrayList;
import java.util.List;

/**
 * 拖拉机结算（闲家抓分）：
 * 0 大光庄+3；&lt;40 小光庄+2；&lt;80 庄+1；&lt;120 换庄不升；≥120 闲上台并升级。
 */
public final class TractorSettleService {

	private static final Logger logger = LoggerFactory.getLogger(TractorSettleService.class);

	private TractorSettleService() {}

	/**
	 * 执行拖拉机单局结算逻辑。
	 *
	 * @param table 当前拖拉机牌桌
	 */
	static void finishGame(TractorTable table) {
		TractorTableContext ctx = table.getTractor();
		int seatNum = 4;
		int def = ctx.getDefenderScore();
		int[] settle = TractorRules.settleUpgrade(def);
		boolean bankerWin = settle[0] == 1;
		int upgrade = settle[1];
		String winType = bankerWin ? ("banker+" + upgrade) : (upgrade == 0 ? "defend" : "defend+" + upgrade);

		int[] scores = new int[seatNum];
		int delta = calcTractorScores(ctx, bankerWin, upgrade, seatNum, scores);
		int winnerSeat = resolveWinnerSeat(ctx, bankerWin);

		table.getGameResult().addRound(table.getCurrentRound(), winnerSeat, Math.abs(delta), scores, winType);
		ScoreRepository.getInstance().saveRound(table);
		if (com.cloud.hub.framework.metrics.HubMetrics.getInstance() != null) {
			com.cloud.hub.framework.metrics.HubMetrics.getInstance().recordRoundSettled("tractor");
		}

		recordTractorReplay(table, ctx, def, bankerWin, upgrade, winnerSeat, winType, scores, delta);
		updateBankerAndLevel(ctx, bankerWin, winnerSeat, upgrade);
		broadcastTractorResult(table, ctx, winnerSeat, bankerWin, def, upgrade, delta, winType, seatNum, scores);

		table.upNextStateWithTime(TableState.TABLE_OVER, System.currentTimeMillis());
		logger.info("[Tractor-Settle] 拖拉机单局结算完成, TableId: {}, Round: {}, WinnerSeat: {}, DefScore: {}, BankerWin: {}, Upgrade: {}, Level: {}, Trump: {}",
				table.getTableId(), table.getCurrentRound(), winnerSeat, def, bankerWin, upgrade, ctx.getLevelRank(), ctx.getTrumpSuit());
	}

	/** 计算庄闲双方得分增量与各座位分值 */
	private static int calcTractorScores(TractorTableContext ctx, boolean bankerWin, int upgrade, int seatNum, int[] scores) {
		int delta = bankerWin ? 10 * Math.max(1, upgrade) : -10 * Math.max(1, upgrade == 0 ? 1 : upgrade);
		for (int s = 0; s < seatNum; s++) {
			scores[s] = ctx.isBankerTeam(s) ? delta : -delta;
		}
		return delta;
	}

	/** 解析最终胜出接庄的座位号 */
	private static int resolveWinnerSeat(TractorTableContext ctx, boolean bankerWin) {
		int oldBanker = ctx.getBankerSeat();
		int winnerSeat = bankerWin ? oldBanker : ctx.getRoundWinnerSeat();
		if (winnerSeat < 0 || ctx.isBankerTeam(winnerSeat)) {
			winnerSeat = (oldBanker + 1) % 4;
		}
		return winnerSeat;
	}

	/** 记录对局回放与审计流水 */
	private static void recordTractorReplay(TractorTable table, TractorTableContext ctx, int def, boolean bankerWin,
											int upgrade, int winnerSeat, String winType, int[] scores, int delta) {
		ReplayRecorder replay = table.getReplayRecorder();
		if (replay != null) {
			replay.writeAuditEvent("结算 闲家抓分 " + def + "，庄家方胜 " + bankerWin
					+ "，升级 " + upgrade + "，赢家座" + winnerSeat
					+ "，各座得分 " + java.util.Arrays.toString(scores));
			replay.writeSettlement(winnerSeat, Math.abs(delta),
					winType + "|闲抓" + def + "|主" + ctx.getTrumpSuit(), scores);
			replay.save();
		}
	}

	/** 更新庄家位置与团队级数 */
	private static void updateBankerAndLevel(TractorTableContext ctx, boolean bankerWin, int winnerSeat, int upgrade) {
		if (bankerWin) {
			ctx.upgradeBankerTeam(upgrade);
		} else {
			ctx.setBankerSeat(winnerSeat);
			if (upgrade > 0) ctx.upgradeSeatTeam(winnerSeat, upgrade);
		}
	}

	/** 广播单局与多局结算通知 */
	private static void broadcastTractorResult(TractorTable table, TractorTableContext ctx, int winnerSeat,
											  boolean bankerWin, int def, int upgrade, int delta,
											  String winType, int seatNum, int[] scores) {
		GameProto.NotResult.Builder result = GameProto.NotResult.newBuilder()
				.setWinner(table.getSeatUser(winnerSeat) != null ? table.getSeatUser(winnerSeat).getUserId() : 0)
				.setLandlordId(table.getSeatUser(ctx.getBankerSeat()) != null
						? table.getSeatUser(ctx.getBankerSeat()).getUserId() : 0)
				.setWinTeam(bankerWin ? 0 : 1).setBaseScore(def).setRobMultiplier(upgrade)
				.setSpring(def == 0).setAntiSpring(false).setSettleFactor(Math.abs(delta));

		for (TableUser u : table.getSeatUsers().values()) {
			GameProto.RPlayer.Builder rp = GameProto.RPlayer.newBuilder().setRoleId(u.getUserId());
			List<Card> remain = new ArrayList<>(u.getCards());
			TractorRules.sortHand(remain, ctx.getLevelRank(), ctx.getTrumpSuit());
			for (Card c : remain) rp.addCards(GameProto.Card.newBuilder().setValue(c.getId()));
			result.addRPlayers(rp.build());
		}
		table.sendTableMessage(result.build(), GMsg.NOT_RESULT);

		if (table.isMultiRound()) {
			GameProto.NotRoundResult.Builder round = GameProto.NotRoundResult.newBuilder()
					.setRound(table.getCurrentRound()).setWinnerSeat(winnerSeat).setFan(Math.abs(delta))
					.setWinType(ByteString.copyFromUtf8(winType + "|级" + TractorRules.levelName(ctx.getLevelRank())
							+ "|闲抓" + def + "|主" + ctx.getTrumpSuit()));
			for (int i = 0; i < seatNum; i++) {
				round.addSeatScores(GameProto.SeatScore.newBuilder().setSeat(i).setScore(scores[i]));
				round.addTotalScores(GameProto.SeatScore.newBuilder().setSeat(i).setScore(table.getGameResult().getTotalScore(i)));
			}
			table.sendTableMessage(round.build(), GMsg.NOT_ROUND_RESULT);
		}
	}


}
