package game.manager.table.ddz;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.ByteString;

import game.manager.table.GameResult;
import game.manager.table.Table;
import game.manager.table.TableUser;
import game.manager.table.cards.Card;
import game.manager.table.replay.DdzReplayRecorder;
import game.manager.table.replay.ReplayRecorder;
import game.db.ScoreRepository;
import utils.registry.enums.TableState;
import msg.registor.message.GMsg;
import proto.GameProto;

/**
 * 斗地主结算服务。
 * <p>
 * 职责覆盖：
 * <ul>
 *   <li><b>单局胜负与倍数判定：</b>地主 vs 农民阵营计分、春天（农民未出牌）与反春天（地主仅出首手牌）判定及翻倍结算；</li>
 *   <li><b>战绩与回放：</b>持久化单局分值并生成战绩审计日志与 Replay 录像切片；</li>
 *   <li><b>协议下发：</b>组装单局小结算协议 ({@code NotResult}) 与多局累计总结算协议 ({@code NotGameResult})；</li>
 *   <li><b>庄家轮换流转：</b>地主连胜享有下局优先叫牌权，农民获胜则由地主下家优先叫牌。</li>
 * </ul>
 */
public final class DdzSettleService {

    private static final Logger logger = LoggerFactory.getLogger(DdzSettleService.class);

    private DdzSettleService() {}

    /**
     * 结束斗地主单小局：计算阵营胜负得分、记录对局回放、下发小结算并触发桌子状态流转。
     *
     * @param table  当前斗地主桌子对象
     * @param winner 本局率先出完手牌的获胜玩家
     */
    static void finishGame(DdzTable table, TableUser winner) {
        DdzTableContext ctx = table.getDdz();
        int landlordSeat = ctx.getLandlordSeat();
        TableUser landlordUser = table.getSeatUser(landlordSeat);
        int landlordUserId = landlordUser != null ? landlordUser.getUserId() : 0;

        // 1. 阵营与胜负归属判定（0: 地主阵营赢，1: 农民阵营赢）
        boolean landlordWin = winner.getSeated() == landlordSeat;
        int winTeam = landlordWin ? 0 : 1;

        // 2. 春天与反春天特殊牌型判定：
        // 春天：地主获胜且所有农民在此局中从未成功出过一手牌（压牌从未成功）
        boolean spring = landlordWin && !ctx.isFarmerEverPlayed();
        // 反春天：农民获胜且地主整局仅在最开局出过一次牌（出牌手数<=1）
        boolean antiSpring = !landlordWin && ctx.getLandlordPlayCount() <= 1;

        // 3. 计算最终结算倍数：春天或反春天各自触发基础倍数翻倍
        int settleFactor = ctx.getCurrentMultiplier();
        if (spring) {
            settleFactor *= 2;
        }
        if (antiSpring) {
            settleFactor *= 2;
        }

        // 4. 根据阵营与座位数计算各玩家的得失积分
        int seatNum = table.getTableModel().getSeatNum();
        int[] scores = calcScores(seatNum, landlordSeat, landlordWin, settleFactor);

        String winType = spring ? "spring" : (antiSpring ? "antiSpring" : "normal");
        table.getGameResult().addRound(table.getCurrentRound(), winner.getSeated(), settleFactor, scores, winType);
        ScoreRepository.getInstance().saveRound(table);

        // 5. 持久化回放录像与下发小结算通知
        saveReplay(table, winner, settleFactor, winType, scores);
        sendResultMessage(table, winner, rPlayers(table), landlordUserId, winTeam, ctx, spring, antiSpring,
                settleFactor, seatNum, scores, winType);

        // 6. 下局首叫轮转规则：地主连庄优先叫牌；若农民获胜，则轮转至地主的逆时针下家优先叫牌
        int nextCall = landlordWin ? landlordSeat : (landlordSeat + 1) % seatNum;
        table.setNextFirstCallSeat(nextCall);

        // 7. 切换桌子状态至 TABLE_OVER，触发下一局准备倒计时
        table.upNextStateWithTime(TableState.TABLE_OVER, System.currentTimeMillis());
    }

    /**
     * 发送斗地主整场总结算账单通知（多局房卡打完后广播）。
     * <p>
     * 汇总所有局数的累计总分与历史局战绩列表。
     *
     * @param table 当前牌桌
     */
    public static void sendGameResult(Table table) {
        GameResult gameResult = table.getGameResult();
        int seatNum = table.getTableModel().getSeatNum();

        GameProto.NotGameResult.Builder builder = GameProto.NotGameResult.newBuilder()
                .setTotalRounds(gameResult.getTotalRounds())
                .setCompletedRounds(gameResult.getCompletedRounds());

        // 填充每个座位的整场累积总积分
        for (int i = 0; i < seatNum; i++) {
            builder.addTotalScores(GameProto.SeatScore.newBuilder()
                    .setSeat(i).setScore(gameResult.getTotalScore(i)).build());
        }

        // 填充每小局历史胜负记录快照
        for (GameResult.RoundEntry entry : gameResult.getRoundEntries()) {
            GameProto.RoundSummary.Builder summary = GameProto.RoundSummary.newBuilder()
                    .setRound(entry.getRound())
                    .setWinnerSeat(entry.getWinnerSeat())
                    .setFan(entry.getScore())
                    .setWinType(ByteString.copyFromUtf8(entry.getWinType()));
            for (int i = 0; i < seatNum; i++) {
                summary.addSeatScores(GameProto.SeatScore.newBuilder()
                        .setSeat(i).setScore(entry.getScores()[i]).build());
            }
            builder.addRounds(summary.build());
        }

        table.sendTableMessage(builder.build(), GMsg.NOT_GAME_RESULT);
    }

