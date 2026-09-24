package com.cloud.hub.game.domain.table;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.op.Operate;
import com.cloud.hub.game.domain.replay.ReplayRecorder;
import com.cloud.hub.game.domain.state.TableHeartbeatLifecycle;
import com.cloud.hub.game.domain.state.TableStateHandleManager;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import model.tablemodel.RobotRoomTemplates;
import model.tablemodel.TableModel;
import utils.registry.enums.TableState;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;
import proto.ModelProto;
import utils.trace.TraceContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * 棋牌桌子通用聚合根与生命周期基类。
 * <p>
 * <b>核心职责：</b>
 * <ul>
 *   <li>维护桌子标识（tableId）、桌规则模型（tableModel）与创建者信息；</li>
 *   <li>管理玩家进退桌、座位分配（seatUsers）、在线状态与补位陪练机器人；</li>
 *   <li>驱动状态机主循环（tableLoop 与 tick 调度），支持动态调整空闲/对局 tick 间隔；</li>
 *   <li>管理对局多局流转、总结算汇总广播（sendGameResult）与安全异步销毁；</li>
 *   <li>提供纯多态生命周期钩子，消灭外层状态机类型强转分支。</li>
 * </ul>
 *
 * @author cloud
 */
public abstract class Table {
    private static final Logger logger = LoggerFactory.getLogger(Table.class);

    private final long tableId;
    private final TableModel tableModel;
    private final ModelProto.RoomRole creator;

    private final Map<Integer, TableUser> users = new ConcurrentHashMap<>();
    private final Map<Integer, TableUser> seatUsers = new ConcurrentHashMap<>();

    private TableState tableState = TableState.WAITING;
    private long stateStartTime;
    private int errorTimes;

    private final Operate op;
    private final Set<Integer> readySet = ConcurrentHashMap.newKeySet();
    private int currentRound = 1;
    private final GameResult gameResult;
    private ReplayRecorder replayRecorder;

    private static final int MAX_ERROR = 100;
    private static final long LOOP_INTERVAL = 500;
    private static final long IDLE_LOOP_INTERVAL = 2000;
    private static final AtomicInteger ROBOT_ID_SEQ = new AtomicInteger(-100000);
    private final AtomicLong snapshotVersion = new AtomicLong(1);
    private ScheduledFuture<?> loopFuture;
    private long currentLoopInterval = IDLE_LOOP_INTERVAL;
    /**
     * 本桌运行时托管（补机器人后开启，勿改共享 TableModel）
     */
    private boolean runtimeAutoPlay;
    private boolean closing;

    protected Table(long tableId, TableModel model, ModelProto.RoomRole creator) {
        this.tableId = tableId;
        this.creator = creator;
        this.tableModel = model;
        this.op = new Operate(this);
        this.gameResult = createGameResult();
        this.stateStartTime = System.currentTimeMillis();
        logger.info("创建桌子实例, tableId: {}, type: {}", tableId, model.getType());
    }

    public int getOwnerId() {
        return creator != null ? creator.getRoleId() : 0;
    }

    // ======================== 抽象方法(子类实现) ========================

    /**
     * 玩法类型: 1=麻将, 2=斗地主
     */
    public abstract int getGameType();

    /**
     * 发初始牌
     */
    public abstract void dealCards();

    /**
     * 重置游戏上下文(下一局时调用)
     */
    public abstract void resetGameContext();

    /**
     * 处理玩家操作(多态分发)
     */
    public abstract int processOp(int userId, GameProto.OpInfo op, net.client.Sender sender, long mapId, int sequence);

    /**
     * 创建玩法专属的GameResult
     */
    public abstract GameResult createGameResult();

    /**
     * 同步游戏状态给重连玩家
     */
    public abstract void syncGameState(TableUser user);

    /**
     * 在牌桌串行线程内构建当前玩家可见的只读完整快照。
     */
    public abstract GameProto.AckTableSnapshot buildTableSnapshot(TableUser viewer);

