package com.cloud.weball.game.domain.mj;

import com.cloud.weball.game.domain.table.GameResult;
import com.cloud.weball.game.domain.table.Table;
import com.cloud.weball.game.domain.table.TableUser;
import com.cloud.weball.game.domain.mj.card.MjConst;
import com.cloud.weball.game.domain.mj.card.MjTilePool;
import com.cloud.weball.game.domain.replay.MjReplayRecorder;
import com.cloud.weball.game.domain.replay.ReplayRecorder;
import model.tablemodel.TableModel;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import net.client.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;
import proto.ModelProto;

import java.util.List;

/**
 * 麻将桌子
 * 包含麻将特有的牌墙、上下文、赖子、副露等
 */
public class MjTable extends Table {
    private static final Logger logger = LoggerFactory.getLogger(MjTable.class);

    private final MjTilePool mjTilePool;
    private final MjTableContext mjContext = new MjTableContext();

    public MjTable(long tableId, TableModel model, ModelProto.RoomRole creator) {
        super(tableId, model, creator);
        this.mjTilePool = new MjTilePool(this);
    }

    // ======================== 抽象方法实现 ========================

    @Override
    public int getGameType() {
        return 1;
    }

    @Override
    public void dealCards() {
        mjTilePool.dealInitTiles();
    }

    @Override
    public void resetGameContext() {
        mjContext.resetRound();
    }

    @Override
    public GameResult createGameResult() {
        MjGameResult result = new MjGameResult();
        result.setTotalRounds(getTableModel().getTotalRounds());
        return result;
    }

    @Override
    public ReplayRecorder createReplayRecorder() {
        return new MjReplayRecorder(getTableId(), getCurrentRound());
    }

    @Override
    public void initGameConfig() {
        int subType = getTableModel().getGameSubType();
        if (subType == 2) {
            // 卡五星（随州/襄阳扩展暂不做）：仍仅万、条
            mjTilePool.setAllowedSuits(new int[]{MjConst.SUIT_WAN, MjConst.SUIT_TIAO});
            return;
        }
        // 二人/三人删牌由 MjWallComposer 按 seatNum 编排，此处不强制花色
        mjTilePool.setAllowedSuits(null);
    }

    @Override
    public int processOp(int userId, GameProto.OpInfo op, Sender sender, long mapId, int sequence) {
        TableState ts = getTableState();

        if (ts == TableState.MJ_DISCARD) {
            return processMjDiscard(userId, op);
        }
        if (ts == TableState.MJ_CLAIM) {
            return processMjClaim(userId, op);
        }

        return ConstProto.Result.OP_CURR_ERROR_VALUE;
    }

    @Override
    public void syncGameState(TableUser user) {
        int seat = user.getSeated();
        if (seat < 0) return;
        logger.info("麻将重连同步开始, table: {}, userId: {}, seat: {}, hand: {}, exposed: {}, wall: {}",
                getTableId(), user.getUserId(), seat, user.getCards().size(), mjContext.getExposedSets(seat).size(), mjTilePool.remaining());

        // 1. 同步手牌
        mjTilePool.sendHandNotice(getSeatUsers());

        // 2. 同步历史弃牌。重连不能只恢复当前手牌，否则客户端桌面会少牌。
        int seatNum = getTableModel().getSeatNum();
        for (int i = 0; i < seatNum; i++) {
            for (Integer tileId : mjContext.getDiscardPile(i)) {
                GameProto.NotMjState discard = GameProto.NotMjState.newBuilder()
                        .setOpSeat(i).setTileId(tileId)
                        .setAction(ConstProto.Operation.DISCARD)
                        .setWallLeft(mjTilePool.remaining()).build();
                user.sendRoleMessage(discard, GMsg.MJ_TILE_NOT, getTableId());
            }
        }

        // 3. 同步副露区
        for (int i = 0; i < seatNum; i++) {
            List<MjExposedSet> sets = mjContext.getExposedSets(i);
            for (MjExposedSet set : sets) {
                GameProto.NotMjState.Builder notBuilder = GameProto.NotMjState.newBuilder()
                        .setOpSeat(i).setAction(ConstProto.Operation.MJ_PASS)
                        .setWallLeft(mjTilePool.remaining());
                switch (set.getType()) {
                    case PENG:
                        notBuilder.setTileId(set.getTileIds().get(0));
                        notBuilder.setAction(ConstProto.Operation.MJ_PENG);
                        break;
                    case MING_GANG:
                    case AN_GANG:
                    case BU_GANG:
                        notBuilder.setTileId(set.getGangTileId());
                        notBuilder.setAction(ConstProto.Operation.MJ_GANG);
                        break;
                    case CHI:
                        notBuilder.setTileId(set.getTileIds().get(set.getTileIds().size() - 1));
                        notBuilder.setAction(ConstProto.Operation.MJ_CHI);
                        break;
                }
                notBuilder.setFromSeat(set.getFromSeat()).addAllExposedTiles(set.getTileIds());
                user.sendRoleMessage(notBuilder.build(), GMsg.MJ_TILE_NOT, getTableId());
            }
        }

        // 3. 同步状态
        GameProto.NotTableState stateNot = buildStateNotification(getTableState().getId(),
                getStateStartTime(), getTableState().getOverTime());
        user.sendRoleMessage(stateNot, GMsg.NOT_TABLE_STATE, getTableId());

        // 4. 同步赖子
        if (getTableModel().getGameSubType() == 1 && mjContext.getLaiZiTileId() != 0) {
            GameProto.NotMjState laiZiNot = GameProto.NotMjState.newBuilder()
                    .setOpSeat(-1).setTileId(mjContext.getLaiZiFlipTile())
                    .setAction(ConstProto.Operation.DRAW).setWallLeft(mjTilePool.remaining()).build();
            user.sendRoleMessage(laiZiNot, GMsg.MJ_TILE_NOT, getTableId());
        }
        logger.info("麻将重连同步完成, table: {}, userId: {}, seat: {}, hand: {}, exposed: {}, wall: {}",
                getTableId(), user.getUserId(), seat, user.getCards().size(), mjContext.getExposedSets(seat).size(), mjTilePool.remaining());
    }

