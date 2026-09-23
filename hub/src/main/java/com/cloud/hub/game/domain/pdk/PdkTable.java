package com.cloud.hub.game.domain.pdk;

import com.cloud.hub.game.domain.table.GameResult;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import com.cloud.hub.game.domain.banner.Banner;
import com.cloud.hub.game.domain.ddz.DdzGameResult;
import com.cloud.hub.game.domain.ddz.DdzHand;
import com.cloud.hub.game.domain.replay.PokerReplayRecorder;
import com.cloud.hub.game.domain.replay.ReplayRecorder;
import model.tablemodel.TableModel;
import utils.registry.enums.TableState;
import msg.registor.message.GMsg;
import net.client.Sender;
import proto.ConstProto;
import proto.GameProto;
import proto.ModelProto;

/**
 * 跑得快桌子：3 人，无叫抢，发完直接出牌。
 */
public class PdkTable extends Table {

    private final PdkCardPool cardPool;
    private final Banner banner = new Banner();
    private final PdkTableContext pdk = new PdkTableContext();

    public PdkTable(long tableId, TableModel model, ModelProto.RoomRole creator) {
        super(tableId, model, creator);
        this.cardPool = new PdkCardPool(this);
    }

    @Override
    public int getGameType() {
        return 3;
    }

    @Override
    public void dealCards() {
        cardPool.dealInitCard();
        int first = pdk.getFirstSeat();
        if (first < 0) {
            first = cardPool.findSeatWithCard(PdkCardPool.DIAMOND_3);
        }
        pdk.setFirstSeat(first);
        getOp().setCurrOpSeat(first);
    }

    @Override
    public void resetGameContext() {
        int keepFirst = pdk.getFirstSeat();
        pdk.resetRound();
        pdk.setFirstSeat(keepFirst);
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
        if (getTableState() == TableState.IDLE_CARD) {
            return PdkPlayService.apply(this, userId, op);
        }
        return ConstProto.Result.OP_CURR_ERROR_VALUE;
    }

    @Override
    public void syncGameState(TableUser user) {
        int seat = user.getSeated();
        if (seat < 0) return;
        cardPool.sendInitCardNotice(getSeatUsers());
        GameProto.NotTableState stateNot = buildStateNotification(getTableState().getId(),
                getStateStartTime(), getTableState().getOverTime());
        user.sendRoleMessage(stateNot, GMsg.NOT_STATE, getTableId());
        TableState ts = getTableState();
        if (ts == TableState.IDLE_CARD || ts == TableState.CARD) {
            int opSeat = getOp().getCurrOpSeat();
            if (opSeat < 0) return;
            GameProto.NotOperation.Builder nb = GameProto.NotOperation.newBuilder()
                    .setWait(TableState.IDLE_CARD.getOverTime())
                    .setOpSeat(opSeat)
                    .addChoice(currentOpChoice());
            user.sendRoleMessage(nb.build(), GMsg.NOT_OP, getTableId());
        }
    }

    @Override
    public GameProto.AckTableSnapshot buildTableSnapshot(TableUser viewer) {
        GameProto.AckTableSnapshot.Builder b = newSnapshotBuilder(viewer)
                .setLastPlaySeat(pdk.getLastPlaySeat())
                .setBaseScore(1)
                .setRobMultiplier(1)
                .setBombMultiplier(1)
                .setCurrentMultiplier(1);
        b.addAllPassSeats(pdk.getPassSeats());
        if (viewer.getSeated() == getOp().getCurrOpSeat() && b.getChoicesCount() == 0) {
            if (getTableState() == TableState.IDLE_CARD || getTableState() == TableState.CARD) {
                b.addChoices(currentOpChoice());
            }
        }
        if (pdk.getLastPlayed() != null) b.setLastCards(pdk.getLastPlayed());
        return b.build();
    }

    public PdkCardPool getCardPool() {
        return cardPool;
    }

    public Banner getBanner() {
        return banner;
    }

    public PdkTableContext getPdk() {
        return pdk;
    }

    /**
     * CARD 通知阶段供状态机读取上一手
     */
    public DdzHand getLastHand() {
        return pdk.getLastHand();
    }

    /**
     * 管不上仅不出；首出或能管仅出牌。
     */
    public GameProto.OpInfo currentOpChoice() {
        if (canCurrentPlayerPass()) {
            return GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PASS).build();
        }
        return GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PLAY).build();
    }

    public boolean canCurrentPlayerPass() {
        if (pdk.getLastHand() == null) return false;
        TableUser user = getSeatUser(getOp().getCurrOpSeat());
        return user != null && !PdkRules.canBeat(user.getCards(), pdk.getLastHand());
    }

    // ======================== 状态机多态实现 ========================

    @Override
    public boolean onCardTiming() {
        int seat = getOp().getCurrOpSeat();
        getOp().clearChoiceMap();
        // 跑得快：管不上只下发不出；首出/能管只下发出牌
        GameProto.OpInfo choice = currentOpChoice();
        getOp().addPosOpInfo(seat, choice);
        GameProto.NotOperation.Builder nb = GameProto.NotOperation.newBuilder()
                .setWait(TableState.IDLE_CARD.getOverTime())
                .setOpSeat(seat)
                .addChoice(choice);
        sendTableMessage(nb.build(), GMsg.NOT_OP);
        upNextState();
        return false;
    }

    @Override
    public void onCardOverTime() {
        int seat = getOp().getCurrOpSeat();
        TableUser u = getSeatUser(seat);
        if (u == null) return;
        if (PdkPlayService.autoPlayAi(this, u.getUserId())) return;
        if (pdk.getLastHand() == null) {
            PdkPlayService.autoPlaySmallest(this, u.getUserId());
            return;
        }
        // 有牌必管：能压则不能 PASS，只能再走 AI/出牌；关不上才允许过
        if (canCurrentPlayerPass()) {
            PdkPlayService.apply(this, u.getUserId(),
                    proto.GameProto.OpInfo.newBuilder().setChoice(proto.ConstProto.Operation.PASS).build());
        }
    }

    @Override
    public TableState getInitialStartState() {
        return TableState.CARD;
    }

    @Override
    public void onGameStarted() {
        // 跑得快首出座位在 dealCards 中根据手牌(如黑桃3)设定，无需重置为 0
    }

    @Override
    public String getGameDisplayName() {
        return "跑得快";
    }

    /**
     * 获取强类型的跑得快扑克对局录像记录器。
     * <p>
     * 消除外部 {@link PdkPlayService} 等服务类在记录出牌动作时的频繁强转。
     *
     * @return 强类型的 {@link PokerReplayRecorder} 实例，若未启用或类型不匹配则返回 null
     */
    public PokerReplayRecorder getPokerReplay() {
        ReplayRecorder r = getReplayRecorder();
        return (r instanceof PokerReplayRecorder) ? (PokerReplayRecorder) r : null;
    }
}
