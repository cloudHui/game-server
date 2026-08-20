package com.cloud.game.manager.table.mj;

import com.cloud.game.manager.table.TableUser;
import com.cloud.game.manager.table.cards.Card;
import com.cloud.game.manager.table.replay.MjReplayRecorder;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.GameProto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * 麻将杠牌服务
 * 处理暗杠、补杠、杠分即时结算
 */
public class MjGangService {

    private static final Logger logger = LoggerFactory.getLogger(MjGangService.class);

    private MjGangService() {
    }

    // ======================== 暗杠 ========================

    /**
     * 玩家主动暗杠(出牌阶段选择)，返回true表示执行了暗杠
     */
    public static boolean applyAnGang(MjTable table, int userId, int gangTileId) {
        int seat = table.getOp().getCurrOpSeat();
        TableUser user = table.getSeatUser(seat);
        if (user == null || user.getUserId() != userId) return false;

        MjWinChecker winChecker = MjPlayService.createWinChecker(table);
        List<Integer> gangTiles = winChecker.getAnGangTiles(user.getCards());
        if (!gangTiles.contains(gangTileId)) return false;

        // 从手牌移除4张
        int removed = removeCardsById(user, gangTileId, 4);

        MjTableContext ctx = table.getMjContext();
        ctx.addExposedSet(seat, new MjExposedSet(MjExposedSet.Type.AN_GANG,
                Arrays.asList(gangTileId, gangTileId, gangTileId, gangTileId), -1));

        MjSettleService.broadcastMjAction(table, seat, gangTileId, proto.ConstProto.Operation.MJ_GANG);
        MjSettleService.syncExposedSets(table);
        table.getMjTilePool().sendHandNotice(table.getSeatUsers());
        settleGangScore(table, seat, MjExposedSet.Type.AN_GANG);

        ctx.setGangShangKaiHua(true);
        int drawnTile = MjDrawService.drawTile(table);

        MjReplayRecorder replay = (MjReplayRecorder) table.getReplayRecorder();
        if (replay != null) replay.recordAnGang(seat, gangTileId, drawnTile >= 0 ? drawnTile : -1);

        if (drawnTile >= 0) {
            if (MjDrawService.checkZiMo(table, drawnTile)) return true;
            ctx.setGangShangKaiHua(false);
            // 杠发生在 MJ_DISCARD 内，状态虽然重新进入 MJ_DISCARD，但不是一次
            // 不同枚举值的切换。必须重置提示标记，否则补摸后不会再次发送出牌按钮。
            ctx.setDiscardPromptSent(false);
            table.upNextState(msg.registor.enums.TableState.MJ_DISCARD);
        } else {
            MjSettleService.finishGame(table, "暗杠后牌墙已空");
        }

        logger.info("麻将暗杠, table: {}, seat: {}, tile: {}", table.getTableId(), seat, gangTileId);
        return true;
    }

    // ======================== 补杠 ========================

