package com.cloud.weball.game.domain.mj;

import com.cloud.weball.game.domain.table.TableUser;
import com.cloud.weball.game.domain.cards.Card;
import com.cloud.weball.game.domain.replay.MjReplayRecorder;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 麻将 claim 检测与选项下发（含截胡逐家 / 一炮多响并行）。
 */
public final class MjClaimDetector {
    private static final Logger logger = LoggerFactory.getLogger(MjClaimDetector.class);

    private MjClaimDetector() {
    }

    public static MjClaimInfo buildClaimInfo(MjTable table, int seat) {
        MjTableContext ctx = table.getMjContext();
        MjClaimInfo stored = ctx.getClaimInfoBySeat().get(seat);
        if (stored != null) {
            if (ctx.isClaimHuPhase()) return MjClaimPlanner.huOnly(stored);
            return MjClaimPlanner.sideOnly(stored);
        }
        int tileId = ctx.getClaimTileId();
        if (tileId == 0) return null;
        TableUser user = table.getSeatUser(seat);
        if (user == null) return null;
        return detectSeatClaim(table, seat, user.getCards(), ctx.getExposedSets(seat), tileId, ctx.getClaimFromSeat());
    }

    static MjClaimInfo detectSeatClaim(MjTable table, int seat, List<Card> handTiles,
                                       List<MjExposedSet> exposedSets, int tileId, int fromSeat) {
        boolean allowHu = table.getTableModel().getAllowHu() != 0
                && table.getTableModel().getAllowDianPao() != 0;
        boolean allowPeng = table.getTableModel().getAllowPeng() != 0;
        boolean allowGangMing = table.getTableModel().getAllowGangMing() != 0;
        boolean allowChi = table.getTableModel().getAllowChi() == 1;
        MjWinChecker winChecker = MjPlayService.createWinChecker(table);

        boolean canHu = false, canGang = false, canPeng = false, canChi = false;
        int gangTileId = 0;
        List<int[]> chiCombos = new ArrayList<>();

        if (allowHu) {
            List<Card> testHand = new ArrayList<>(handTiles);
            testHand.add(new Card(tileId));
            if (winChecker.canWin(testHand, exposedSets, tileId)) canHu = true;
        }
        if (allowPeng && winChecker.canPeng(handTiles, tileId)) canPeng = true;
        if (allowGangMing && winChecker.canMingGang(handTiles, tileId)) {
            canGang = true;
            gangTileId = tileId;
        }
        if (allowChi) {
            int seatNum = table.getTableModel().getSeatNum();
            if (isNextSeat(fromSeat, seat, seatNum)) {
                chiCombos = winChecker.getChiCombos(handTiles, tileId);
                canChi = !chiCombos.isEmpty();
            }
        }

        if (!canHu && !canGang && !canPeng && !canChi) return null;
        return new MjClaimInfo(seat, canHu, canGang, canPeng, canChi, tileId, gangTileId, chiCombos);
    }

    /**
     * 吃牌只属于出牌者沿服务器轮转方向的下一座位。
     * 与 {@code Table.nextSeat} 保持相同的递增并回绕规则。
     */
    static boolean isNextSeat(int fromSeat, int claimSeat, int seatNum) {
        return seatNum > 1 && fromSeat >= 0 && fromSeat < seatNum
                && claimSeat == (fromSeat + 1) % seatNum;
    }

    public static boolean checkClaim(MjTable table) {
        MjTableContext ctx = table.getMjContext();
        int tileId = ctx.getLastDiscardTile();
        int fromSeat = ctx.getLastDiscardSeat();
        if (tileId == 0) return false;

        int seatNum = table.getTableModel().getSeatNum();
        Map<Integer, MjClaimInfo> bySeat = new LinkedHashMap<>();
        List<MjClaimInfo> ordered = new ArrayList<>();

        for (int i = 1; i < seatNum; i++) {
            int checkSeat = (fromSeat + i) % seatNum;
            TableUser user = table.getSeatUser(checkSeat);
            if (user == null) continue;
            MjClaimInfo claim = detectSeatClaim(table, checkSeat, user.getCards(),
                    ctx.getExposedSets(checkSeat), tileId, fromSeat);
            if (claim != null) {
                bySeat.put(checkSeat, claim);
                ordered.add(claim);
            }
        }
        if (bySeat.isEmpty()) return false;

        boolean multiHu = table.getTableModel().getAllowMultiHu() != 0;
        ctx.beginClaimRound(bySeat, multiHu);
        ctx.setClaimInfo(tileId, fromSeat, new ArrayList<>());
        table.getOp().clearChoiceMap();

        List<Integer> huSeats = MjClaimPlanner.huSeatsInOrder(ordered);
        if (!huSeats.isEmpty()) {
            startHuPhase(table, huSeats, multiHu);
        } else {
            startSidePhase(table);
        }

        logger.info("麻将claim检测, table: {}, tile: {}, huSeats: {}, multiHu: {}, claims: {}",
                table.getTableId(), tileId, huSeats, multiHu, bySeat.keySet());
        return true;
    }

