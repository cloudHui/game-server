package game.manager.table.tractor;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import game.manager.table.GameResult;
import game.manager.table.Table;
import game.manager.table.TableUser;
import game.manager.table.tractor.TractorTable;
import game.manager.table.cards.Card;
import game.db.ScoreRepository;
import game.manager.table.replay.ReplayRecorder;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import proto.GameProto;

/**
 * 拖拉机结算（闲家抓分）：
 * 0 大光庄+3；&lt;40 小光庄+2；&lt;80 庄+1；&lt;120 换庄不升；≥120 闲上台并升级。
 */
public final class TractorSettleService {

	private static final Logger logger = LoggerFactory.getLogger(TractorSettleService.class);

	private TractorSettleService() {}

	static void finishGame(TractorTable table) {
		TractorTableContext ctx = table.getTractor();
		int seatNum = 4;
		int def = ctx.getDefenderScore();
		int[] settle = TractorRules.settleUpgrade(def);
		boolean bankerWin = settle[0] == 1;
		int upgrade = settle[1];
		String winType = bankerWin ? ("banker+" + upgrade) : (upgrade == 0 ? "defend" : "defend+" + upgrade);

		int[] scores = new int[seatNum];
		int delta = bankerWin ? 10 * Math.max(1, upgrade) : -10 * Math.max(1, upgrade == 0 ? 1 : upgrade);
		for (int s = 0; s < seatNum; s++) {
			scores[s] = ctx.isBankerTeam(s) ? delta : -delta;
		}
		int oldBanker = ctx.getBankerSeat();
        int winnerSeat = bankerWin ? oldBanker : ctx.getRoundWinnerSeat();
        if (winnerSeat < 0 || ctx.isBankerTeam(winnerSeat)) {
            winnerSeat = (oldBanker + 1) % 4;
        }
		table.getGameResult().addRound(table.getCurrentRound(), winnerSeat, Math.abs(delta), scores, winType);
		ScoreRepository.getInstance().saveRound(table);
		ReplayRecorder replay = table.getReplayRecorder();
		if (replay != null) {
			replay.writeAuditEvent("结算 闲家抓分 " + def + "，庄家方胜 " + bankerWin
					+ "，升级 " + upgrade + "，赢家座" + winnerSeat
					+ "，各座得分 " + java.util.Arrays.toString(scores));
			replay.writeSettlement(winnerSeat, Math.abs(delta),
					winType + "|闲抓" + def + "|主" + ctx.getTrumpSuit(), scores);
			replay.save();
		}

		if (bankerWin) {
			ctx.upgradeBankerTeam(upgrade);
		} else {
            // 闲家胜利时由实际赢下最后一墩的座位接庄，而不是机械取庄家下家。
            int newBanker = winnerSeat;
			ctx.setBankerSeat(newBanker);
			if (upgrade > 0) ctx.upgradeSeatTeam(newBanker, upgrade);
		}

		GameProto.NotResult.Builder result = GameProto.NotResult.newBuilder()
				.setWinner(table.getSeatUser(winnerSeat) != null ? table.getSeatUser(winnerSeat).getUserId() : 0)
				.setLandlordId(table.getSeatUser(ctx.getBankerSeat()) != null
						? table.getSeatUser(ctx.getBankerSeat()).getUserId() : 0)
				.setWinTeam(bankerWin ? 0 : 1)
				.setBaseScore(def)
				.setRobMultiplier(upgrade)
				.setSpring(def == 0)
				.setAntiSpring(false)
				.setSettleFactor(Math.abs(delta));
		for (TableUser u : table.getSeatUsers().values()) {
			GameProto.RPlayer.Builder rp = GameProto.RPlayer.newBuilder().setRoleId(u.getUserId());
			// 小结算余牌按拖拉机手牌序展示
			List<Card> remain = new ArrayList<>(u.getCards());
			TractorRules.sortHand(remain, ctx.getLevelRank(), ctx.getTrumpSuit());
			for (Card c : remain) rp.addCards(GameProto.Card.newBuilder().setValue(c.getId()));
			result.addRPlayers(rp.build());
		}
		table.sendTableMessage(result.build(), GMsg.NOT_RESULT);

		if (table.isMultiRound()) {
			GameProto.NotRoundResult.Builder round = GameProto.NotRoundResult.newBuilder()
					.setRound(table.getCurrentRound())
					.setWinnerSeat(winnerSeat)
					.setFan(Math.abs(delta))
					.setWinType(ByteString.copyFromUtf8(winType + "|级" + TractorRules.levelName(ctx.getLevelRank())
							+ "|闲抓" + def
							+ "|主" + ctx.getTrumpSuit()));
			for (int i = 0; i < seatNum; i++) {
				round.addSeatScores(GameProto.SeatScore.newBuilder().setSeat(i).setScore(scores[i]));
				round.addTotalScores(GameProto.SeatScore.newBuilder()
						.setSeat(i).setScore(table.getGameResult().getTotalScore(i)));
			}
			table.sendTableMessage(round.build(), GMsg.NOT_ROUND_RESULT);
		}
		table.upNextStateWithTime(TableState.TABLE_OVER, System.currentTimeMillis());
		logger.info("拖拉机结算 defScore:{} bankerWin:{} upgrade:{} level:{} trump:{}",
				def, bankerWin, upgrade, ctx.getLevelRank(), ctx.getTrumpSuit());
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
