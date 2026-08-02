package game.manager.table.tractor;

import game.manager.table.GameResult;
import game.manager.table.Table;
import game.manager.table.TableUser;
import game.manager.table.cards.Card;
import game.manager.table.ddz.DdzGameResult;
import game.manager.table.replay.PokerReplayRecorder;
import game.manager.table.replay.ReplayRecorder;
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
            if (choice == ConstProto.Operation.ROB || choice == ConstProto.Operation.CALL
                    || choice == ConstProto.Operation.NOT_CALL || choice == ConstProto.Operation.NOT_ROB
                    || choice == ConstProto.Operation.PASS) {
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
        if (user == null || user.getSeated() != tractor.getBankerSeat()) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        if (op.getChoice() != ConstProto.Operation.DISCARD && op.getChoice() != ConstProto.Operation.PLAY) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        List<Integer> ids = new ArrayList<>();
        for (GameProto.CardInfo ci : op.getOpCardsList()) {
            for (GameProto.Card c : ci.getCardsList()) ids.add(c.getValue());
        }
        if (!cardPool.buryCards(this, tractor.getBankerSeat(), ids)) {
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
        user.sendRoleMessage(GameProto.NotTableState.newBuilder()
                .setState(getTableState().getId())
                .setStateStart(getStateStartTime())
                .setStateDuration(tractorStateDuration()).build(), GMsg.NOT_STATE, getTableId());
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
        if (tractor.getLastPlayed() != null) b.setLastCards(tractor.getLastPlayed());
        return b.build();
    }

    private int tractorStateDuration() {
        if (getTableState() == TableState.IDLE_ROB) return TractorBidService.DECLARE_SECONDS;
        if (getTableState() == TableState.IDLE_SHOW_CARD) {
            return game.manager.table.state.IdleShowCard.TRACTOR_BURY_SECONDS;
        }
        return getTableState().getOverTime();
    }

    public TractorCardPool getCardPool() {
        return cardPool;
    }

    public TractorTableContext getTractor() {
        return tractor;
    }
}