    /**
     * 玩家主动补杠(出牌阶段选择)，返回true表示执行了补杠(或被抢杠胡)
     */
    public static boolean applyBuGang(MjTable table, int userId, int tileId) {
        int seat = table.getOp().getCurrOpSeat();
        TableUser user = table.getSeatUser(seat);
        if (user == null || user.getUserId() != userId) return false;

        MjTableContext ctx = table.getMjContext();
        MjWinChecker winChecker = MjPlayService.createWinChecker(table);

        if (!winChecker.canBuGang(user.getCards(), ctx.getExposedSets(seat), tileId)) return false;

        // 检查抢杠胡
        int seatNum = table.getTableModel().getSeatNum();
        for (int i = 1; i < seatNum; i++) {
            int checkSeat = (seat + i) % seatNum;
            TableUser other = table.getSeatUser(checkSeat);
            if (other == null) continue;

            List<Card> testHand = new ArrayList<>(other.getCards());
            testHand.add(new Card(tileId));
            if (winChecker.canWin(testHand, ctx.getExposedSets(checkSeat), tileId)) {
                ctx.setQiangGangHu(true);
                user.removeCardsByProtoIds(Collections.singletonList(tileId));
                MjReplayRecorder replay = (MjReplayRecorder) table.getReplayRecorder();
                if (replay != null) replay.recordBuGangRobbed(seat, tileId, checkSeat);
                MjClaimService.clearClaimState(table);
                MjClaimService.processHu(table, checkSeat, tileId, seat, true);
                return true;
            }
        }

        // 没人抢杠, 执行补杠
        user.removeCardsByProtoIds(Collections.singletonList(tileId));

        // 更新副露: 找到碰, 改为补杠
        List<MjExposedSet> sets = ctx.getExposedSets(seat);
        for (int i = 0; i < sets.size(); i++) {
            MjExposedSet set = sets.get(i);
            if (set.getType() == MjExposedSet.Type.PENG && set.getTileIds().get(0) == tileId) {
                sets.set(i, new MjExposedSet(MjExposedSet.Type.BU_GANG,
                        Arrays.asList(tileId, tileId, tileId, tileId), set.getFromSeat()));
                break;
            }
        }

        MjSettleService.broadcastMjAction(table, seat, tileId, proto.ConstProto.Operation.MJ_GANG);
        MjSettleService.syncExposedSets(table);
        table.getMjTilePool().sendHandNotice(table.getSeatUsers());
        settleGangScore(table, seat, MjExposedSet.Type.BU_GANG);

        MjReplayRecorder replay = (MjReplayRecorder) table.getReplayRecorder();
        if (replay != null) replay.recordBuGang(seat, tileId, 0);

        ctx.setGangShangKaiHua(true);
        int drawnTile = MjDrawService.drawTile(table);
        if (drawnTile >= 0) {
            if (MjDrawService.checkZiMo(table, drawnTile)) return true;
            ctx.setGangShangKaiHua(false);
            // 同暗杠：补杠前的出牌提示已经发送过，补摸后要开启一个新的出牌窗口。
            ctx.setDiscardPromptSent(false);
            table.upNextState(msg.registor.enums.TableState.MJ_DISCARD);
        } else {
            MjSettleService.finishGame(table, "补杠后牌墙已空");
        }

        logger.info("麻将补杠, table: {}, seat: {}, tile: {}", table.getTableId(), seat, tileId);
        return true;
    }

    // ======================== 杠分结算 ========================

    /**
     * 杠分即时结算：明杠/补杠每家出1分，暗杠每家出2分
     */
    static void settleGangScore(MjTable table, int seat, MjExposedSet.Type gangType) {
        int gangScore = table.getTableModel().getGangScore();
        if (gangScore <= 0) gangScore = 1;

        int multiplier = (gangType == MjExposedSet.Type.AN_GANG) ? 2 : 1;
        int scorePerPlayer = gangScore * multiplier;
        int seatNum = table.getTableModel().getSeatNum();

        int[] scores = new int[seatNum];
        for (int i = 0; i < seatNum; i++) {
            scores[i] = (i == seat) ? scorePerPlayer * (seatNum - 1) : -scorePerPlayer;
        }

        GameProto.NotResult.Builder resultBuilder = GameProto.NotResult.newBuilder()
                .setWinner(seat).setSettleFactor(scorePerPlayer);
        for (int i = 0; i < seatNum; i++) {
            TableUser u = table.getSeatUser(i);
            if (u != null) {
                resultBuilder.addRPlayers(GameProto.RPlayer.newBuilder().setRoleId(u.getUserId()).build());
            }
        }
        table.sendTableMessage(resultBuilder.build(), GMsg.NOT_RESULT);

        logger.info("麻将杠分结算, table: {}, seat: {}, type: {}, score: {}",
                table.getTableId(), seat, gangType, scorePerPlayer);
    }

    /**
     * 从手牌中按ID移除指定张数，返回实际移除数
     */
    private static int removeCardsById(TableUser user, int tileId, int count) {
        int removed = 0;
        Iterator<Card> it = user.getCards().iterator();
        while (it.hasNext() && removed < count) {
            if (it.next().getId() == tileId) {
                it.remove();
                removed++;
            }
        }
        return removed;
    }
}
