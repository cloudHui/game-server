package com.cloud.weball.game.domain.pdk;

import com.cloud.weball.game.db.ScoreRepository;
import com.cloud.weball.game.domain.table.GameResult;
import com.cloud.weball.game.domain.table.Table;
import com.cloud.weball.game.domain.table.TableUser;
import com.cloud.weball.game.domain.cards.Card;
import com.cloud.weball.game.domain.replay.ReplayRecorder;
import com.google.protobuf.ByteString;
import msg.registor.enums.TableState;
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

	static void finishGame(PdkTable table, TableUser winner) {
		PdkTableContext ctx = table.getPdk();
		int seatNum = table.getTableModel().getSeatNum();
		int[] scores = new int[seatNum];
		int winSeat = winner.getSeated();
		int totalGain = 0;
		for (int s = 0; s < seatNum; s++) {
			if (s == winSeat) continue;
			TableUser u = table.getSeatUser(s);
			int left = u == null ? 0 : u.getCards().size();
			int lose = left;
			if (!ctx.hasPlayed(s) && left > 0) lose *= 2;
			scores[s] = -lose;
			totalGain += lose;
		}
		scores[winSeat] = totalGain;

		table.getGameResult().addRound(table.getCurrentRound(), winSeat, totalGain, scores, "normal");
		ScoreRepository.getInstance().saveRound(table);
		ctx.setFirstSeat(winSeat);
		ReplayRecorder replay = table.getReplayRecorder();
		if (replay != null) {
			replay.writeAuditEvent("结算 赢家座" + winSeat + " 剩余牌计分/关门翻倍，各座得分 "
					+ java.util.Arrays.toString(scores));
			replay.writeSettlement(winSeat, totalGain, "normal", scores);
			replay.save();
		}

		List<GameProto.RPlayer> rPlayers = new ArrayList<>();
		for (TableUser u : table.getSeatUsers().values()) {
			GameProto.RPlayer.Builder rp = GameProto.RPlayer.newBuilder().setRoleId(u.getUserId());
			// 小结算余牌按手牌点数序展示，避免乱序
			List<Card> remain = new ArrayList<>(u.getCards());
			remain.sort(java.util.Collections.reverseOrder());
			for (Card c : remain) {
				rp.addCards(GameProto.Card.newBuilder().setValue(c.getId()));
			}
			rPlayers.add(rp.build());
		}
		GameProto.NotResult.Builder result = GameProto.NotResult.newBuilder()
				.setWinner(winner.getUserId())
				.setLandlordId(0)
				.setWinTeam(0)
				.setBaseScore(1)
				.setRobMultiplier(1)
				.setSpring(false)
				.setAntiSpring(false)
				.setSettleFactor(totalGain);
		for (GameProto.RPlayer rp : rPlayers) result.addRPlayers(rp);
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

		table.upNextStateWithTime(TableState.TABLE_OVER, System.currentTimeMillis());
		logger.info("跑得快结算 table:{} winnerSeat:{} scores:{}",
				table.getTableId(), winSeat, java.util.Arrays.toString(scores));
	}

	public static void sendGameResult(Table table) {
		GameResult gameResult = table.getGameResult();
		int seatNum = table.getTableModel().getSeatNum();
		GameProto.NotGameResult.Builder builder = GameProto.NotGameResult.newBuilder()
				.setTotalRounds(gameResult.getTotalRounds())
				.setCompletedRounds(gameResult.getCompletedRounds());
		for (int i = 0; i < seatNum; i++) {
			builder.addTotalScores(GameProto.SeatScore.newBuilder().setSeat(i).setScore(gameResult.getTotalScore(i)));
		}
		for (GameResult.RoundEntry entry : gameResult.getRoundEntries()) {
			GameProto.RoundSummary.Builder summary = GameProto.RoundSummary.newBuilder()
					.setRound(entry.getRound())
					.setWinnerSeat(entry.getWinnerSeat())
					.setFan(entry.getScore())
					.setWinType(ByteString.copyFromUtf8(entry.getWinType()));
			for (int i = 0; i < seatNum; i++) {
				summary.addSeatScores(GameProto.SeatScore.newBuilder().setSeat(i).setScore(entry.getScores()[i]));
			}
			builder.addRounds(summary.build());
		}
		table.sendTableMessage(builder.build(), GMsg.NOT_GAME_RESULT);
	}
}