    @Override
    public GameProto.AckTableSnapshot buildTableSnapshot(TableUser viewer) {
        GameProto.AckTableSnapshot.Builder b = newSnapshotBuilder(viewer)
                .setDrawnTile(mjContext.getDrawnTile())
                .setPendingDiscardTile(mjContext.getClaimTileId())
                .setPendingDiscardSeat(mjContext.getClaimFromSeat())
                .setWallLeft(mjTilePool.remaining())
                .setLaiziTile(mjContext.getLaiZiTileId())
                .setLaiziFlipTile(mjContext.getLaiZiFlipTile())
                .setDealerSeat(mjContext.getDealerSeat());
        if (viewer.getSeated() == getOp().getCurrOpSeat() && b.getChoicesCount() == 0) {
            if (getTableState() == TableState.MJ_DISCARD) {
                b.addChoices(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.DISCARD));
            } else if (getTableState() == TableState.MJ_CLAIM) {
                MjClaimInfo claim = MjClaimDetector.buildClaimInfo(this, viewer.getSeated());
                if (claim != null) {
                    if (claim.isCanHu())
                        b.addChoices(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.MJ_HU));
                    if (claim.isCanGang())
                        b.addChoices(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.MJ_GANG));
                    if (claim.isCanPeng())
                        b.addChoices(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.MJ_PENG));
                    if (claim.isCanChi())
                        b.addChoices(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.MJ_CHI));
                    b.addChoices(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.MJ_PASS));
                }
            }
        }
        long sequence = 0;
        for (MjTableContext.DiscardRecord discard : mjContext.getDiscardHistory()) {
            b.addDiscards(GameProto.SnapshotDiscard.newBuilder()
                    .setSeat(discard.getSeat()).setTileId(discard.getTileId())
                    .setSequence(++sequence));
        }
        for (int seat = 0; seat < getTableModel().getSeatNum(); seat++) {
            for (MjExposedSet set : mjContext.getExposedSets(seat)) {
                GameProto.SnapshotExposed.Builder exposed = GameProto.SnapshotExposed.newBuilder()
                        .setSeat(seat).setType(com.google.protobuf.ByteString.copyFromUtf8(set.getType().toName()));
                exposed.addAllTileIds(set.getTileIds());
                b.addExposed(exposed);
            }
        }
        return b.build();
    }

    // ======================== MJ内部方法 ========================

    private int processMjDiscard(int userId, GameProto.OpInfo op) {
        ConstProto.Operation choice = op.getChoice();
        if (choice == ConstProto.Operation.MJ_GANG) {
            if (op.getOpCardsCount() > 0) {
                int gangTileId = op.getOpCards(0).getCards(0).getValue();
                if (MjGangService.applyAnGang(this, userId, gangTileId)) return ConstProto.Result.SUCCESS_VALUE;
                if (MjGangService.applyBuGang(this, userId, gangTileId)) return ConstProto.Result.SUCCESS_VALUE;
            }
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        boolean ok = MjPlayService.applyDiscard(this, userId, op);
        if (!ok) return ConstProto.Result.OP_CURR_ERROR_VALUE;
        MjPlayService.afterDiscard(this);
        return ConstProto.Result.SUCCESS_VALUE;
    }

    private int processMjClaim(int userId, GameProto.OpInfo op) {
        boolean ok = MjClaimService.applyClaim(this, userId, op);
        return ok ? ConstProto.Result.SUCCESS_VALUE : ConstProto.Result.OP_CURR_ERROR_VALUE;
    }

    // ======================== MJ特有getter ========================

    public MjTilePool getMjTilePool() {
        return mjTilePool;
    }

    public MjTableContext getMjContext() {
        return mjContext;
    }
}