    // ======================== 内部私有方法 ========================

    /**
     * 计算斗地主各座位的得失积分（地主 1 对多不对等博弈模型）。
     *
     * @param seatNum      总座位数（标准斗地主为 3 人）
     * @param landlordSeat 地主座位号
     * @param landlordWin  是否地主获胜
     * @param settleFactor 最终生效的结算番数
     * @return 下标对应座位号的得分数组
     */
    private static int[] calcScores(int seatNum, int landlordSeat, boolean landlordWin, int settleFactor) {
        int[] scores = new int[seatNum];
        for (int i = 0; i < seatNum; i++) {
            if (i == landlordSeat) {
                // 地主胜独得所有农民失分之和；地主败则独输全场所有农民得分之和
                scores[i] = landlordWin ? settleFactor * (seatNum - 1) : -settleFactor * (seatNum - 1);
            } else {
                // 农民均摊收益或损失
                scores[i] = landlordWin ? -settleFactor : settleFactor;
            }
        }
        return scores;
    }

    /**
     * 写入并保存单局回放审计事件与结算帧。
     *
     * @param table        桌子对象
     * @param winner       赢家玩家
     * @param settleFactor 最终倍数
     * @param winType      胜利类型（normal/spring/antiSpring）
     * @param scores       各座位得分数组
     */
    private static void saveReplay(DdzTable table, TableUser winner, int settleFactor, String winType, int[] scores) {
        ReplayRecorder replay = table.getReplayRecorder();
        if (replay instanceof DdzReplayRecorder) {
            replay.writeAuditEvent("结算 赢家座" + winner.getSeated() + " 类型 " + winType
                    + " 最终倍数 " + settleFactor + " 各座得分 " + java.util.Arrays.toString(scores));
            replay.writeSettlement(winner.getSeated(), settleFactor, winType, scores);
            replay.save();
        }
    }

    /**
     * 构建结算时的玩家手牌亮出数据。
     * <p>
     * 剩余手牌按点数降序排列，保证客户端渲染展示时不乱序。
     *
     * @param table 当前牌桌
     * @return 封装好的 RPlayer 列表
     */
    private static List<GameProto.RPlayer> rPlayers(DdzTable table) {
        List<GameProto.RPlayer> rPlayers = new ArrayList<>();
        for (TableUser u : table.getSeatUsers().values()) {
            GameProto.RPlayer.Builder rp = GameProto.RPlayer.newBuilder().setRoleId(u.getUserId());
            List<Card> remain = new ArrayList<>(u.getCards());
            // 倒序排列余牌，利于前端按大牌到小牌有序展示
            remain.sort(java.util.Collections.reverseOrder());
            for (Card c : remain) {
                rp.addCards(GameProto.Card.newBuilder().setValue(c.getId()).build());
            }
            rPlayers.add(rp.build());
        }
        return rPlayers;
    }

    /**
     * 发送小结算消息（NotResult 与可选的 NotRoundResult）。
     * <p>
     * 包含对自定义扩展字段序列化的异常容错回退机制。
     *
     * @param table          牌桌对象
     * @param winner         赢家玩家
     * @param rPlayers       亮出的各家余牌
     * @param landlordUserId 地主用户 ID
     * @param winTeam        获胜阵营（0:地主，1:农民）
     * @param ctx            斗地主桌子上下文
     * @param spring         是否春天
     * @param antiSpring     是否反春天
     * @param settleFactor   最终结算番数
     * @param seatNum        座位总数
     * @param scores         各座分值
     * @param winType        胜利类型字符串
     */
    private static void sendResultMessage(DdzTable table, TableUser winner, List<GameProto.RPlayer> rPlayers,
            int landlordUserId, int winTeam, DdzTableContext ctx, boolean spring, boolean antiSpring,
            int settleFactor, int seatNum, int[] scores, String winType) {
        try {
            // 优先使用扩展编码器发送包含叫抢底倍数、春天反春天的扩展结算数据
            byte[] payload = DdzResultEncoder.encodeNotResultExtended(
                    winner.getUserId(), rPlayers, landlordUserId, winTeam,
                    ctx.getBaseScore(), ctx.getRobMultiplier(), spring, antiSpring, settleFactor);
            table.sendTableMessageRaw(GMsg.NOT_RESULT, payload);
        } catch (IOException e) {
            // 序列化异常防御：降级发送标准 Protobuf 基础包，防止客户端卡死在游戏结算界面
            logger.error("encode NotResult failed table:{}", table.getTableId(), e);
            GameProto.NotResult.Builder b = GameProto.NotResult.newBuilder().setWinner(winner.getUserId());
            for (GameProto.RPlayer rp : rPlayers) {
                b.addRPlayers(rp);
            }
            table.sendTableMessage(b.build(), GMsg.NOT_RESULT);
        }

        // 多局房卡模式下广播当局局数小结与整场累计总分
        if (table.isMultiRound()) {
            GameProto.NotRoundResult.Builder roundResult = GameProto.NotRoundResult.newBuilder()
                    .setRound(table.getCurrentRound())
                    .setWinnerSeat(winner.getSeated())
                    .setFan(settleFactor)
                    .setWinType(ByteString.copyFromUtf8(winType));
            for (int i = 0; i < seatNum; i++) {
                roundResult.addSeatScores(GameProto.SeatScore.newBuilder()
                        .setSeat(i).setScore(scores[i]).build());
                roundResult.addTotalScores(GameProto.SeatScore.newBuilder().setSeat(i)
                        .setScore(table.getGameResult().getTotalScore(i)).build());
            }
            table.sendTableMessage(roundResult.build(), GMsg.NOT_ROUND_RESULT);
        }
    }
}

