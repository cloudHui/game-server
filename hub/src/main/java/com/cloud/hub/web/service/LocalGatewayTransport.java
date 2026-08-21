package com.cloud.hub.web.service;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.client.handle.role.ReqEnterTableHandle;
import com.cloud.hub.game.client.handle.role.ReqLeaveTableHandle;
import com.cloud.hub.game.client.handle.role.ReqOpHandle;
import com.cloud.hub.game.client.handle.role.ReqTableHeartbeatHandle;
import com.cloud.hub.game.client.handle.role.ReqTableSnapshotHandle;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.runtime.GamePushBus;
import com.cloud.hub.lobby.manager.User;
import com.cloud.hub.lobby.manager.UserManager;
import com.cloud.hub.lobby.manager.table.TableInfo;
import com.cloud.hub.lobby.manager.table.TableManager;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import model.tablemodel.TableModel;
import model.tablemodel.TableModelJson;
import msg.registor.message.GMsg;
import msg.registor.message.LMsg;
import net.client.Sender;
import net.handler.Handler;
import net.message.TCPMessage;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import proto.ConstProto;
import proto.LobbyProto;
import proto.ModelProto;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Hub 内置网关。保留原消息协议边界，但请求不再经过 Gate TCP 服务。
 */
@Primary
@Component
public final class LocalGatewayTransport implements GatewayTransport {
    private final Map<String, Integer> sessionUsers = new ConcurrentHashMap<>();
    private final Map<Integer, String> userSessions = new ConcurrentHashMap<>();
    private final Map<Integer, Long> activeTables = new ConcurrentHashMap<>();
    private volatile BiConsumer<String, TCPMessage> pushListener;

    public LocalGatewayTransport() {
        GamePushBus.install(this::forwardPush);
    }

    @Override
    public void bind(String sessionId, int userId, String username, String nickname) {
        String old = userSessions.put(userId, sessionId);
        if (old != null && !old.equals(sessionId)) sessionUsers.remove(old);
        sessionUsers.put(sessionId, userId);
        UserManager.getInstance().putOrUpdate(new User(userId, username, nickname, 0));
    }

    @Override
    public void removeConnection(String sessionId) {
        Integer userId = sessionUsers.remove(sessionId);
        if (userId != null) {
            userSessions.remove(userId, sessionId);
            UserManager.getInstance().removeUser(userId);
        }
    }

    @Override
    public CompletableFuture<Message> sendAndWait(String sessionId, int msgId, Message message, int timeoutSeconds) {
        Integer userId = requireUser(sessionId);
        if (userId == null) return failed("会话不存在");
        if (msgId == LMsg.REQ_ROOM_LIST_MSG) {
            return CompletableFuture.completedFuture(TableManager.getInstance().getAllRoomTable());
        }
        if (msgId == LMsg.REQ_JOIN_ROOM_TABLE_MSG) {
            return join(userId, (LobbyProto.ReqJoinRoomTable) message).thenApply(value -> value);
        }
        LocalSender sender = new LocalSender();
        Handler handler = handler(msgId);
        if (handler == null) return failed("服务不支持消息: " + msgId);
        long tableId = tableId(userId, msgId, message);
        try {
            handler.handler(sender, userId, message, tableId, 1);
            if (msgId == GMsg.REQ_ENTER_TABLE_MSG) {
                activeTables.put(userId, ((proto.GameProto.ReqEnterTable) message).getTableId());
            }
            if (msgId == GMsg.REQ_LEAVE) sender.message.whenComplete((ok, error) -> activeTables.remove(userId));
            return sender.message;
        } catch (Exception error) {
            return failed(error.getMessage());
        }
    }

    @Override
    public CompletableFuture<TCPMessage> sendAndWaitTcp(String sessionId, int msgId, Message message,
                                                         int timeoutSeconds) {
        Integer userId = requireUser(sessionId);
        if (userId == null) return failedTcp("会话不存在");
        Handler handler = handler(msgId);
        if (handler == null) return failedTcp("服务不支持消息: " + msgId);
        LocalSender sender = new LocalSender();
        try {
            handler.handler(sender, userId, message, tableId(userId, msgId, message), 1);
            return sender.tcp;
        } catch (Exception error) {
            return failedTcp(error.getMessage());
        }
    }

    @Override
    public void send(String sessionId, int msgId, Message message) {
        Integer userId = requireUser(sessionId);
        Handler handler = handler(msgId);
        if (userId == null || handler == null) return;
        handler.handler(new LocalSender(), userId, message, tableId(userId, msgId, message), 0);
    }

    @Override public boolean isAuthenticated(String sessionId) { return sessionUsers.containsKey(sessionId); }
    @Override public void markAuthenticated(String sessionId) { }
    @Override public void setPushListener(BiConsumer<String, TCPMessage> listener) { pushListener = listener; }

