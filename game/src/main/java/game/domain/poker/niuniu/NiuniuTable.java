package game.domain.poker.niuniu;

import java.util.Map;
import java.util.function.Consumer;

import game.domain.cards.Card;
import game.domain.poker.niuniu.ai.NiuniuAi;
import game.domain.replay.PokerReplayRecorder;
import game.domain.replay.ReplayRecorder;
import game.domain.table.GameResult;
import game.domain.table.RobotOperationDelay;
import game.domain.table.Table;
import game.domain.table.TableUser;
import com.google.protobuf.ByteString;

import model.tablemodel.TableModel;
import msg.registor.message.GMsg;
import net.client.Sender;
import proto.ConstProto;
import proto.GameProto;
import proto.ModelProto;
import utils.registry.enums.TableState;

/**
 * 牛牛（明牌抢庄斗牛）游戏桌聚合根。
 * <p>
 * 负责状态机驱动、断线重连快照组装及协议推送；算牛委托 {@link NiuniuRules}，
 * 阶段结算委托 {@link NiuniuFlow}，机器人拟人延迟决策委托 {@link NiuniuAi}。
 * </p>
 */
public class NiuniuTable extends Table {

    /** 玩法类型常数：5 代表牛牛 */
    public static final int GAME_TYPE_NIUNIU = 5;
    /** 经典多人房最低开局人数 */
    public static final int MIN_START_PLAYERS = 2;

    /** 明牌抢庄阶段时限（秒） */
    private static final int ROB_TIMEOUT = 5;
    /** 闲家加倍阶段时限（秒） */
    private static final int BET_TIMEOUT = 5;
    /** 拼牛亮牌阶段时限（秒） */
    private static final int SHOW_TIMEOUT = 8;

    /** 牛牛单局运行时上下文 */
    private final NiuniuContext context = new NiuniuContext();
    /** 牛牛牌堆与两段发牌管理器 */
    private final NiuniuPool cardPool;