    /**
     * 胡阶段：截胡逐家推送；一炮多响并行推送全部可胡者
     */
    static void startHuPhase(MjTable table, List<Integer> huSeats, boolean multiHu) {
        MjTableContext ctx = table.getMjContext();
        ctx.setClaimHuPhase(true);
        ctx.getHuQueue().clear();
        ctx.getHuQueue().addAll(huSeats);
        ctx.getMultiHuAccepted().clear();

        if (multiHu) {
            promptSeats(table, huSeats, true);
        } else {
            int first = huSeats.get(0);
            promptSeats(table, java.util.Collections.singletonList(first), true);
        }
    }

    /**
     * 全部可胡者过牌后，按 杠&gt;碰&gt;吃 与逆时针距离逐家询问
     */
    static void startSidePhase(MjTable table) {
        MjTableContext ctx = table.getMjContext();
        ctx.setClaimHuPhase(false);
        ctx.getHuQueue().clear();
        int fromSeat = ctx.getClaimFromSeat();
        int seatNum = table.getTableModel().getSeatNum();
        List<MjClaimInfo> sides = MjClaimPlanner.sideCandidates(ctx.getClaimInfoBySeat(), fromSeat, seatNum);
        if (sides.isEmpty()) {
            MjClaimExecutor.clearClaimState(table);
            // claim 期间 currOpSeat 会被切换到待响应座位，结束后必须从
            // 最后出牌者继续轮转，不能从最后一个过牌者继续轮转。
            table.getOp().setCurrOpSeat(fromSeat);
            MjPlayService.nextPlayer(table);
            table.upNextState(TableState.MJ_PLAY);
            return;
        }
        int seat = sides.get(0).getSeat();
        promptSeats(table, java.util.Collections.singletonList(seat), false);
    }

    /**
     * 截胡：当前过胡后推进下一位可胡者
     */
    static void advanceHuQueue(MjTable table) {
        MjTableContext ctx = table.getMjContext();
        if (ctx.getHuQueue().isEmpty()) {
            startSidePhase(table);
            return;
        }
        ctx.getHuQueue().remove(0);
        if (ctx.getHuQueue().isEmpty()) {
            startSidePhase(table);
            return;
        }
        int next = ctx.getHuQueue().get(0);
        promptSeats(table, java.util.Collections.singletonList(next), true);
    }

    /**
     * 副露：当前过牌后推进下一位候选
     */
    static void advanceSideQueue(MjTable table, int passedSeat) {
        MjTableContext ctx = table.getMjContext();
        ctx.getClaimInfoBySeat().remove(passedSeat);
        startSidePhase(table);
    }

    private static void promptSeats(MjTable table, List<Integer> seats, boolean huPhase) {
        MjTableContext ctx = table.getMjContext();
        table.getOp().clearChoiceMap();
        ctx.setClaimInfo(ctx.getClaimTileId(), ctx.getClaimFromSeat(), new ArrayList<>(seats));
        int opSeat = seats.get(0);
        table.getOp().setCurrOpSeat(opSeat);

        for (int seat : seats) {
            MjClaimInfo raw = ctx.getClaimInfoBySeat().get(seat);
            MjClaimInfo view = huPhase ? MjClaimPlanner.huOnly(raw) : MjClaimPlanner.sideOnly(raw);
            if (view == null) continue;
            sendClaimOptions(table, view);
        }
        // 切换操作位时重置 15 秒等待
        table.upNextState(TableState.MJ_CLAIM);
    }

    private static void sendClaimOptions(MjTable table, MjClaimInfo claim) {
        GameProto.NotMjState.Builder notBuilder = GameProto.NotMjState.newBuilder()
                .setOpSeat(claim.getSeat())
                .setTileId(table.getMjContext().getClaimTileId())
                .setAction(ConstProto.Operation.DISCARD)
                .setWait(TableState.MJ_CLAIM.getOverTime())
                .setWallLeft(table.getMjTilePool().remaining());

        if (claim.isCanHu()) {
            notBuilder.addChoice(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.MJ_HU).build());
        }
        if (claim.isCanGang()) {
            notBuilder.addChoice(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.MJ_GANG).build());
        }
        if (claim.isCanPeng()) {
            notBuilder.addChoice(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.MJ_PENG).build());
        }
        if (claim.isCanChi()) {
            for (int[] combo : claim.getChiCombos()) {
                GameProto.OpInfo.Builder chiBuilder = GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.MJ_CHI);
                GameProto.CardInfo.Builder cardInfoBuilder = GameProto.CardInfo.newBuilder();
                for (int chiTile : combo) {
                    cardInfoBuilder.addCards(GameProto.Card.newBuilder().setValue(chiTile).build());
                }
                chiBuilder.addOpCards(cardInfoBuilder.build());
                notBuilder.addChoice(chiBuilder.build());
            }
        }
        notBuilder.addChoice(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.MJ_PASS).build());

        if (table.getReplayRecorder() instanceof MjReplayRecorder) {
            List<String> options = new ArrayList<>();
            for (GameProto.OpInfo choice : notBuilder.getChoiceList()) {
                options.add(choiceName(choice.getChoice()));
            }
            ((MjReplayRecorder) table.getReplayRecorder()).recordOptions(claim.getSeat(), options);
        }

        for (GameProto.OpInfo choice : notBuilder.getChoiceList()) {
            table.getOp().addPosOpInfo(claim.getSeat(), choice);
        }

        // 当前响应座位对全桌可见，网页端据此更新操作指示；只有目标座位会显示操作按钮。
        table.sendTableMessage(notBuilder.build(), GMsg.MJ_TILE_NOT);
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
}
