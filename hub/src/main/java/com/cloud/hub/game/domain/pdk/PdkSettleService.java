package com.cloud.hub.game.domain.pdk;

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
 * 跑得快结算：按剩余张数计分；被关（从未出牌）翻倍。
 */
public final class PdkSettleService {

	private static final Logger logger = LoggerFactory.getLogger(PdkSettleService.class);

	private PdkSettleService() {}

	/**
	 * 执行跑得快单局结算逻辑。
	 *
	 * @param table  当前跑得快牌桌
	 * @param winner 本局赢家（首个出完手牌者）
	 */
	static void finishGame(PdkTable table, TableUser winner) {
		PdkTableContext ctx = table.getPdk();
		int seatNum = table.getTableModel().getSeatNum();
		int winSeat = winner.getSeated();

		int[] scores = new int[seatNum];
		int totalGain = calcPdkScores(table, ctx, winSeat, seatNum, scores);

		table.getGameResult().addRound(table.getCurrentRound(), winSeat, totalGain, scores, "normal");
		ScoreRepository.getInstance().saveRound(table);
		ctx.setFirstSeat(winSeat);

		com.cloud.hub.framework.metrics.HubMetrics metrics = com.cloud.hub.framework.metrics.HubMetrics.getInstance();
		if (metrics != null) {
			metrics.recordRoundSettled("pdk");
		}

		recordPdkReplay(table, winSeat, totalGain, scores);
		broadcastPdkResult(table, winner, winSeat, seatNum, totalGain, scores);

		table.upNextStateWithTime(TableState.TABLE_OVER, System.currentTimeMillis());
		logger.info("跑得快结算完成, tableId: {}, winnerSeat: {}, scores: {}",
				table.getTableId(), winSeat, java.util.Arrays.toString(scores));
	}

	/** 计算剩余牌计分与全关翻倍 */
	private static int calcPdkScores(PdkTable table, PdkTableContext ctx, int winSeat, int seatNum, int[] scores) {
		int totalGain = 0;
		for (int s = 0; s < seatNum; s++) {
			if (s == winSeat) continue;
			TableUser u = table.getSeatUser(s);
			int left = u == null ? 0 : u.getCards().size();
			int lose = left;
			if (!ctx.hasPlayed(s) && left > 0) lose *= 2; // 被关门翻倍
			scores[s] = -lose;
			totalGain += lose;
		}
		scores[winSeat] = totalGain;
		return totalGain;
	}

	/** 记录回放审计 */
	private static void recordPdkReplay(PdkTable table, int winSeat, int totalGain, int[] scores) {
		ReplayRecorder replay = table.getReplayRecorder();
		if (replay != null) {
			replay.writeAuditEvent("结算 赢家座" + winSeat + " 剩余牌计分/关门翻倍，各座得分 "
					+ java.util.Arrays.toString(scores));
			replay.writeSettlement(winSeat, totalGain, "normal", scores);
			replay.save();
		}
	}

	/** 广播单局与多局结算协议包 */
	private static void broadcastPdkResult(PdkTable table, TableUser winner, int winSeat, int seatNum, int totalGain, int[] scores) {
		GameProto.NotResult.Builder result = GameProto.NotResult.newBuilder()
				.setWinner(winner.getUserId())
				.setLandlordId(0).setWinTeam(0).setBaseScore(1).setRobMultiplier(1)
				.setSpring(false).setAntiSpring(false).setSettleFactor(totalGain);

		for (TableUser u : table.getSeatUsers().values()) {
			GameProto.RPlayer.Builder rp = GameProto.RPlayer.newBuilder().setRoleId(u.getUserId());
			List<Card> remain = new ArrayList<>(u.getCards());
			remain.sort(java.util.Collections.reverseOrder());
			for (Card c : remain) rp.addCards(GameProto.Card.newBuilder().setValue(c.getId()));
			result.addRPlayers(rp.build());
		}
		table.sendTableMessage(result.build(), GMsg.NOT_RESULT);

		if (table.isMultiRound()) {
			GameProto.NotRoundResult.Builder roundResult = GameProto.NotRoundResult.newBuilder()
					.setRound(table.getCurrentRound())
					.setWinnerSeat(winSeat)
					.setFan(totalGain)
					.setWinType(ByteString.copyFromUtf8("normal"));
			for (int i = 0; i < seatNum; i++) {
				roundResult.addSeatScores(GameProto.SeatScore.newBuilder().setSeat(i).setScore(scores[i]));
				roundResult.addTotalScores(GameProto.SeatScore.newBuilder()
						.setSeat(i).setScore(table.getGameResult().getTotalScore(i)));
			}
			table.sendTableMessage(roundResult.build(), GMsg.NOT_ROUND_RESULT);
		}
	}


}