    /**
     * 构造牛牛游戏桌实例。
     *
     * @param tableId 桌子全局唯一 ID
     * @param model   房间规则配置模板
     * @param creator 创建者角色信息
     */
    public NiuniuTable(long tableId, TableModel model, ModelProto.RoomRole creator) {
        super(tableId, model, creator);
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

    @Override
    public boolean canStartGame() {
        if (getSeatUsers().size() < MIN_START_PLAYERS) {
            return false;
        }
        if (isRobotRoom()) {
            return sitFull();
        }
        return getCurrentRound() > 1 || isAllSeatedReady();
    }

    @Override
    public boolean allReady() {
        return getSeatUsers().size() >= MIN_START_PLAYERS && isAllSeatedReady();
    }

    @Override
    public void dealCards() {
        cardPool.initCards();
        cardPool.dealFourCards();
        context.setStage(NiuniuContext.STAGE_ROB);
    }

    @Override
    public void onGameStarted() {
        sendCards();
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
        return TableState.ROB;
    }

    @Override
    public boolean onRobTiming() {
        // 仅广播一次抢庄通知并立即切入 IDLE_ROB，防止停留在 ROB 状态周期重刷
        enterStage(NiuniuContext.STAGE_ROB, TableState.IDLE_ROB, TableState.ROB, ROB_TIMEOUT);
        return false;
    }

    @Override
    public boolean onIdleRobHandle() {
        return tickStage(NiuniuContext.STAGE_ROB, ROB_TIMEOUT,
                u -> NiuniuAi.rob(this, u), () -> NiuniuFlow.onRobTimeout(this), true);
    }

    @Override
    public void onRobOverTime() {
        NiuniuFlow.onRobTimeout(this);
    }

    /**
     * 定庄完成后广播庄家身份并进入闲家加倍阶段。
     */
    public void onBankerDetermined() {
        broadcastBanker();
        enterStage(NiuniuContext.STAGE_BET, TableState.IDLE_SHOW_CARD, TableState.IDLE_SHOW_CARD, BET_TIMEOUT);
    }

    @Override
    public boolean onIdleShowCardHandle() {
        // 返回 true 接管超时，防止父类默认超时直接跳入 CARD 而漏发第 5 张牌
        return tickStage(NiuniuContext.STAGE_BET, BET_TIMEOUT,
                u -> NiuniuAi.bet(this, u), () -> NiuniuFlow.onBetTimeout(this), true);
    }

    @Override
    public void onIdleShowCardOverTime() {
        NiuniuFlow.onBetTimeout(this);
    }

    /**
     * 闲家全部完成下注后统一补发第 5 张决胜牌并进入亮牌阶段。
     */
    public void onBetCompleted() {
        cardPool.dealLastCard();
        sendCards();
        enterStage(NiuniuContext.STAGE_SHOW, TableState.CARD, TableState.CARD, SHOW_TIMEOUT);
    }

    @Override
    public boolean onCardTiming() {
        return tickStage(NiuniuContext.STAGE_SHOW, SHOW_TIMEOUT,
                u -> NiuniuAi.show(this, u), () -> NiuniuFlow.onShowTimeout(this), false);
    }

    /**
     * 全员亮牌后执行单局比牌结算并切入局间等待。
     */
    public void onShowCompleted() {
        context.setStage(NiuniuContext.STAGE_SETTLE);
        NiuniuFlow.settle(this);
        upNextStateWithTime(TableState.TABLE_OVER, System.currentTimeMillis());
    }

    @Override
    public int processOp(int userId, GameProto.OpInfo op, Sender sender, long mapId, int sequence) {
        TableUser user = getUserByUserId(userId);
        if (user == null || user.getSeated() < 0) {
            return ConstProto.Result.ROLE_NULL_VALUE;
        }
        int stage = context.getStage();
        if (stage == NiuniuContext.STAGE_ROB) {
            NiuniuFlow.onRob(this, userId, NiuniuFlow.parseRobMult(op));
        } else if (stage == NiuniuContext.STAGE_BET) {
            NiuniuFlow.onBet(this, userId, NiuniuFlow.parseBetMult(op));
        } else if (stage == NiuniuContext.STAGE_SHOW
                && (op.getChoice() == ConstProto.Operation.NIU_SHOW
                || op.getChoice() == ConstProto.Operation.PLAY
                || op.getChoiceValue() == 6)) {
            NiuniuFlow.onShow(this, userId);
        }
        return ConstProto.Result.SUCCESS_VALUE;
    }

    @Override
    public void syncGameState(TableUser user) {
        if (user == null || user.getSeated() < 0) {
            return;
        }
        user.sendRoleMessage(buildNotCard(user), GMsg.NOT_CARD, getTableId());
        if (context.getBankerSeat() < 0) {
            for (Map.Entry<Integer, Integer> e : context.getRobMultipliers().entrySet()) {
                TableUser su = getSeatUser(e.getKey());
                if (su != null) {
                    user.sendRoleMessage(buildOpAck(su.getUserId(), NiuniuFlow.robOp(e.getValue()), e.getValue(), 0, 0),
                            GMsg.ACK_OP, getTableId());
                }
            }
        } else {
            user.sendRoleMessage(buildBankerAck(), GMsg.ACK_OP, getTableId());
            for (Map.Entry<Integer, Integer> e : context.getBetMultipliers().entrySet()) {
                TableUser su = getSeatUser(e.getKey());
                if (su != null) {
                    user.sendRoleMessage(buildOpAck(su.getUserId(), NiuniuFlow.betOp(e.getValue()), 0, e.getValue(), 0),
                            GMsg.ACK_OP, getTableId());
                }
            }
            for (Integer seat : context.getShowedSeats()) {
                TableUser su = getSeatUser(seat);
                if (su != null) {
                    user.sendRoleMessage(buildOpAck(su.getUserId(), ConstProto.Operation.NIU_SHOW, 0, 0, 0),
                            GMsg.ACK_OP, getTableId());
                }
            }
        }
        user.sendRoleMessage(buildStateNotification(getTableState().getId(), getStateStartTime(), stageDuration()),
                GMsg.NOT_STATE, getTableId());
    }

    @Override
    public GameProto.AckTableSnapshot buildTableSnapshot(TableUser viewer) {
        GameProto.AckTableSnapshot.Builder b = newSnapshotBuilder(viewer);
        b.setStateDuration(stageDuration());
        b.setBaseScore(Math.max(1, getTableModel().getBaseScore()));
        b.setLandlordSeat(context.getBankerSeat());
        b.setRobMultiplier(context.getBankerMultiplier());
        int viewerBet = viewer != null ? context.getBetMultipliers().getOrDefault(viewer.getSeated(), 0) : 0;
        b.setCurrentMultiplier(viewerBet > 0 ? viewerBet : context.getBankerMultiplier());
        return b.build();
    }

    /**
     * 构建面向特定观察者的手牌通知包（结算前对他人暗牌脱敏）。
     *
     * @param viewer 接收手牌消息的目标玩家
     * @return 手牌通知消息
     */
    public GameProto.NotCard buildNotCard(TableUser viewer) {
        GameProto.NotCard.Builder builder = GameProto.NotCard.newBuilder();
        boolean revealAll = (context.getStage() == NiuniuContext.STAGE_SETTLE);
        for (TableUser u : getSeatUsers().values()) {
            GameProto.NCardsInfo.Builder nCards = GameProto.NCardsInfo.newBuilder().setRoleId(u.getUserId());
            boolean canSee = revealAll || (viewer != null && u.getUserId() == viewer.getUserId());
            for (Card c : u.getCards()) {
                nCards.addCards(GameProto.Card.newBuilder().setValue(canSee ? c.getId() : 0).build());
            }
            builder.addNCards(nCards.build());
        }
        return builder.build();
    }

    /**
     * 向全桌在座玩家分别推送脱敏后的当前手牌。
     */
    public void sendCards() {
        for (TableUser user : getSeatUsers().values()) {
            user.sendRoleMessage(buildNotCard(user), GMsg.NOT_CARD, getTableId());
        }
    }

    /**
     * 广播单个玩家的抢庄倍数表态。
     *
     * @param userId     玩家 ID
     * @param multiplier 抢庄倍数（0~4）
     */
    public void broadcastRob(int userId, int multiplier) {
        sendTableMessage(buildOpAck(userId, NiuniuFlow.robOp(multiplier), multiplier, 0, 0), GMsg.ACK_OP);
    }

    /**
     * 广播最终定庄结果（使用 {@link ConstProto.Operation#ROB} 与个人抢庄表态区分）。
     */
    public void broadcastBanker() {
        sendTableMessage(buildBankerAck(), GMsg.ACK_OP);
    }

    /**
     * 广播单个闲家的下注倍数。
     *
     * @param userId     闲家玩家 ID
     * @param multiplier 下注倍数（1~5）
     */
    public void broadcastBet(int userId, int multiplier) {
        sendTableMessage(buildOpAck(userId, NiuniuFlow.betOp(multiplier), 0, multiplier, 0), GMsg.ACK_OP);
    }

    /**
     * 广播单个座位已完成亮牌。
     *
     * @param seat 已摊牌座位号
     */
    public void notifyPlayerShowed(int seat) {
        TableUser user = getSeatUser(seat);
        sendTableMessage(buildOpAck(user != null ? user.getUserId() : 0, ConstProto.Operation.NIU_SHOW, 0, 0, 0),
                GMsg.ACK_OP);
    }

    /**
     * 广播单局小结算详情（总结算由 {@link game.domain.state.TableOverBridge} 统一处理）。
     *
     * @param roundScores 各座位本局得分增减映射
     */
    public void broadcastSettle(Map<Integer, Integer> roundScores) {
        GameProto.NotRoundResult.Builder builder = GameProto.NotRoundResult.newBuilder()
                .setRound(getCurrentRound())
                .setWinnerSeat(context.getBankerSeat())
                .setFan(context.getBankerMultiplier());
        NiuniuHand bankerHand = context.getPlayerHands().get(context.getBankerSeat());
        if (bankerHand != null) {
            builder.setWinType(ByteString.copyFromUtf8(bankerHand.getCardType().getName()));
        }
        for (Map.Entry<Integer, Integer> e : roundScores.entrySet()) {
            int seat = e.getKey();
            builder.addSeatScores(GameProto.SeatScore.newBuilder().setSeat(seat).setScore(e.getValue()).build());
            builder.addTotalScores(GameProto.SeatScore.newBuilder()
                    .setSeat(seat).setScore(getGameResult().getTotalScore(seat)).build());
        }
        for (TableUser user : getSeatUsers().values()) {
            GameProto.HandInfo.Builder hb = GameProto.HandInfo.newBuilder().setSeat(user.getSeated());
            for (Card c : user.getCards()) {
                hb.addHandTiles(c.getId());
            }
            builder.addHands(hb.build());
        }
        sendTableMessage(builder.build(), GMsg.NOT_ROUND_RESULT);
    }

    /**
     * 根据用户 ID 查找桌内玩家对象。
     *
     * @param userId 玩家 ID
     * @return 桌内玩家实体，不存在返回 null
     */
    public TableUser getUserByUserId(int userId) {
        return getUsers().get(userId);
    }

    /**
     * 获取当前局运行时上下文。
     *
     * @return 运行时上下文
     */
    public NiuniuContext getContext() {
        return context;
    }

    /**
     * 获取本桌牛牛牌池管理器。
     *
     * @return 牌池管理器
     */
    public NiuniuPool getCardPool() {
        return cardPool;
    }

    private void enterStage(int stage, TableState nextState, TableState noticeState, int timeoutSec) {
        long now = System.currentTimeMillis();
        context.setStage(stage);
        context.setRobotActAt(now + RobotOperationDelay.randomMillis());
        upNextStateWithTime(nextState, now);
        sendTableMessage(buildStateNotification(noticeState.getId(), now, timeoutSec), GMsg.NOT_STATE);
    }

    private boolean tickStage(int expectedStage, int timeoutSec, Consumer<TableUser> robotAction,
            Runnable onTimeout, boolean handledReturn) {
        if (context.getStage() != expectedStage) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now >= context.getRobotActAt()) {
            for (TableUser user : getSeatUsers().values()) {
                if (context.getStage() != expectedStage) {
                    break;
                }
                if (user.isRobot()) {
                    robotAction.accept(user);
                }
            }
        }
        if (context.getStage() == expectedStage && now - getStateStartTime() >= timeoutSec * 1000L) {
            onTimeout.run();
        }
        return handledReturn;
    }

