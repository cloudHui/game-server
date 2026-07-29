package game.manager.table.pdk;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import game.manager.table.GameResult;
import game.manager.table.pdk.PdkTable;
import game.manager.table.Table;
import game.manager.table.TableUser;
import game.manager.table.cards.Card;
import game.db.ScoreRepository;
import game.manager.table.replay.ReplayRecorder;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import proto.GameProto;

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
			replay.writeSettlement(winSeat, totalGain, "normal", scores);
			replay.save();
		}

		List<GameProto.RPlayer> rPlayers = new ArrayList<>();
		for (TableUser u : table.getSeatUsers().values()) {
			GameProto.RPlayer.Builder rp = GameProto.RPlayer.newBuilder().setRoleId(u.getUserId());
			for (Card c : u.getCards()) {
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