    private CompletableFuture<LobbyProto.AckJoinRoomTable> join(int userId, LobbyProto.ReqJoinRoomTable request) {
        TableManager lobby = TableManager.getInstance();
        User user = UserManager.getInstance().getUser(userId);
        TableModel model = lobby.getTableModel(request.getRoomId());
        if (user == null || model == null) return failedJoin("用户或房间不存在");
        TableInfo available = lobby.getCanJoinTable(request.getRoomId());
        if (available != null && Game.getInstance().getTableManager().getTable(available.getTableId()) != null) {
            available.joinRole(user);
            activeTables.put(userId, available.getTableId());
            return CompletableFuture.completedFuture(joinAck(available.getTableId()));
        }
        ModelProto.RoomRole.Builder role = ModelProto.RoomRole.newBuilder().setRoleId(userId)
                .setNickName(ByteString.copyFromUtf8(user.getNick() == null ? user.getUsername() : user.getNick()));
        String json = TableModelJson.toJson(model);
        if (json != null && !json.isEmpty()) {
            role.setAvatar(ByteString.copyFromUtf8("TMJSON:" + json));
        }
        ModelProto.RoomRole created = role.build();
        return Game.getInstance().getTableManager().createTableAsync(request.getRoomId(), created)
                .thenApply(table -> {
                    ModelProto.RoomTableInfo info = ModelProto.RoomTableInfo.newBuilder()
                            .setTableId(table.getTableId()).setRoomId(request.getRoomId())
                            .setCreatorId(userId).setOwnerId(userId).setGameType(model.getType())
                            .addTableRoles(created).build();
                    TableInfo lobbyTable = lobby.putRoomInfo(info);
                    lobbyTable.joinRole(user);
                    activeTables.put(userId, table.getTableId());
                    return joinAck(table.getTableId());
                });
    }

    private static LobbyProto.AckJoinRoomTable joinAck(long tableId) {
        return LobbyProto.AckJoinRoomTable.newBuilder().setTableId(tableId).build();
    }

    private long tableId(int userId, int msgId, Message message) {
        if (msgId == GMsg.REQ_ENTER_TABLE_MSG) return ((proto.GameProto.ReqEnterTable) message).getTableId();
        if (msgId == GMsg.REQ_TABLE_SNAPSHOT) return ((proto.GameProto.ReqTableSnapshot) message).getTableId();
        if (msgId == GMsg.REQ_TABLE_HEARTBEAT) return ((proto.GameProto.ReqTableHeartbeat) message).getTableId();
        return activeTables.getOrDefault(userId, 0L);
    }

    private static Handler handler(int msgId) {
        if (msgId == GMsg.REQ_ENTER_TABLE_MSG) return new ReqEnterTableHandle();
        if (msgId == GMsg.REQ_TABLE_SNAPSHOT) return new ReqTableSnapshotHandle();
        if (msgId == GMsg.REQ_OP) return new ReqOpHandle();
        if (msgId == GMsg.REQ_LEAVE) return new ReqLeaveTableHandle();
        if (msgId == GMsg.REQ_TABLE_HEARTBEAT) return new ReqTableHeartbeatHandle();
        return null;
    }

    private Integer requireUser(String sessionId) { return sessionUsers.get(sessionId); }

    private void forwardPush(int userId, TCPMessage message) {
        String sessionId = userSessions.get(userId);
        BiConsumer<String, TCPMessage> listener = pushListener;
        if (sessionId != null && listener != null) listener.accept(sessionId, message);
    }

    @PreDestroy
    public void shutdown() {
        GamePushBus.clear();
        sessionUsers.clear();
        userSessions.clear();
        activeTables.clear();
    }

    private static <T> CompletableFuture<T> failed(String reason) {
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(new IllegalStateException(reason));
        return result;
    }
    private static CompletableFuture<TCPMessage> failedTcp(String reason) { return failed(reason); }
    private static CompletableFuture<LobbyProto.AckJoinRoomTable> failedJoin(String reason) { return failed(reason); }

    private static final class LocalSender implements Sender {
        private final CompletableFuture<Message> message = new CompletableFuture<>();
        private final CompletableFuture<TCPMessage> tcp = new CompletableFuture<>();

        @Override public void sendMessage(int msgId, Message msg, int sequence) { complete(msgId, 0, 0, msg, sequence); }
        @Override public void sendMessage(TCPMessage msg) {
            tcp.complete(msg);
            if (msg.getMessage() == null || msg.getMessage().length == 0) {
                message.completeExceptionally(new IllegalStateException("业务失败: " + msg.getResult()));
            }
        }
        @Override public void sendMessage(int clientId, int msgId, long mapId, Message msg, int sequence) {
            complete(msgId, clientId, mapId, msg, sequence);
        }
        private void complete(int msgId, int clientId, long mapId, Message body, int sequence) {
            if (sequence == 0) return;
            TCPMessage packet = TCPMessage.newInstance(ConstProto.Result.SUCCESS_VALUE, msgId, clientId,
                    body.toByteArray(), mapId, sequence);
            message.complete(body);
            tcp.complete(packet);
        }
        @Override public CompletableFuture<TCPMessage> sendMessageBackTcp(Message msg, int msgId, int timeout) {
            return failedTcp("本地处理器不支持反向请求");
        }
        @Override public CompletableFuture<TCPMessage> sendTcpMessage(TCPMessage msg, int timeout) {
            return failedTcp("本地处理器不支持反向请求");
        }
    }
}
