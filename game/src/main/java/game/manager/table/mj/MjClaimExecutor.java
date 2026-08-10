package game.manager.table.mj;

import game.manager.table.TableUser;
import game.manager.table.cards.Card;
import game.manager.table.replay.MjReplayRecorder;
import msg.registor.enums.TableState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 麻将 claim 执行：碰/杠/胡/吃/过与超时（含截胡推进与一炮多响汇总）。
 */
public final class MjClaimExecutor {
    private static final Logger logger = LoggerFactory.getLogger(MjClaimExecutor.class);

    private MjClaimExecutor() {
    }

    /**
     * 处理玩家的claim响应(碰/杠/胡/吃/过)
     */
    public static boolean applyClaim(MjTable table, int userId, GameProto.OpInfo op) {
        MjTableContext ctx = table.getMjContext();
        int seat = findSeat(table, userId);
        if (seat < 0 || !ctx.getPendingClaimSeats().contains(seat)) return false;

        ConstProto.Operation choice = op.getChoice();
        if (!isChoiceAllowed(ctx, seat, choice)) {
            logger.warn("claim阶段不允许该操作, table: {}, seat: {}, choice: {}, huPhase: {}",
                    table.getTableId(), seat, choice, ctx.isClaimHuPhase());
            return false;
        }

        int tileId = ctx.getClaimTileId();
        int fromSeat = ctx.getClaimFromSeat();
        MjReplayRecorder replay = (MjReplayRecorder) table.getReplayRecorder();
        if (replay != null) {
            TableUser actor = table.getSeatUser(seat);
            replay.recordChoice(seat, choiceName(choice), actor != null && actor.isRobot() ? "机器人" : "玩家");
        }

        switch (choice) {
            case MJ_HU:
                return processHuChoice(table, seat, tileId, fromSeat);
            case MJ_GANG:
                return processClaimGang(table, seat, tileId, fromSeat);
            case MJ_PENG:
                return processPeng(table, seat, tileId, fromSeat);
            case MJ_CHI:
                return processChi(table, seat, tileId, fromSeat, op);
            case MJ_PASS:
                return processPass(table, seat);
            default:
                logger.warn("无效的claim操作, table: {}, userId: {}, choice: {}", table.getTableId(), userId, choice);
                return false;
        }
    }

    private static String choiceName(ConstProto.Operation choice) {
        switch (choice) {
            case MJ_HU: return "胡";
            case MJ_GANG: return "杠";
            case MJ_PENG: return "碰";
            case MJ_CHI: return "吃";
            case MJ_PASS: return "过";
            default: return choice.name();
        }
    }

    private static int findSeat(MjTable table, int userId) {
        for (Map.Entry<Integer, TableUser> entry : table.getSeatUsers().entrySet()) {
            if (entry.getValue().getUserId() == userId) return entry.getKey();
        }
        return -1;
    }

    /**
     * 胡阶段只许胡/过；副露阶段不许胡
     */
    private static boolean isChoiceAllowed(MjTableContext ctx, int seat, ConstProto.Operation choice) {
        MjClaimInfo info = ctx.getClaimInfoBySeat().get(seat);
        if (info == null) return false;
        if (ctx.isClaimHuPhase()) {
            return choice == ConstProto.Operation.MJ_HU || choice == ConstProto.Operation.MJ_PASS;
        }
        if (choice == ConstProto.Operation.MJ_HU) return false;
        if (choice == ConstProto.Operation.MJ_PASS) return true;
        if (choice == ConstProto.Operation.MJ_GANG) return info.isCanGang();
        if (choice == ConstProto.Operation.MJ_PENG) return info.isCanPeng();
        if (choice == ConstProto.Operation.MJ_CHI) return info.isCanChi();
        return false;
    }

    /**
     * 清理claim状态并取消所有待响应的座位（包内可见，供MjDrawService调用）
     */
    public static void clearClaimState(MjTable table) {
        table.getMjContext().clearClaimRuntime();
    }

    /**
     * claim超时: 所有待响应座位自动pass
     */
    public static void timeoutClaim(MjTable table) {
        MjTableContext ctx = table.getMjContext();
        List<Integer> pending = new ArrayList<>(ctx.getPendingClaimSeats());
        MjReplayRecorder replay = (MjReplayRecorder) table.getReplayRecorder();
        for (int seat : pending) {
            if (replay != null) replay.recordAutoPass(seat);
            processPass(table, seat);
        }
    }

    private static boolean processHuChoice(MjTable table, int seat, int tileId, int fromSeat) {
        MjTableContext ctx = table.getMjContext();
        if (ctx.isMultiHuMode() && ctx.isClaimHuPhase()) {
            ctx.getMultiHuAccepted().add(seat);
            ctx.removeClaimSeat(seat);
            if (ctx.hasPendingClaims()) return true;
            return settleMultiHu(table, tileId, fromSeat);
        }
        return processHu(table, seat, tileId, fromSeat, false);
    }