    /**
     * 初始化游戏配置(发牌前调用)
     */
    public abstract void initGameConfig();

    // ======================== 通用方法 ========================

    public long getTableId() {
        return tableId;
    }

    public TableModel getTableModel() {
        return tableModel;
    }

    public ModelProto.RoomRole getCreator() {
        return creator;
    }

    public int getRoomId() {
        return tableModel.getId();
    }

    public Map<Integer, TableUser> getUsers() {
        return users;
    }

    public TableState getTableState() {
        return tableState;
    }

    public long getStateStartTime() {
        return stateStartTime;
    }

    public Operate getOp() {
        return op;
    }

    public Map<Integer, TableUser> getSeatUsers() {
        return seatUsers;
    }

    public TableUser getSeatUser(int seat) {
        return seatUsers.get(seat);
    }

    public int nextSeat(int seat) {
        return ++seat >= tableModel.getSeatNum() ? 0 : seat;
    }

    public TableUser getNextSeatUser(int seat) {
        return seatUsers.get(nextSeat(seat));
    }

    public boolean sitFull() {
        return seatUsers.size() >= tableModel.getSeatNum();
    }

    public boolean isEmpty() {
        return users.isEmpty();
    }

    public boolean gaming() {
        return tableState != TableState.WAITING && tableState != TableState.TABLE_OVER
                && tableState != TableState.TABLE_DIS;
    }

    public boolean hasHumanPlayer() {
        for (TableUser u : users.values()) {
            if (!u.isRobot())
                return true;
        }
        return false;
    }

    public boolean hasOnlineHumanPlayer() {
        for (TableUser u : users.values()) {
            if (!u.isRobot() && u.isOnline())
                return true;
        }
        return false;
    }

    /** 网页心跳超时且没有其他在线真人时，本桌可以安全结束。 */
    public boolean hasExpiredWebPlayersOnly(long now, long timeoutMillis) {
        boolean expired = false;
        for (TableUser user : users.values()) {
            if (user.isRobot())
                continue;
            if (user.isWebHeartbeatExpired(now, timeoutMillis)) {
                expired = true;
            } else if (!user.isWebHeartbeatManaged() || user.isOnline()) {
                return false;
            }
        }
        return expired;
    }

    /** 原子标记桌子进入结束流程，避免相邻 tick 重复发送总结算。 */
    public boolean beginClosing() {
        if (closing)
            return false;
        closing = true;
        return true;
    }

    public boolean isAllRobot() {
        if (users.isEmpty())
            return false;
        for (TableUser u : users.values()) {
            if (!u.isRobot())
                return false;
        }
        return true;
    }

    public boolean isAutoPlayEnabled() {
        return runtimeAutoPlay || tableModel.getAutoPlay() != 0;
    }

    /**
     * 补齐空位为机器人；成功补位后打开本桌托管以便超时代打
     */
    public void fillRobotSeats() {
        int added = 0;
        while (!sitFull()) {
            int botId = ROBOT_ID_SEQ.decrementAndGet();
            if (botId >= 0) {
                ROBOT_ID_SEQ.set(-100000);
                botId = ROBOT_ID_SEQ.decrementAndGet();
            }
            TableUser bot = new TableUser(botId, "", randomBotName(), 0);
            bot.setRobot(true);
            int result = addUser(bot);
            if (result != ConstProto.Result.SUCCESS_VALUE) {
                logger.warn("补机器人失败, tableId: {}, result: {}", tableId, result);
                break;
            }
            added++;
        }
        if (added > 0) {
            runtimeAutoPlay = true;
            logger.info("桌子补机器人完成, tableId: {}, added: {}, seats: {}/{}",
                    tableId, added, seatUsers.size(), tableModel.getSeatNum());
            notifySeatPlayers();
        }
    }

