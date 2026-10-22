package com.cloud.hub.game.domain.poker.niuniu;

import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.poker.niuniu.ai.NiuniuAi;
import com.cloud.hub.game.domain.replay.PokerReplayRecorder;
import com.cloud.hub.game.domain.replay.ReplayRecorder;
import com.cloud.hub.game.domain.table.GameResult;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import model.tablemodel.TableModel;
import msg.registor.message.GMsg;
import net.client.Sender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;
import proto.ModelProto;
import utils.registry.enums.TableState;

import java.util.Map;

/**
 * 牛牛（斗牛）游戏桌聚合根。
 * <p>
 * 遵循高内聚低耦合原则，自身维护状态机周期流转与事件路由，
 * 规则运算委托给 {@link NiuniuRules}，流程与结算委托给 {@link NiuniuFlow}，机器人委托给 {@link NiuniuAi}。
 *
 * @author cloud
 */
public class NiuniuTable extends Table {

    private static final Logger logger = LoggerFactory.getLogger(NiuniuTable.class);

    /** 玩法类型常数: 5=牛牛 */
    public static final int GAME_TYPE_NIUNIU = 5;

    private final NiuniuConfig ruleConfig;
    private final NiuniuContext context = new NiuniuContext();
    private final NiuniuPool cardPool;

    public NiuniuTable(long tableId, TableModel model, ModelProto.RoomRole creator) {
        super(tableId, model, creator);
        this.ruleConfig = NiuniuConfig.fromTableModel(model);
        this.cardPool = new NiuniuPool(this);
    }

    @Override
    public int getGameType() {
        return GAME_TYPE_NIUNIU;
    }

    @Override
    public String getGameDisplayName() {
        return "牛牛";
    }

    /**
     * 开局条件：至少满足配置的最小人数（默认 2 人）且在座全员准备完毕。
     */
    @Override
    public boolean canStartGame() {
        int seatedCount = getSeatUsers().size();
        if (seatedCount < ruleConfig.getMinStartPlayers()) {
            return false;
        }
        return isAllSeatedReady();
    }

    @Override
    public void dealCards() {
        cardPool.initCards(ruleConfig.isHasJoker());
        cardPool.dealFourCards();
        context.setStage(NiuniuContext.STAGE_ROB);
    }

    @Override
    public void resetGameContext() {
        context.reset();
    }