    /**
     * 一炮多响：等全部可胡者决策后，有人胡则结算结束
     */
    private static boolean settleMultiHu(MjTable table, int tileId, int fromSeat) {
        MjTableContext ctx = table.getMjContext();
        List<Integer> winners = new ArrayList<>(ctx.getMultiHuAccepted());
        if (winners.isEmpty()) {
            MjClaimDetector.startSidePhase(table);
            return true;
        }
        ctx.claimLastDiscard(fromSeat, tileId);
        List<MjWinResult> results = new ArrayList<>();
        for (int seat : winners) {
            TableUser user = table.getSeatUser(seat);
            if (user == null) continue;
            user.addCards(new Card(tileId));
            results.add(buildDianPaoResult(table, seat, tileId, fromSeat, false));
            MjReplayRecorder replay = (MjReplayRecorder) table.getReplayRecorder();
            if (replay != null) replay.recordHu(seat, tileId, false);
        }
        clearClaimState(table);
        MjSettleService.broadcastMjAction(table, winners.get(0), tileId, ConstProto.Operation.MJ_HU);
        MjSettleService.finishGameWithMultiWin(table, results);
        return true;
    }

    /**
     * 处理胡牌（包内可见，供MjGangService调用）
     */
    public static boolean processHu(MjTable table, int seat, int tileId, int fromSeat, boolean qiangGang) {
        TableUser user = table.getSeatUser(seat);
        if (user == null) return false;

        if (!qiangGang) {
            user.addCards(new Card(tileId));
            table.getMjContext().claimLastDiscard(fromSeat, tileId);
        }
        MjWinResult winResult = buildDianPaoResult(table, seat, tileId, fromSeat, qiangGang);

        clearClaimState(table);
        MjReplayRecorder replay = (MjReplayRecorder) table.getReplayRecorder();
        if (replay != null) replay.recordHu(seat, tileId, false);

        MjSettleService.broadcastMjAction(table, seat, tileId, ConstProto.Operation.MJ_HU);
        MjSettleService.finishGameWithWin(table, winResult);
        return true;
    }

    private static MjWinResult buildDianPaoResult(MjTable table, int seat, int tileId, int fromSeat, boolean qiangGang) {
        TableUser user = table.getSeatUser(seat);
        MjTableContext ctx = table.getMjContext();
        MjWinResult winResult = new MjWinResult();
        winResult.setWinnerId(seat);
        winResult.setWinTile(tileId);
        winResult.setZiMo(false);
        winResult.setDianPao(true);
        winResult.setDianPaoSeat(fromSeat);
        winResult.setHandTiles(new ArrayList<>(user.getCards()));
        winResult.setExposedSets(new ArrayList<>(ctx.getExposedSets(seat)));
        winResult.setGangShangKaiHua(ctx.isGangShangKaiHua());
        winResult.setQiangGangHu(qiangGang);
        winResult.setHaiDi(ctx.isHaiDi());
        return winResult;
    }

    /**
     * 处理碰
     */
    private static boolean processPeng(MjTable table, int seat, int tileId, int fromSeat) {
        TableUser user = table.getSeatUser(seat);
        if (user == null) return false;

        int removed = removeCardsById(user, tileId, 2);
        if (removed < 2) {
            logger.error("碰牌时手牌不足, table: {}, seat: {}, tile: {}", table.getTableId(), seat, tileId);
            return false;
        }

        MjTableContext ctx = table.getMjContext();
        ctx.claimLastDiscard(fromSeat, tileId);
        ctx.addExposedSet(seat, new MjExposedSet(MjExposedSet.Type.PENG,
                Arrays.asList(tileId, tileId, tileId), fromSeat));
        clearClaimState(table);
        table.getOp().setCurrOpSeat(seat);
        ctx.resetTurn();
        ctx.setDiscardAfterClaim(true);

        MjSettleService.broadcastMjAction(table, seat, tileId, ConstProto.Operation.MJ_PENG);
        MjSettleService.syncExposedSets(table);
        table.getMjTilePool().sendHandNotice(table.getSeatUsers());

        MjReplayRecorder replay = (MjReplayRecorder) table.getReplayRecorder();
        if (replay != null) replay.recordPeng(seat, fromSeat, tileId);

        table.upNextState(TableState.MJ_DISCARD);
        logger.info("麻将碰, table: {}, seat: {}, tile: {}", table.getTableId(), seat, tileId);
        return true;
    }

