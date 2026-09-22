package com.cloud.hub.game.domain.tractor;

import com.cloud.hub.game.domain.table.GameResult;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.ddz.DdzGameResult;
import com.cloud.hub.game.domain.replay.PokerReplayRecorder;
import com.cloud.hub.game.domain.replay.ReplayRecorder;
import com.cloud.hub.game.domain.state.IdleShowCard;
import model.tablemodel.TableModel;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import net.client.Sender;
import proto.ConstProto;
import proto.GameProto;
import proto.ModelProto;

import java.util.ArrayList;
import java.util.List;

/**
 * 拖拉机桌子：4 人两副牌，亮主→扣底→出牌
 */
public class TractorTable extends Table {

    private final TractorCardPool cardPool;
    private final TractorTableContext tractor = new TractorTableContext();

    public TractorTable(long tableId, TableModel model, ModelProto.RoomRole creator) {
        super(tableId, model, creator);
        this.cardPool = new TractorCardPool(this);
    }

    @Override
    public int getGameType() {
        return 4;
    }

    @Override
    public void dealCards() {
        int banker = tractor.getBankerSeat();
        if (banker < 0) banker = 0;
        tractor.setBankerSeat(banker);
        // 洗牌后进入 START_ANI：服务端一次发完，客户端播动画期间可抢主
        cardPool.prepareDeal(banker);
        getOp().setCurrOpSeat(banker);
        tractor.setTrickLeader(banker);
    }

    @Override
    public void resetGameContext() {
        tractor.resetRoundKeepLevel();
    }

    @Override
    public GameResult createGameResult() {
        DdzGameResult result = new DdzGameResult();
        result.setTotalRounds(getTableModel().getTotalRounds());
        return result;
    }

    @Override
    public ReplayRecorder createReplayRecorder() {
        return new PokerReplayRecorder(getTableId(), getCurrentRound());
    }

    @Override
    public void initGameConfig() {
    }

    @Override
    public int processOp(int userId, GameProto.OpInfo op, Sender sender, long mapId, int sequence) {
        TableState ts = getTableState();
        if (ts == TableState.START_ANI && tractor.isDealing()) {
            return TractorBidService.applyDuringDeal(this, userId, op);
        }
        if (ts == TableState.IDLE_ROB) {
            return TractorBidService.apply(this, userId, op);
        }
        if (ts == TableState.IDLE_SHOW_CARD) {
            ConstProto.Operation choice = op.getChoice();
            TableUser actor = getUsers().get(userId);
            boolean isOtherSeat = actor != null && actor.getSeated() != tractor.getBottomHolderSeat();
            if (choice == ConstProto.Operation.ROB
                    || (isOtherSeat && TractorBidService.isPass(choice))) {
                return TractorBidService.applyReverseDuringBury(this, userId, op);
            }
            return applyBury(userId, op);
        }
        if (ts == TableState.IDLE_CARD) {
            return TractorPlayService.apply(this, userId, op);
        }
        return ConstProto.Result.OP_CURR_ERROR_VALUE;
    }

    private int applyBury(int userId, GameProto.OpInfo op) {
        TableUser user = getUsers().get(userId);
        if (user == null || user.getSeated() != tractor.getBottomHolderSeat()) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        if (op.getChoice() != ConstProto.Operation.DISCARD && op.getChoice() != ConstProto.Operation.PLAY) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        List<Integer> ids = new ArrayList<>();
        for (GameProto.CardInfo ci : op.getOpCardsList()) {
            for (GameProto.Card c : ci.getCardsList()) ids.add(c.getValue());
        }
        if (!cardPool.buryCards(this, tractor.getBottomHolderSeat(), ids)) {
            return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
        }
        // 放回 8 张后开始出牌
        getOp().setCurrOpSeat(tractor.getBankerSeat());
        tractor.setTrickLeader(tractor.getBankerSeat());
        upNextStateWithTime(TableState.CARD, System.currentTimeMillis());
        return ConstProto.Result.SUCCESS_VALUE;
    }