    private int stageDuration() {
        int stage = context.getStage();
        if (stage == NiuniuContext.STAGE_ROB) {
            return ROB_TIMEOUT;
        }
        if (stage == NiuniuContext.STAGE_BET) {
            return BET_TIMEOUT;
        }
        if (stage == NiuniuContext.STAGE_SHOW) {
            return SHOW_TIMEOUT;
        }
        return Math.max(0, getTableState().getOverTime());
    }

    private GameProto.AckOp buildBankerAck() {
        TableUser banker = getSeatUser(context.getBankerSeat());
        int bankerId = banker != null ? banker.getUserId() : 0;
        int mult = context.getBankerMultiplier();
        return buildOpAck(bankerId, ConstProto.Operation.ROB, mult, mult, Math.max(1, getTableModel().getBaseScore()));
    }

    private GameProto.AckOp buildOpAck(int userId, ConstProto.Operation choice, int robMult, int curMult, int baseScore) {
        GameProto.AckOp.Builder b = GameProto.AckOp.newBuilder()
                .setOpId(userId)
                .setOpFrom(userId)
                .setOp(GameProto.OpInfo.newBuilder().setChoice(choice).build());
        if (robMult > 0) {
            b.setRobMultiplier(robMult);
        }
        if (curMult > 0) {
            b.setCurrentMultiplier(curMult);
        }
        if (baseScore > 0) {
            b.setBaseScore(baseScore);
        }
        return b.build();
    }
}