    /**
     * 处理claim明杠
     */
    private static boolean processClaimGang(MjTable table, int seat, int tileId, int fromSeat) {
        TableUser user = table.getSeatUser(seat);
        if (user == null) return false;

        int removed = removeCardsById(user, tileId, 3);
        if (removed < 3) {
            logger.error("杠牌时手牌不足, table: {}, seat: {}, tile: {}", table.getTableId(), seat, tileId);
            return false;
        }

        MjTableContext ctx = table.getMjContext();
        ctx.claimLastDiscard(fromSeat, tileId);
        ctx.addExposedSet(seat, new MjExposedSet(MjExposedSet.Type.MING_GANG,
                Arrays.asList(tileId, tileId, tileId, tileId), fromSeat));
        clearClaimState(table);
        table.getOp().setCurrOpSeat(seat);
        ctx.resetTurn();
        ctx.setDiscardAfterClaim(true);
        ctx.setGangShangKaiHua(true);

        MjSettleService.broadcastMjAction(table, seat, tileId, ConstProto.Operation.MJ_GANG);
        MjSettleService.syncExposedSets(table);

        MjReplayRecorder replay = (MjReplayRecorder) table.getReplayRecorder();
        if (replay != null) replay.recordMingGang(seat, fromSeat, tileId);

        MjGangService.settleGangScore(table, seat, MjExposedSet.Type.MING_GANG);

        int drawnTile = MjDrawService.drawTile(table);
        if (drawnTile >= 0) {
            List<Card> handTiles = user.getCards();
            MjWinChecker winChecker = MjPlayService.createWinChecker(table);
            if (winChecker.canWin(handTiles, ctx.getExposedSets(seat), drawnTile)) {
                MjDrawService.processZiMo(table, seat, drawnTile);
                return true;
            }
            ctx.setGangShangKaiHua(false);
            table.upNextState(TableState.MJ_DISCARD);
        } else {
            MjSettleService.finishGame(table, "杠后牌墙已空");
        }

        logger.info("麻将明杠, table: {}, seat: {}, tile: {}", table.getTableId(), seat, tileId);
        return true;
    }

    /**
     * 处理claim吃
     */
    private static boolean processChi(MjTable table, int seat, int tileId, int fromSeat, GameProto.OpInfo op) {
        TableUser user = table.getSeatUser(seat);
        if (user == null) return false;
        if (op.getOpCardsCount() == 0) return false;

        List<Integer> chiTileIds = new ArrayList<>();
        for (GameProto.Card c : op.getOpCards(0).getCardsList()) {
            chiTileIds.add(c.getValue());
        }
        chiTileIds.add(tileId);

        for (int chiTile : chiTileIds) {
            if (chiTile == tileId) continue;
            boolean removed = user.removeCardsByProtoIds(Collections.singletonList(chiTile));
            if (!removed) {
                logger.error("吃牌时手牌不足, table: {}, seat: {}, tile: {}", table.getTableId(), seat, chiTile);
                return false;
            }
        }
        logger.info("麻将吃牌移除完成, table: {}, userId: {}, seat: {}, tiles: {}, handAfterRemove: {}",
                table.getTableId(), user.getUserId(), seat, chiTileIds, user.getCards().size());

        MjTableContext ctx = table.getMjContext();
        ctx.claimLastDiscard(fromSeat, tileId);
        ctx.addExposedSet(seat, new MjExposedSet(MjExposedSet.Type.CHI, chiTileIds, fromSeat));
        clearClaimState(table);
        table.getOp().setCurrOpSeat(seat);
        ctx.resetTurn();
        ctx.setDiscardAfterClaim(true);

        MjSettleService.broadcastMjAction(table, seat, tileId, ConstProto.Operation.MJ_CHI);
        MjSettleService.syncExposedSets(table);
        table.getMjTilePool().sendHandNotice(table.getSeatUsers());

        MjReplayRecorder replay = (MjReplayRecorder) table.getReplayRecorder();
        if (replay != null) {
            replay.recordChi(seat, fromSeat, chiTileIds, user.getCards().stream()
                    .mapToInt(Card::getId).boxed().collect(Collectors.toList()));
        }

        table.upNextState(TableState.MJ_DISCARD);
        logger.info("麻将吃, table: {}, seat: {}, tiles: {}", table.getTableId(), seat, chiTileIds);
        return true;
    }

    /**
     * 处理pass：截胡推进下家胡 / 一炮多响等齐 / 副露推进
     */
    private static boolean processPass(MjTable table, int seat) {
        MjTableContext ctx = table.getMjContext();
        if (!ctx.getPendingClaimSeats().contains(seat)) return true;
        ctx.removeClaimSeat(seat);

        if (ctx.isClaimHuPhase()) {
            if (ctx.isMultiHuMode()) {
                if (ctx.hasPendingClaims()) return true;
                if (!ctx.getMultiHuAccepted().isEmpty()) {
                    return settleMultiHu(table, ctx.getClaimTileId(), ctx.getClaimFromSeat());
                }
                MjClaimDetector.startSidePhase(table);
                return true;
            }
            MjClaimDetector.advanceHuQueue(table);
            return true;
        }

        MjClaimDetector.advanceSideQueue(table, seat);
        return true;
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
