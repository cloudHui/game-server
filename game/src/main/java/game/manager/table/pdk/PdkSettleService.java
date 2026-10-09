package game.manager.table.pdk;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import game.manager.table.GameResult;
import game.manager.table.Table;
import game.manager.table.TableUser;
import game.manager.table.cards.Card;
import game.db.ScoreRepository;
import game.manager.table.replay.ReplayRecorder;
import utils.registry.enums.TableState;
import msg.registor.message.GMsg;
import proto.GameProto;

/**
 * 跑得快单局结算与多局总结算服务。
 * <p>
 * 核心计分规则：
 * <ul>
 *   <li><b>基准计分：</b>输家按自身剩余手牌张数扣分（1 张手牌扣 1 分）；</li>
 *   <li><b>关门翻倍：</b>若输家在本小局内一次牌都没出过（被关死），则扣分翻倍（扣分 = 剩余张数 * 2）；</li>
 *   <li><b>赢家得分：</b>赢家收益为场上所有输家失分的总和（零和博弈）。</li>
 * </ul>
 */
public final class PdkSettleService {

    private static final Logger logger = LoggerFactory.getLogger(PdkSettleService.class);

    private PdkSettleService() {}

    /**
     * 单小局结束结算：计算得分、持久化战绩、记录对局回放并下发结算网络协议。
     *
     * @param table  当前跑得快牌桌对象
     * @param winner 本局获胜玩家（手牌率先出完者）
     */
    static void finishGame(PdkTable table, TableUser winner) {
        PdkTableContext ctx = table.getPdk();
        int seatNum = table.getTableModel().getSeatNum();
        int[] scores = new int[seatNum];
        int winSeat = winner.getSeated();
        int totalGain = 0;

        // 1. 遍历所有非赢家座位，计算各座失分并汇总给赢家
        for (int s = 0; s < seatNum; s++) {
            if (s == winSeat) {
                continue;
            }
            TableUser u = table.getSeatUser(s);
            int left = u == null ? 0 : u.getCards().size();
            int lose = left;

            // 关门判定：若玩家本小局从未出过牌且手中有余牌，触发跑得快经典“关门”惩罚，失分翻倍
            if (!ctx.hasPlayed(s) && left > 0) {
                lose *= 2;
            }
            scores[s] = -lose;
            totalGain += lose;
        }
        // 赢家总得分为全部输家扣分总额
        scores[winSeat] = totalGain;

        // 2. 战绩持久化与首出座位更迭
        table.getGameResult().addRound(table.getCurrentRound(), winSeat, totalGain, scores, "normal");
        ScoreRepository.getInstance().saveRound(table);
        // 跑得快规则：本局赢家获得下一局的首轮优先出牌权
        ctx.setFirstSeat(winSeat);

        // 3. 记录对局回放结算信息
        ReplayRecorder replay = table.getReplayRecorder();
        if (replay != null) {
            replay.writeAuditEvent("结算 赢家座" + winSeat + " 剩余牌计分/关门翻倍，各座得分 "
                    + java.util.Arrays.toString(scores));
            replay.writeSettlement(winSeat, totalGain, "normal", scores);
            replay.save();
        }

        // 4. 组装小结算玩家展示列表（客户端需亮明各家底牌与剩余手牌）
        List<GameProto.RPlayer> rPlayers = new ArrayList<>();
        for (TableUser u : table.getSeatUsers().values()) {
            GameProto.RPlayer.Builder rp = GameProto.RPlayer.newBuilder().setRoleId(u.getUserId());
            // 剩余手牌按点数降序排列，避免客户端渲染牌型时出现视觉乱序
            List<Card> remain = new ArrayList<>(u.getCards());
            remain.sort(java.util.Collections.reverseOrder());
            for (Card c : remain) {
                rp.addCards(GameProto.Card.newBuilder().setValue(c.getId()));
            }
            rPlayers.add(rp.build());
        }

        // 5. 下发单局结果通知协议（NotResult）
        GameProto.NotResult.Builder result = GameProto.NotResult.newBuilder()
                .setWinner(winner.getUserId())
                .setLandlordId(0) // 跑得快无地主角色，填充缺省 0
                .setWinTeam(0)
                .setBaseScore(1)
                .setRobMultiplier(1)
                .setSpring(false)
                .setAntiSpring(false)
                .setSettleFactor(totalGain);
        for (GameProto.RPlayer rp : rPlayers) {
            result.addRPlayers(rp);
        }
        table.sendTableMessage(result.build(), GMsg.NOT_RESULT);

        // 6. 若为多局赛制（如 4 局/8 局房卡局），广播局数小结与累积总分
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

        // 7. 流转桌子状态至 TABLE_OVER，触发后续局间准备倒计时或总结算判定
        table.upNextStateWithTime(TableState.TABLE_OVER, System.currentTimeMillis());
        logger.info("跑得快结算 table:{} winnerSeat:{} scores:{}",
                table.getTableId(), winSeat, java.util.Arrays.toString(scores));
    }

    /**
     * 发送整场多局对局的总结算账单通知。
     * <p>
     * 涵盖全部局数的胜负明细、每局番数、各座位总积分汇总，通知客户端弹出总结算大面板。
     *
     * @param table 当前牌桌
     */
    public static void sendGameResult(Table table) {
        GameResult gameResult = table.getGameResult();
        int seatNum = table.getTableModel().getSeatNum();
        GameProto.NotGameResult.Builder builder = GameProto.NotGameResult.newBuilder()
                .setTotalRounds(gameResult.getTotalRounds())
                .setCompletedRounds(gameResult.getCompletedRounds());

        // 汇总各座位的整场累积总积分
        for (int i = 0; i < seatNum; i++) {
            builder.addTotalScores(GameProto.SeatScore.newBuilder().setSeat(i).setScore(gameResult.getTotalScore(i)));
        }

        // 提取每小局的历史战绩快照
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

