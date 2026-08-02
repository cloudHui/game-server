package game.manager.table.tractor;

import com.google.protobuf.ByteString;
import game.db.ScoreRepository;
import game.manager.table.Table;
import game.manager.table.TableUser;
import game.manager.table.cards.Card;
import game.manager.table.pdk.PdkSettleService;
import game.manager.table.replay.ReplayRecorder;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.GameProto;

/**
 * 拖拉机结算（闲家抓分）：
 * 0 大光庄+3；&lt;40 小光庄+2；&lt;80 庄+1；&lt;120 换庄不升；≥120 闲上台并升级。
 */
public final class TractorSettleService {

    private static final Logger logger = LoggerFactory.getLogger(TractorSettleService.class);

    private TractorSettleService() {
    }

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
        int winnerSeat = bankerWin ? oldBanker : (oldBanker + 1) % 4;
        table.getGameResult().addRound(table.getCurrentRound(), winnerSeat, Math.abs(delta), scores, winType);
        ScoreRepository.getInstance().saveRound(table);
        ReplayRecorder replay = table.getReplayRecorder();
        if (replay != null) {
            replay.writeSettlement(winnerSeat, Math.abs(delta),
                    winType + "|闲抓" + def + "|主" + ctx.getTrumpSuit(), scores);
            replay.save();
        }

        if (bankerWin) {
            ctx.upgradeBankerTeam(upgrade);
        } else {
            int newBanker = (oldBanker + 1) % 4;
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
            for (Card c : u.getCards()) rp.addCards(GameProto.Card.newBuilder().setValue(c.getId()));
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
        PdkSettleService.sendGameResult(table);
    }
}