    @Override
    public GameResult createGameResult() {
        GameResult result = new GameResult();
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
    public TableState getInitialStartState() {
        return TableState.START_ANI;
    }

    // ======================== 状态机周期钩子 ========================

    @Override
    public boolean onStartAniTiming() {
        sendCards();
        long elapsed = System.currentTimeMillis() - getStateStartTime();
        if (elapsed >= 2000L) {
            upNextState(TableState.ROB);
        }
        return true;
    }

    @Override
    public boolean onRobTiming() {
        broadcastState(TableState.ROB, ruleConfig.getRobTimeout());
        for (TableUser user : getSeatUsers().values()) {
            if (user.isRobot()) {
                NiuniuAi.rob(this, user);
            }
        }
        return false;
    }

    @Override
    public boolean onIdleRobHandle() {
        long elapsed = System.currentTimeMillis() - getStateStartTime();
        if (elapsed >= ruleConfig.getRobTimeout() * 1000L) {
            NiuniuFlow.onRobTimeout(this);
            return true;
        }
        return false;
    }

    /**
     * 庄家确定后的回调：广播定庄通知并推进到闲家加倍下注阶段
     */
    public void onBankerDetermined() {
        context.setStage(NiuniuContext.STAGE_BET);
        broadcastBanker();
        upNextStateWithTime(TableState.IDLE_SHOW_CARD, System.currentTimeMillis());

        for (TableUser user : getSeatUsers().values()) {
            if (user.isRobot()) {
                NiuniuAi.bet(this, user);
            }
        }
    }

    @Override
    public boolean onIdleShowCardHandle() {
        if (context.getStage() == NiuniuContext.STAGE_BET) {
            long elapsed = System.currentTimeMillis() - getStateStartTime();
            if (elapsed >= ruleConfig.getBetTimeout() * 1000L) {
                NiuniuFlow.onBetTimeout(this);
                return true;
            }
        }
        return false;
    }

    /**
     * 闲家下注完成后的回调：补发第 5 张牌并推进到拼牛摊牌阶段
     */
    public void onBetCompleted() {
        context.setStage(NiuniuContext.STAGE_SHOW);
        cardPool.dealLastCard();
        sendCards();
        upNextStateWithTime(TableState.CARD, System.currentTimeMillis());

        for (TableUser user : getSeatUsers().values()) {
            if (user.isRobot()) {
                NiuniuAi.show(this, user);
            }
        }
    }

    @Override
    public boolean onCardTiming() {
        if (context.getStage() == NiuniuContext.STAGE_SHOW) {
            long elapsed = System.currentTimeMillis() - getStateStartTime();
            if (elapsed >= ruleConfig.getShowTimeout() * 1000L) {
                NiuniuFlow.onShowTimeout(this);
                return true;
            }
        }
        return false;
    }

    /**
     * 全员摊牌完成后的回调：执行闲庄比牌结算
     */
    public void onShowCompleted() {
        context.setStage(NiuniuContext.STAGE_SETTLE);
        NiuniuFlow.settle(this);
        upNextStateWithTime(TableState.TABLE_OVER, System.currentTimeMillis());
    }

    // ======================== 网络请求与操作分发 ========================

    @Override
    public int processOp(int userId, GameProto.OpInfo op, Sender sender, long mapId, int sequence) {
        TableUser user = getUserByUserId(userId);
        if (user == null || user.getSeated() < 0) {
            return ConstProto.Result.ROLE_NULL_VALUE;
        }

        int stage = context.getStage();
        if (stage == NiuniuContext.STAGE_ROB) {
            NiuniuFlow.onRob(this, userId, NiuniuFlow.parseRobMult(op));
            return ConstProto.Result.SUCCESS_VALUE;
        }

        if (stage == NiuniuContext.STAGE_BET) {
            NiuniuFlow.onBet(this, userId, NiuniuFlow.parseBetMult(op));
            return ConstProto.Result.SUCCESS_VALUE;
        }

        if (stage == NiuniuContext.STAGE_SHOW) {
            if (op.getChoice() == ConstProto.Operation.NIU_SHOW
                    || op.getChoice() == ConstProto.Operation.PLAY
                    || op.getChoiceValue() == 6) {
                NiuniuFlow.onShow(this, userId);
            }
            return ConstProto.Result.SUCCESS_VALUE;
        }

        return ConstProto.Result.SUCCESS_VALUE;
    }

    @Override
    public void syncGameState(TableUser user) {
        if (user == null || user.getSeated() < 0) {
            return;
        }
        user.sendRoleMessage(buildNotCard(user), GMsg.NOT_CARD, getTableId());

        GameProto.NotTableState stateNot = buildStateNotification(getTableState().getId(),
                getStateStartTime(), getTableState().getOverTime());
        user.sendRoleMessage(stateNot, GMsg.NOT_STATE, getTableId());
    }

    @Override
    public GameProto.AckTableSnapshot buildTableSnapshot(TableUser viewer) {
        GameProto.AckTableSnapshot.Builder b = newSnapshotBuilder(viewer);
        b.setLandlordSeat(context.getBankerSeat());
        b.setRobMultiplier(context.getBankerMultiplier());
        return b.build();
    }

    // ======================== 内部消息推送辅助 ========================

    public GameProto.NotCard buildNotCard(TableUser viewer) {
        GameProto.NotCard.Builder builder = GameProto.NotCard.newBuilder();
        for (TableUser u : getSeatUsers().values()) {
            GameProto.NCardsInfo.Builder nCards = GameProto.NCardsInfo.newBuilder()
                    .setRoleId(u.getUserId());
            boolean isSelf = (viewer != null && u.getUserId() == viewer.getUserId());
            for (Card c : u.getCards()) {
                nCards.addCards(GameProto.Card.newBuilder()
                        .setValue(isSelf ? c.getId() : 0)
                        .build());
            }
            builder.addNCards(nCards.build());
        }
        return builder.build();
    }

    public void sendCards() {
        for (TableUser user : getSeatUsers().values()) {
            user.sendRoleMessage(buildNotCard(user), GMsg.NOT_CARD, getTableId());
        }
    }

    public void broadcastRob(int userId, int seat, int multiplier) {
        GameProto.AckOp.Builder ack = GameProto.AckOp.newBuilder();
        ack.setOpId(userId);
        ack.setOpFrom(userId);
        ack.setRobMultiplier(multiplier);
        GameProto.OpInfo.Builder opInfo = GameProto.OpInfo.newBuilder();
        opInfo.setChoice(NiuniuFlow.robOp(multiplier));
        ack.setOp(opInfo);
        sendTableMessage(ack.build(), GMsg.ACK_OP);
    }

    public void broadcastBanker() {
        int mult = context.getBankerMultiplier();
        TableUser banker = getSeatUser(context.getBankerSeat());
        int bankerId = banker != null ? banker.getUserId() : 0;

        GameProto.AckOp.Builder ack = GameProto.AckOp.newBuilder();
        ack.setOpId(bankerId);
        ack.setOpFrom(bankerId);
        ack.setRobMultiplier(mult);
        ack.setCurrentMultiplier(mult);
        GameProto.OpInfo.Builder opInfo = GameProto.OpInfo.newBuilder();
        opInfo.setChoice(NiuniuFlow.robOp(mult));
        ack.setOp(opInfo);
        sendTableMessage(ack.build(), GMsg.ACK_OP);
    }

    public void broadcastBet(int userId, int seat, int multiplier) {
        GameProto.AckOp.Builder ack = GameProto.AckOp.newBuilder();
        ack.setOpId(userId);
        ack.setOpFrom(userId);
        ack.setCurrentMultiplier(multiplier);
        GameProto.OpInfo.Builder opInfo = GameProto.OpInfo.newBuilder();
        opInfo.setChoice(NiuniuFlow.betOp(multiplier));
        ack.setOp(opInfo);
        sendTableMessage(ack.build(), GMsg.ACK_OP);
    }

    public void notifyPlayerShowed(int seat) {
        TableUser user = getSeatUser(seat);
        int userId = user != null ? user.getUserId() : 0;
        GameProto.AckOp.Builder ack = GameProto.AckOp.newBuilder();
        ack.setOpId(userId);
        ack.setOpFrom(userId);
        GameProto.OpInfo.Builder opInfo = GameProto.OpInfo.newBuilder();
        opInfo.setChoice(ConstProto.Operation.NIU_SHOW);
        ack.setOp(opInfo);
        sendTableMessage(ack.build(), GMsg.ACK_OP);

        NiuniuHand hand = context.getPlayerHands().get(seat);
        if (hand != null) {
            logger.info("玩家摊牌成功: seat={}, userId={}, hand={}, tableId={}",
                    seat, userId, hand, getTableId());
        }
    }

    public void broadcastSettle(Map<Integer, Integer> roundScores) {
        GameProto.NotRoundResult.Builder builder = GameProto.NotRoundResult.newBuilder()
                .setRound(getCurrentRound())
                .setWinnerSeat(context.getBankerSeat())
                .setFan(context.getBankerMultiplier());

        NiuniuHand bankerHand = context.getPlayerHands().get(context.getBankerSeat());
        if (bankerHand != null) {
            builder.setWinType(com.google.protobuf.ByteString.copyFromUtf8(bankerHand.getCardType().getName()));
        }

        for (Map.Entry<Integer, Integer> entry : roundScores.entrySet()) {
            int seat = entry.getKey();
            int score = entry.getValue();
            builder.addSeatScores(GameProto.SeatScore.newBuilder().setSeat(seat).setScore(score).build());
            builder.addTotalScores(GameProto.SeatScore.newBuilder().setSeat(seat)
                    .setScore(getGameResult().getTotalScore(seat)).build());
        }

        for (TableUser user : getSeatUsers().values()) {
            GameProto.HandInfo.Builder handBuilder = GameProto.HandInfo.newBuilder();
            handBuilder.setSeat(user.getSeated());
            for (Card c : user.getCards()) {
                handBuilder.addHandTiles(c.getId());
            }
            builder.addHands(handBuilder.build());
        }

        sendTableMessage(builder.build(), GMsg.NOT_ROUND_RESULT);

        if (getCurrentRound() >= getTableModel().getTotalRounds()) {
            sendGameResult();
        }
    }

    private void broadcastState(TableState state, int waitSeconds) {
        GameProto.NotTableState notice = buildStateNotification(state.getId(), getStateStartTime(), waitSeconds);
        sendTableMessage(notice, GMsg.NOT_STATE);
    }

    public TableUser getUserByUserId(int userId) {
        return getUsers().get(userId);
    }

    public NiuniuConfig getRuleConfig() {
        return ruleConfig;
    }

    public NiuniuContext getContext() {
        return context;
    }

    public NiuniuPool getCardPool() {
        return cardPool;
    }
}