    /**
     * 机器人昵称只使用随机英文字母，固定十位且不暴露机器人身份。
     */
    private static String randomBotName() {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder name = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            name.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
        }
        return name.toString();
    }

    /**
     * 机器人模板允许全机器人桌开局；普通桌仍保持原有保护逻辑。
     */
    public boolean isRobotRoom() {
        return RobotRoomTemplates.isRobotRoom(getRoomId())
                || (creator != null && creator.getRoleId() < 0);
    }

    /**
     * 推送当前座位名单（补机器人后刷新前端显示）
     */
    public void notifySeatPlayers() {
        GameProto.AckEnterTable.Builder response = GameProto.AckEnterTable.newBuilder();
        response.setTableInfo(buildTableInfo());
        for (TableUser tableUser : users.values()) {
            response.addPlayers(GameProto.Player.newBuilder()
                    .setPosition(tableUser.getSeated())
                    .setRoleId(tableUser.getUserId())
                    .setNickName(com.google.protobuf.ByteString.copyFromUtf8(
                            tableUser.getNick() == null ? "" : tableUser.getNick()))
                    .setAvatar(com.google.protobuf.ByteString.copyFromUtf8(
                            tableUser.getHead() == null ? "" : tableUser.getHead()))
                    .build());
        }
        sendTableMessage(response.build(), msg.registor.message.GMsg.ACK_ENTER_TABLE_MSG);
    }

    /** 构建所有入桌、离桌和座位推送共用的桌信息。 */
    public GameProto.TableInfo buildTableInfo() {
        return GameProto.TableInfo.newBuilder()
                .setTableId(tableId).setRoomId(getRoomId())
                .setCurrentRound(currentRound).setTotalRounds(tableModel.getTotalRounds())
                .build();
    }

    /** 构建带局数信息的统一状态通知，供开局、重连和解散链路复用。 */
    public GameProto.NotTableState buildStateNotification(int state, long start, int duration) {
        return GameProto.NotTableState.newBuilder()
                .setState(state).setStateStart(start).setStateDuration(duration)
                .setCurrentRound(currentRound).setTotalRounds(tableModel.getTotalRounds())
                .build();
    }

    // ======================== 状态转换 ========================

    public void upNextState(TableState next) {
        upNextStateWithTime(next, System.currentTimeMillis());
    }

    public void upNextState() {
        upNextStateWithTime(tableState.getNext(), System.currentTimeMillis());
    }

    public void upNextStateWithTime(TableState next, long now) {
        if (next == null)
            next = tableState.getNext();
        if (next == null) {
            logger.error("table:{} stat:{} update to nextState:null error", tableId, tableState);
            return;
        }
        logger.info("table:{} change state old:{} new:{}", tableId, this.tableState, next);
        tableState = next;
        stateStartTime = now;
        snapshotVersion.incrementAndGet();
        adjustLoopInterval(next);
    }

    private void adjustLoopInterval(TableState state) {
        if (state == TableState.TABLE_DIS)
            return;
        if (state == TableState.WAITING || state == TableState.ROUND_OVER || state == TableState.TABLE_OVER) {
            setLoopInterval(IDLE_LOOP_INTERVAL);
        } else {
            setLoopInterval(LOOP_INTERVAL);
        }
    }

    public void addErrorTime() {
        if (++errorTimes >= MAX_ERROR)
            upNextState(TableState.TABLE_OVER);
    }

    // ======================== 定时器 ========================

    /**
     * 将桌子内状态任务投递到桌串行队列（物理线程可与其他桌共享）。
     */
    public CompletableFuture<Void> execute(Runnable task) {
        return Game.getInstance().getThreadPoolManager().submitTable(tableId, task);
    }

    /**
     * 将带有返回值的计算任务投递到桌串行队列。
     *
     * @param <T>      返回值类型
     * @param supplier 具有返回值的计算逻辑
     * @return 异步执行凭据
     */
    public <T> CompletableFuture<T> execute(Supplier<T> supplier) {
        return Game.getInstance().getThreadPoolManager().submitTable(tableId, supplier);
    }

    /**
     * 将任务投递到桌串行队列，并内置操作名异常切面日志，避免调用方重复手写 .exceptionally。
     *
     * @param actionName 业务动作名称（用于排错定位）
     * @param task       待执行任务
     * @return 异步执行凭据
     */
    public CompletableFuture<Void> execute(String actionName, Runnable task) {
        return execute(task).exceptionally(error -> {
            logger.error("桌子处理 [{}] 异常, tableId: {}", actionName, tableId, error);
            return null;
        });
    }

    public void start() {
        try {
            if (loopFuture != null && !loopFuture.isCancelled())
                return;
            loopFuture = Game.getInstance().getThreadPoolManager().scheduleTable(
                    tableId, this::tableLoop, 1000, IDLE_LOOP_INTERVAL);
            currentLoopInterval = IDLE_LOOP_INTERVAL;
            logger.info("启动桌子逻辑循环, tableId: {}", tableId);
        } catch (Exception e) {
            logger.error("启动桌子逻辑循环失败, tableId: {}", tableId, e);
        }
    }

    /**
     * 停止桌子循环，避免删桌后仍触发 Waiting
     */
    public void stop() {
        try {
            if (loopFuture != null) {
                Game.getInstance().getThreadPoolManager().cancelTableSchedule(loopFuture);
                loopFuture = null;
                logger.info("停止桌子逻辑循环, tableId: {}", tableId);
            }
        } catch (Exception e) {
            logger.error("停止桌子逻辑循环失败, tableId: {}", tableId, e);
        }
    }

    public void setLoopInterval(long intervalMs) {
        if (currentLoopInterval == intervalMs)
            return;
        try {
            if (loopFuture != null) {
                Game.getInstance().getThreadPoolManager().cancelTableSchedule(loopFuture);
            }
            loopFuture = Game.getInstance().getThreadPoolManager().scheduleTable(
                    tableId, this::tableLoop, 0, intervalMs);
            currentLoopInterval = intervalMs;
        } catch (Exception e) {
            logger.error("调整循环间隔失败, tableId: {}", tableId, e);
        }
    }

    public void tableLoop() {
        try {
            TraceContext.setTableId(tableId);
            com.cloud.hub.framework.metrics.HubMetrics metrics = com.cloud.hub.framework.metrics.HubMetrics.getInstance();
            if (metrics != null) {
                metrics.recordTableLoop();
            }
            if (TableHeartbeatLifecycle.closeExpiredRobotRoom(this))
                return;
            TableStateHandleManager.handle(this);
        } catch (Exception e) {
            logger.error("桌子循环执行异常, tableId: {}", tableId, e);
        }
    }

    /**
     * 在本桌线程生成大厅需要的只读快照，避免并发读取座位状态。
     */
    public CompletableFuture<ModelProto.RoomTableInfo> getRoomTableInfoAsync() {
        return execute(this::buildRoomTableInfo);
    }

    private ModelProto.RoomTableInfo buildRoomTableInfo() {
        ModelProto.RoomTableInfo.Builder builder = ModelProto.RoomTableInfo.newBuilder()
                .setTableId(tableId).setRoomId(getRoomId()).setOwnerId(getOwnerId())
                .setCreatorId(getOwnerId()).setGameType(getGameType());
        for (TableUser user : seatUsers.values()) {
            if (user == null)
                continue;
            builder.addTableRoles(ModelProto.RoomRole.newBuilder().setRoleId(user.getUserId())
                    .setNickName(com.google.protobuf.ByteString.copyFromUtf8(user.getNick())).build());
        }
        return builder.build();
    }

    // ======================== 玩家管理 ========================

    public TableUser getUser(int userId, int gateId, GameProto.ReqEnterTable req) {
        TableUser tableUser = users.get(userId);
        if (tableUser == null) {
            // 先不写入 users：避免 addUser 前 sitFull 被预占导致第三人 TABLE_FULL
            tableUser = new TableUser(userId, req.getHead().toStringUtf8(), req.getNick().toStringUtf8(), gateId);
        }
        return tableUser;
    }

    public int addUser(TableUser user) {
        try {
            if (user == null)
                return ConstProto.Result.ROLE_NULL_VALUE;
            int prevSeat = user.getSeated();
            if (prevSeat >= 0 && seatUsers.get(prevSeat) == user) {
                users.put(user.getUserId(), user);
                return ConstProto.Result.SUCCESS_VALUE;
            }
            if (prevSeat >= 0)
                user.setSeated(-1);
            if (sitFull())
                return ConstProto.Result.TABLE_FULL_VALUE;
            if (isEmpty())
                start();
            int seat = occupySeat(user);
            if (seat == -1)
                return ConstProto.Result.TABLE_FULL_VALUE;
            users.put(user.getUserId(), user);
            logger.info("玩家加入桌子, userId: {}, tableId: {} seat:{}", user.getUserId(), tableId, seat);
            // 机器人模板的体验是进入即开局：第一个真人入座后立即补齐其余席位。
            // 递归补位时跳过机器人自身，避免重复触发。
            if (!user.isRobot() && isRobotRoom())
                fillRobotSeats();
            return ConstProto.Result.SUCCESS_VALUE;
        } catch (Exception e) {
            logger.error("添加玩家到桌子失败, userId: {}, tableId: {}", user.getUserId(), tableId, e);
            return ConstProto.Result.ROLE_ERROR_VALUE;
        }
    }

    private int occupySeat(TableUser user) {
        for (int index = 0; index < tableModel.getSeatNum(); index++) {
            if (!seatUsers.containsKey(index)) {
                seatUsers.put(index, user);
                user.setSeated(index);
                return index;
            }
        }
        return -1;
    }

    public void removeUser(TableUser user) {
        try {
            if (user == null)
                return;
            users.remove(user.getUserId());
            int seat = user.getSeated();
            if (seat >= 0)
                seatUsers.remove(seat);
            user.removeTable(tableId);
            user.setSeated(-1);
            logger.info("玩家离开桌子, userId: {}, tableId: {}", user.getUserId(), tableId);
        } catch (Exception e) {
            logger.error("从桌子移除玩家失败, userId: {}, tableId: {}", user.getUserId(), tableId, e);
        }
    }

    // ======================== 消息发送 ========================

    public void sendTableMessage(Message message, int messageId) {
        for (Map.Entry<Integer, TableUser> entry : seatUsers.entrySet()) {
            entry.getValue().sendRoleMessage(message, messageId, tableId);
        }
        logger.info("sendTableMessage:{} message:{}", tableId, message.toString());
    }

    public void sendTableMessageRaw(int messageId, byte[] payload) {
        for (Map.Entry<Integer, TableUser> entry : seatUsers.entrySet()) {
            entry.getValue().sendRoleMessageBytes(messageId, payload, tableId);
        }
    }

    // ======================== 多局 ========================

    public void clearReadySet() {
        readySet.clear();
    }

    public void addReady(int userId) {
        readySet.add(userId);
    }

    public boolean allReady() {
        return readySet.size() >= tableModel.getSeatNum();
    }

    public int getReadyCount() {
        return readySet.size();
    }

    public void resetForNextRound() {
        readySet.clear();
        currentRound++;
        op.reset();
        for (TableUser user : seatUsers.values())
            user.getCards().clear();
        resetGameContext(); // 子类实现: 重置MJ/DDZ上下文
        tableState = TableState.WAITING;
        stateStartTime = System.currentTimeMillis();
        logger.info("牌桌重置准备下一局, tableId: {}, round: {}", tableId, currentRound);
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public GameResult getGameResult() {
        return gameResult;
    }

    /**
     * 发送对局总结算通知（多局汇总）。
     * <p>
     * <b>多态化支持：</b>
     * 消除各玩法结算类中重复书写的多局总结算构建与广播逻辑。
     * 所有玩法共用 {@link GameResult} 模型与 {@link GameProto.NotGameResult} 协议格式；
     * 如个别特殊棋牌未来需要定制总结算广播格式，可直接在子类重写本方法。
     */
    public void sendGameResult() {
        if (gameResult == null) {
            return;
        }
        int seatNum = tableModel.getSeatNum();
        GameProto.NotGameResult.Builder builder = GameProto.NotGameResult.newBuilder()
                .setTotalRounds(gameResult.getTotalRounds())
                .setCompletedRounds(gameResult.getCompletedRounds());

        for (int i = 0; i < seatNum; i++) {
            builder.addTotalScores(GameProto.SeatScore.newBuilder()
                    .setSeat(i).setScore(gameResult.getTotalScore(i)).build());
        }

        for (GameResult.RoundEntry entry : gameResult.getRoundEntries()) {
            GameProto.RoundSummary.Builder summary = GameProto.RoundSummary.newBuilder()
                    .setRound(entry.getRound())
                    .setWinnerSeat(entry.getWinnerSeat())
                    .setFan(entry.getScore())
                    .setWinType(ByteString.copyFromUtf8(entry.getWinType() == null ? "" : entry.getWinType()));
            for (int i = 0; i < seatNum; i++) {
                summary.addSeatScores(GameProto.SeatScore.newBuilder()
                        .setSeat(i).setScore(entry.getScores()[i]).build());
            }
            builder.addRounds(summary.build());
        }

        sendTableMessage(builder.build(), GMsg.NOT_GAME_RESULT);
    }

    /**
     * 发送多局总结算（若当前为多局房间）并异步销毁移除牌桌。
     * <p>
     * <b>职责说明：</b>
     * 牌局生命周期终结（对局结束、中途离桌解散、心跳超时解散等）时的统一收尾入口，
     * 直接由桌子聚合根承载，避免外部冗余嵌套与单方法工具类。
     */
    public void dismissAndSettle() {
        if (isMultiRound()) {
            sendGameResult();
        }
        Game.getInstance().getTableManager().removeTableAsync(tableId);
    }

    protected GameProto.AckTableSnapshot.Builder newSnapshotBuilder(TableUser viewer) {
        GameProto.AckTableSnapshot.Builder b = GameProto.AckTableSnapshot.newBuilder()
                .setTableId(tableId).setGameType(getGameType())
                .setVersion(snapshotVersion.get()).setRound(currentRound)
                .setState(tableState.getId()).setStateStart(stateStartTime)
                .setStateDuration(tableState.getOverTime()).setOpSeat(op.getCurrOpSeat());
        Set<GameProto.OpInfo> choices = op.getSeatOps(viewer.getSeated());
        if (choices != null)
            b.addAllChoices(choices);
        for (TableUser u : seatUsers.values()) {
            GameProto.SnapshotPlayer.Builder p = GameProto.SnapshotPlayer.newBuilder()
                    .setRoleId(u.getUserId()).setSeat(u.getSeated())
                    .setNick(com.google.protobuf.ByteString.copyFromUtf8(
                            u.getNick() == null ? "" : u.getNick()))
                    .setOnline(u.isOnline()).setCardCount(u.getCards().size())
                    .setTotalScore(gameResult.getTotalScore(u.getSeated()));
            if (u == viewer) {
                for (Card card : u.getCards())
                    p.addCards(card.getId());
            }
            b.addPlayers(p);
        }
        return b;
    }

    public boolean isLastRound() {
        return currentRound >= tableModel.getTotalRounds();
    }

    public boolean isMultiRound() {
        return tableModel.getTotalRounds() > 1;
    }

    public ReplayRecorder getReplayRecorder() {
        return replayRecorder;
    }

    public void setReplayRecorder(ReplayRecorder replayRecorder) {
        this.replayRecorder = replayRecorder;
    }

    public abstract ReplayRecorder createReplayRecorder();

    // ======================== 状态机多态钩子（各游戏玩法特化重写） ========================

    /** 抢地主/叫分阶段通知，默认空实现 */
    public boolean onRobTiming() {
        return false;
    }

    /**
     * 叫分/抢庄阶段每 tick 检查。
     * <p>
     * 检查当前操作者是否为机器人且达到延迟时限，若超时则自动触发 {@link #onRobOverTime()}。
     *
     * @return true 表示已被机器人逻辑处理，外层状态无需再执行通用超时逻辑
     */
    public boolean onIdleRobHandle() {
        int seat = op.getCurrOpSeat();
        TableUser u = getSeatUser(seat);
        if (u != null && u.isRobot()
                && System.currentTimeMillis() >= stateStartTime + RobotOperationDelay.randomMillis()) {
            onRobOverTime();
            return true;
        }
        return false;
    }

    /**
     * 叫分/抢庄操作超时回调。
     * <p>
     * 子类重写以实现具体的超时自动叫分/弃权/不叫逻辑（如斗地主、拖拉机亮主超时）。
     */
    public void onRobOverTime() {
    }

    /**
     * 出牌阶段定时通知钩子。
     *
     * @return true 表示本 tick 已处理，false 走通用流程
     */
    public boolean onCardTiming() {
        return false;
    }

    /**
     * 出牌阶段机器人操作延迟毫秒数。
     *
     * @return 机器人出牌延迟时间（毫秒）
     */
    public long getRobotCardDelay() {
        return RobotOperationDelay.randomMillis();
    }

    /**
     * 开局动画/发牌阶段定时处理钩子。
     * <p>
     * 供特殊玩法（如拖拉机动画倒计时与逐张发牌逻辑）定制，避免外层 Handle 中硬编码
     * {@code table instanceof TractorTable}。
     *
     * @return true 表示已被玩法子类接管处理，false 走默认状态流转
     */
    public boolean onStartAniTiming() {
        return false;
    }

    /**
     * 亮牌/扣底阶段每 tick 处理钩子。
     * <p>
     * 供有扣底玩法的桌子（如拖拉机庄家扣底时限检测）多态定制，避免外层 Handle 散落业务类型判断。
     *
     * @return true 表示已拦截处理，false 表示走通用状态流转
     */
    public boolean onIdleShowCardHandle() {
        return false;
    }

    /**
     * 亮牌/扣底阶段超时处理钩子。
     * <p>
     * 默认直接流转到出牌阶段；子类可重写以实现自动扣底逻辑。
     */
    public void onIdleShowCardOverTime() {
        upNextState(TableState.CARD);
    }

    /**
     * 获取洗牌/发牌完毕后的开局初始目标状态。
     * <p>
     * 替代 {@code Waiting} 中的 switch-case，不同棋牌子类多态自定初始状态（例如斗地主为 ROB，拖拉机为 START_ANI）。
     *
     * @return 下一个目标桌状态
     */
    public TableState getInitialStartState() {
        return TableState.START_ANI;
    }

    /**
     * 发牌完毕后特定玩法的开局初始化钩子。
     * <p>
     * 负责定庄、翻赖子、设置首出座位等玩法特有逻辑，由各桌子自理。
     */
    public void onGameStarted() {
        op.setCurrOpSeat(0);
    }

    /**
     * 是否在开局阶段记录初始手牌到录像文件中。
     *
     * @return true 表示记录；拖拉机等逐张发牌玩法返回 false 避免手牌记录不完整
     */
    public boolean shouldRecordInitHands() {
        return true;
    }

    /**
     * 获取游戏展示名称（用于对局录像或调试日志）。
     *
     * @return 游戏展示名，如 "斗地主"、"跑得快"、"拖拉机"
     */
    public String getGameDisplayName() {
        return "游戏";
    }

    /**
     * 录像头初始化时的专有元数据写入钩子。
     *
     * @param replay 对局回放记录器
     */
    public void onInitReplayHeader(ReplayRecorder replay) {
    }

    /**
     * 出牌等待超时自动代出/过牌处理钩子。
     */
    public void onCardOverTime() {
    }

    @Override
    public String toString() {
        return String.format("Table{tableId='%d', type=%d}", tableId, getGameType());
    }
}
