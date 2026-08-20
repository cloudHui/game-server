package com.cloud.game.manager.table.pdk;

import com.cloud.game.manager.table.GameResult;
import com.cloud.game.manager.table.Table;
import com.cloud.game.manager.table.TableUser;
import com.cloud.game.manager.table.banner.Banner;
import com.cloud.game.manager.table.ddz.DdzGameResult;
import com.cloud.game.manager.table.ddz.DdzHand;
import com.cloud.game.manager.table.replay.PokerReplayRecorder;
import com.cloud.game.manager.table.replay.ReplayRecorder;
import model.tablemodel.TableModel;
import msg.registor.enums.TableState;
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
}
