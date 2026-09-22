package com.cloud.hub.game.domain.ddz;

import com.cloud.hub.game.domain.table.GameResult;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import com.cloud.hub.game.domain.banner.Banner;
import com.cloud.hub.game.domain.replay.DdzReplayRecorder;
import com.cloud.hub.game.domain.replay.ReplayRecorder;
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

    // ======================== 状态机多态实现 ========================

    @Override
    public boolean onRobTiming() {
        if (banner.isRobBroadcastDone()) {
            return false;
        }
        int seats = getTableModel().getSeatNum();

        if (!banner.isRobPhase()) {
            int seat = resolveCallOpSeat(seats);
            getOp().clearChoiceMap();

            GameProto.OpInfo notCall = GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.NOT_CALL).build();
            if (getTableModel().getGameSubType() == 1) {
                GameProto.OpInfo call = GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.CALL).build();
                getOp().addPosOpInfo(seat, call);
                sendTableMessage(GameProto.NotOperation.newBuilder().setWait(TableState.IDLE_ROB.getOverTime()).setOpSeat(seat).addChoice(notCall).addChoice(call).build(), GMsg.NOT_OP);
                banner.setRobBroadcastDone(true);
                upNextState();
                return false;
            }
            getOp().addPosOpInfo(seat, notCall);
            GameProto.NotOperation.Builder notBuilder = GameProto.NotOperation.newBuilder()
                    .setWait(TableState.IDLE_ROB.getOverTime())
                    .setOpSeat(seat).addChoice(notCall);
            for (int score = 1; score <= 3; score++) {
                if (banner.isScoreAvailable(score)) {
                    int choice = score == 1 ? ConstProto.Operation.CALL_SCORE_1_VALUE
                            : score == 2 ? ConstProto.Operation.CALL_SCORE_2_VALUE : ConstProto.Operation.CALL_SCORE_3_VALUE;
                    GameProto.OpInfo call = GameProto.OpInfo.newBuilder().setChoiceValue(choice).build();
                    getOp().addPosOpInfo(seat, call);
                    notBuilder.addChoice(call);
                }
            }
            sendTableMessage(notBuilder.build(), GMsg.NOT_OP);
        } else {
            int seat = banner.getCurrentRobSeat();
            if (seat < 0) {
                return false;
            }
            getOp().clearChoiceMap();
            getOp().setCurrOpSeat(seat);

            GameProto.OpInfo rob = GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.ROB).build();
            GameProto.OpInfo notRob = GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.NOT_ROB).build();
            getOp().addPosOpInfo(seat, rob);
            getOp().addPosOpInfo(seat, notRob);

            GameProto.NotOperation not = GameProto.NotOperation.newBuilder()
                    .setWait(TableState.IDLE_ROB.getOverTime())
                    .setOpSeat(seat)
                    .addChoice(rob)
                    .addChoice(notRob)
                    .build();
            sendTableMessage(not, GMsg.NOT_OP);
        }

        banner.setRobBroadcastDone(true);
        upNextState();
        return false;
    }

    private int resolveCallOpSeat(int seats) {
        int first = banner.getFirstRandomRobSeat();
        if (first < 0) {
            first = java.util.concurrent.ThreadLocalRandom.current().nextInt(seats);
            banner.setFirstRandomRobSeat(first);
            getOp().setCurrOpSeat(first);
            return first;
        }
        int seat = getOp().getCurrOpSeat();
        if (seat < 0) {
            seat = first;
            getOp().setCurrOpSeat(seat);
        }
        return seat;
    }

    @Override
    public void onRobOverTime() {
        DdzBidService.onBidTimeout(this);
    }

    @Override
    public boolean onCardTiming() {
        int seat = getOp().getCurrOpSeat();
        getOp().clearChoiceMap();

        java.util.List<GameProto.OpInfo> choices = DdzOperationChoices.forTurn(ddz.getLastHand());
        GameProto.NotOperation.Builder nb = GameProto.NotOperation.newBuilder()
                .setWait(TableState.IDLE_CARD.getOverTime())
                .setOpSeat(seat).addAllChoice(choices);
        for (GameProto.OpInfo choice : choices) getOp().addPosOpInfo(seat, choice);

        upNextState();
        // 切到 IDLE_CARD 后再由服务端校验整副余牌；合法且能压过时直接打完。
        if (DdzPlayService.autoPlayWholeHand(this, seat)) return false;
        sendTableMessage(nb.build(), GMsg.NOT_OP);
        return false;
    }

    @Override
    public void onCardOverTime() {
        int seat = getOp().getCurrOpSeat();
        TableUser u = getSeatUser(seat);
        if (u == null) return;
        if (DdzPlayService.autoPlayAi(this, u.getUserId())) {
            return;
        }
        if (ddz.getLastHand() == null) {
            DdzPlayService.autoPlaySmallest(this, u.getUserId());
        } else {
            DdzPlayService.apply(this, u.getUserId(),
                    proto.GameProto.OpInfo.newBuilder().setChoice(proto.ConstProto.Operation.PASS).build());
        }
    }

    // ======================== DDZ特有getter ========================

    public CardPool getCardPool() {
        return cardPool;
    }

    public Banner getBanner() {
        return banner;
    }

    @Override
    public String getGameDisplayName() {
        return "斗地主";
    }

    public DdzTableContext getDdz() {
        return ddz;
    }

    /**
     * 获取强类型的斗地主对局录像记录器。
     * <p>
     * 消除外部调用点（如 {@link DdzPlayService}、{@link DdzBidService}）频繁写模式匹配强转的冗余。
     *
     * @return 强类型的 {@link DdzReplayRecorder} 实例，若未启用或类型不匹配则返回 null
     */
    public DdzReplayRecorder getDdzReplay() {
        ReplayRecorder r = getReplayRecorder();
        return (r instanceof DdzReplayRecorder) ? (DdzReplayRecorder) r : null;
    }
}