    @Override
    public void syncGameState(TableUser user) {
        if (user.getSeated() < 0) return;
        cardPool.sendInitCardNotice(getSeatUsers());
        user.sendRoleMessage(buildStateNotification(getTableState().getId(),
                getStateStartTime(), tractorStateDuration()), GMsg.NOT_STATE, getTableId());
        TableState ts = getTableState();
        int opSeat = getOp().getCurrOpSeat();
        if (opSeat < 0) return;
        if (ts == TableState.START_ANI && tractor.isDealing()) {
            TractorBidService.notifyDealBid(this);
        } else if (ts == TableState.IDLE_ROB || ts == TableState.ROB) {
            TractorBidService.notifyCurrent(this);
        } else if (ts == TableState.IDLE_SHOW_CARD) {
            TractorBidService.notifyBury(this);
        } else if (ts == TableState.IDLE_CARD || ts == TableState.CARD) {
            user.sendRoleMessage(GameProto.NotOperation.newBuilder()
                    .setWait(TableState.IDLE_CARD.getOverTime())
                    .setOpSeat(opSeat)
                    .addChoice(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PLAY))
                    .build(), GMsg.NOT_OP, getTableId());
        }
    }

    @Override
    public GameProto.AckTableSnapshot buildTableSnapshot(TableUser viewer) {
        GameProto.AckTableSnapshot.Builder b = newSnapshotBuilder(viewer)
                .setStateDuration(tractorStateDuration())
                .setLandlordSeat(tractor.getBankerSeat())
                .setLastPlaySeat(tractor.getLastPlaySeat())
                .setBaseScore(tractor.getDefenderScore())
                .setRobMultiplier(tractor.getLevelRank())
                .setBombMultiplier(Math.max(0, tractor.getTrumpSuit()))
                .setCurrentMultiplier(Math.max(1, tractor.getBidStrength()));
        // 底牌牌面仅当前持底/扣底庄家可见（扣完也可看、不可改）
        int holder = tractor.getBottomHolderSeat();
        if (holder < 0) holder = tractor.getBankerSeat();
        if (viewer.getSeated() == holder && !tractor.getRevealedBottom().isEmpty()) {
            b.addAllBottomCards(tractor.getRevealedBottom());
        }
        // 本墩已出牌（断线重连恢复四人桌面）
        for (int i = 0; i < tractor.getTrickSeats().size(); i++) {
            GameProto.SnapshotExposed.Builder ex = GameProto.SnapshotExposed.newBuilder()
                    .setSeat(tractor.getTrickSeats().get(i))
                    .setType(com.google.protobuf.ByteString.copyFromUtf8("trick"));
            for (Card c : tractor.getTrickPlays().get(i)) ex.addTileIds(c.getId());
            b.addExposed(ex);
        }
        if (viewer.getSeated() == getOp().getCurrOpSeat() && b.getChoicesCount() == 0) {
            TableState ts = getTableState();
            if (ts == TableState.IDLE_CARD || ts == TableState.CARD) {
                b.addChoices(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PLAY));
            } else if (ts == TableState.IDLE_SHOW_CARD) {
                b.addChoices(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.DISCARD));
            } else if (ts == TableState.IDLE_ROB || (ts == TableState.START_ANI && tractor.isDealing())) {
                b.addChoices(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.NOT_CALL));
                b.addChoices(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.CALL));
                b.addChoices(GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.ROB));
            }
        }
        // 扣底阶段允许其他座位同时反主；其个性化 choices 应在重连后直接展示。
        if (getTableState() == TableState.IDLE_SHOW_CARD && b.getChoicesCount() > 0) {
            b.setOpSeat(viewer.getSeated());
        }
        if (tractor.getLastPlayed() != null) b.setLastCards(tractor.getLastPlayed());
        return b.build();
    }

    private int tractorStateDuration() {
        if (getTableState() == TableState.IDLE_ROB) return TractorBidService.DECLARE_SECONDS;
        if (getTableState() == TableState.IDLE_SHOW_CARD) {
            return TRACTOR_BURY_SECONDS;
        }
        return getTableState().getOverTime();
    }

    // ======================== 状态机多态实现 ========================

    @Override
    public boolean onRobTiming() {
        TractorBidService.notifyCurrent(this);
        upNextState();
        return false;
    }

    @Override
    public boolean onIdleRobHandle() {
        long now = System.currentTimeMillis();
        int seat = getOp().getCurrOpSeat();
        TableUser u = getSeatUser(seat);
        if (u != null && u.isRobot()
                && now >= getStateStartTime() + com.cloud.hub.game.domain.table.RobotOperationDelay.randomMillis()) {
            TractorBidService.autoBid(this, seat);
            return true;
        }
        if (now >= getStateStartTime() + TractorBidService.DECLARE_SECONDS * 1000L) {
            onRobOverTime();
            return true;
        }
        return false;
    }

    @Override
    public void onRobOverTime() {
        TractorBidService.onTimeout(this);
    }

    @Override
    public boolean onCardTiming() {
        int seat = getOp().getCurrOpSeat();
        getOp().clearChoiceMap();
        GameProto.OpInfo play = GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PLAY).build();
        getOp().addPosOpInfo(seat, play);
        GameProto.NotOperation.Builder nb = GameProto.NotOperation.newBuilder()
                .setWait(TableState.IDLE_CARD.getOverTime())
                .setOpSeat(seat)
                .addChoice(play);
        // 拖拉机每轮都要跟牌，无“过牌”
        sendTableMessage(nb.build(), GMsg.NOT_OP);
        upNextState();
        return false;
    }

    @Override
    public long getRobotCardDelay() {
        long delay = super.getRobotCardDelay();
        if (tractor.getLeadCombo() == null) {
            return Math.max(delay, 2_000L);
        }
        return delay;
    }

    @Override
    public void onCardOverTime() {
        TractorPlayService.autoPlay(this, getOp().getCurrOpSeat());
    }

    @Override
    public boolean onStartAniTiming() {
        return TractorDealService.onTiming(this);
    }

    public static final int TRACTOR_BURY_SECONDS = 30;

    @Override
    public boolean onIdleShowCardHandle() {
        int seat = tractor.getBottomHolderSeat();
        if (seat < 0) seat = tractor.getBankerSeat();
        TableUser u = getSeatUser(seat);
        long now = System.currentTimeMillis();
        long deadline = getStateStartTime() + TRACTOR_BURY_SECONDS * 1000L;

        if (u != null && u.isRobot()
                && now >= getStateStartTime() + com.cloud.hub.game.domain.table.RobotOperationDelay.randomMillis()) {
            finishBuryAndPlay(seat);
            return true;
        }
        if (now >= deadline) {
            finishBuryAndPlay(seat);
            return true;
        }
        return true;
    }

    @Override
    public void onIdleShowCardOverTime() {
        finishBuryAndPlay(tractor.getBankerSeat());
    }

    private void finishBuryAndPlay(int seat) {
        if (tractor.getBuriedCards().isEmpty()) {
            cardPool.autoBury(this, seat);
        }
        getOp().setCurrOpSeat(seat);
        tractor.setTrickLeader(seat);
        upNextState(TableState.CARD);
    }

    @Override
    public void onGameStarted() {
        // 首出座位已在 dealCards 中由庄家设定，无需默认重置为 0
    }

    @Override
    public boolean shouldRecordInitHands() {
        return false;
    }

    @Override
    public String getGameDisplayName() {
        return "拖拉机";
    }

    public TractorCardPool getCardPool() {
        return cardPool;
    }

    public TractorTableContext getTractor() {
        return tractor;
    }

    /**
     * 获取强类型的拖拉机扑克对局录像记录器。
     * <p>
     * 消除外部出牌、叫分、扣底逻辑中繁复的 {@code if (replay instanceof PokerReplayRecorder)} 强转。
     *
     * @return 强类型的 {@link PokerReplayRecorder} 实例，若未启用或类型不匹配则返回 null
     */
    public PokerReplayRecorder getPokerReplay() {
        ReplayRecorder r = getReplayRecorder();
        return (r instanceof PokerReplayRecorder) ? (PokerReplayRecorder) r : null;
    }
}
