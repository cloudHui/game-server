package com.cloud.weball.game.domain.ddz;

import com.cloud.weball.game.domain.table.GameResult;
import com.cloud.weball.game.domain.table.Table;
import com.cloud.weball.game.domain.table.TableUser;
import com.cloud.weball.game.domain.banner.Banner;
import com.cloud.weball.game.domain.replay.DdzReplayRecorder;
import com.cloud.weball.game.domain.replay.ReplayRecorder;
import model.tablemodel.TableModel;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import net.client.Sender;
import proto.ConstProto;
import proto.GameProto;
import proto.ModelProto;

/**
 * 斗地主桌子
 * 包含斗地主特有的牌池、叫分、出牌上下文等
 */
public class DdzTable extends Table {

    private final CardPool cardPool;
    private final Banner banner;
    private final DdzTableContext ddz = new DdzTableContext();
    /**
     * 下一局优先叫牌座位（地主连庄或农民胜后下家优先）
     */
    private int nextFirstCallSeat = -1;

    public DdzTable(long tableId, TableModel model, ModelProto.RoomRole creator) {
        super(tableId, model, creator);
        this.cardPool = new CardPool(this);
        this.banner = new Banner();
    }

    // ======================== 抽象方法实现 ========================

    @Override
    public int getGameType() {
        return 2;
    }

    @Override
    public void dealCards() {
        cardPool.dealInitCard();
    }

    @Override
    public void resetGameContext() {
        int keepFirstCall = nextFirstCallSeat;
        banner.reset();
        ddz.resetHand();
        // 连庄/下家优先：保留下一局首叫座位，避免 banner.reset 清掉后随机重选。
        if (keepFirstCall >= 0) {
            banner.setFirstRandomRobSeat(keepFirstCall);
            getOp().setCurrOpSeat(keepFirstCall);
            nextFirstCallSeat = -1;
        }
    }

    /**
     * 结算后设置下一局优先叫牌座位
     */
    public void setNextFirstCallSeat(int seat) {
        this.nextFirstCallSeat = seat;
    }

    public int getNextFirstCallSeat() {
        return nextFirstCallSeat;
    }

    @Override
    public GameResult createGameResult() {
        DdzGameResult result = new DdzGameResult();
        result.setTotalRounds(getTableModel().getTotalRounds());
        return result;
    }

    @Override
    public ReplayRecorder createReplayRecorder() {
        return new DdzReplayRecorder(getTableId(), getCurrentRound());
    }

    @Override
    public void initGameConfig() {
        // DDZ无特殊初始化
    }

    @Override
    public int processOp(int userId, GameProto.OpInfo op, Sender sender, long mapId, int sequence) {
        TableState ts = getTableState();
        if (ts == TableState.IDLE_ROB) {
            return DdzBidService.apply(this, userId, op);
        }
        if (ts == TableState.IDLE_CARD) {
            return DdzPlayService.apply(this, userId, op);
        }
        return ConstProto.Result.OP_CURR_ERROR_VALUE;
    }

    @Override
    public void syncGameState(TableUser user) {
        int seat = user.getSeated();
        if (seat < 0) return;

        // 1. 同步手牌(自己的牌有值, 别人的牌值为0)
        cardPool.sendInitCardNotice(getSeatUsers());

        // 2. 同步桌子状态
        GameProto.NotTableState stateNot = buildStateNotification(getTableState().getId(),
                getStateStartTime(), getTableState().getOverTime());
        user.sendRoleMessage(stateNot, GMsg.NOT_STATE, getTableId());

        // 3. 如果当前有出牌阶段的操作, 重新通知当前操作
        TableState ts = getTableState();
        if (ts == TableState.IDLE_CARD || ts == TableState.CARD) {
            DdzHand lastHand = ddz.getLastHand();
            int opSeat = getOp().getCurrOpSeat();
            if (opSeat >= 0) {
                user.sendRoleMessage(GameProto.NotOperation.newBuilder()
                        .setWait(TableState.IDLE_CARD.getOverTime()).setOpSeat(opSeat)
                        .addAllChoice(DdzOperationChoices.forTurn(lastHand)).build(), GMsg.NOT_OP, getTableId());
            }
        } else if (ts == TableState.IDLE_ROB || ts == TableState.ROB) {
            int opSeat = getOp().getCurrOpSeat();
            if (opSeat >= 0) {
                GameProto.NotOperation notOp = GameProto.NotOperation.newBuilder()
                        .setWait(TableState.IDLE_ROB.getOverTime())
                        .setOpSeat(opSeat)
                        .addChoice(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.CALL).build())
                        .build();
                user.sendRoleMessage(notOp, GMsg.NOT_OP, getTableId());
            }
        }
    }

    @Override
    public GameProto.AckTableSnapshot buildTableSnapshot(TableUser viewer) {
        GameProto.AckTableSnapshot.Builder b = newSnapshotBuilder(viewer)
                .setLandlordSeat(ddz.getLandlordSeat())
                .setLastPlaySeat(ddz.getLastPlaySeat())
                .setBaseScore(ddz.getBaseScore())
                .setRobMultiplier(ddz.getRobMultiplier())
                .setBombMultiplier(ddz.getBombMultiplier())
                .setCurrentMultiplier(ddz.getCurrentMultiplier())
                .addAllBottomCards(ddz.getRevealedBottomCards());
        if (viewer.getSeated() == getOp().getCurrOpSeat() && b.getChoicesCount() == 0) {
            if (getTableState() == TableState.IDLE_CARD || getTableState() == TableState.CARD) {
                b.addAllChoices(DdzOperationChoices.forTurn(ddz.getLastHand()));
            } else if (getTableState() == TableState.IDLE_ROB || getTableState() == TableState.ROB) {
                b.addChoices(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.CALL));
            }
        }
        if (ddz.getLastPlayed() != null) b.setLastCards(ddz.getLastPlayed());
        int seat = ddz.getLastPlaySeat();
        for (int i = 0; i < ddz.getConsecutivePasses(); i++) {
            seat = nextSeat(seat);
            b.addPassSeats(seat);
        }
        return b.build();
    }

    // ======================== DDZ特有getter ========================

    public CardPool getCardPool() {
        return cardPool;
    }

    public Banner getBanner() {
        return banner;
    }

    public DdzTableContext getDdz() {
        return ddz;
    }
}
