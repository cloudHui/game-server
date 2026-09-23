package com.cloud.hub.lobby.client.handle.role;

import com.google.protobuf.Message;
import com.cloud.hub.lobby.Lobby;
import com.cloud.hub.lobby.client.ClientProto;
import com.cloud.hub.lobby.manager.User;
import com.cloud.hub.lobby.manager.UserManager;
import com.cloud.hub.lobby.manager.table.TableInfo;
import com.cloud.hub.lobby.manager.table.TableManager;
import model.tablemodel.TableModel;
import model.tablemodel.TableModelJson;
import utils.registry.annotation.ProcessType;
import utils.registry.enums.ServerType;
import msg.registor.message.LMsg;
import msg.registor.message.SMsg;
import net.client.Sender;
import net.client.handler.ClientHandler;
import net.connect.handle.ConnectHandler;
import net.handler.Handler;
import net.message.TCPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.LobbyProto;
import proto.ModelProto;
import proto.ServerProto;
import tools.manager.HandleManager;

/**
 * 处理玩家请求加入房间桌子的处理器。
 *
 * <p><b>核心业务流程：</b>
 * 1. 校验用户在大厅的在线状态与合法性；
 * 2. 校验目标房间配置（{@link TableModel}）是否存在；
 * 3. 尝试在现有大厅桌子中寻找可加入的空闲席位（{@code joinTable}）；
 * 4. 若无可用桌子，则向游戏服务（GameServer）发起创桌请求（{@code createTable}），并将原始请求链路记录在
 *    {@link PendingCreateJoin} 中以便异步回调时准确回复。
 */
@ProcessType(LMsg.REQ_JOIN_ROOM_TABLE_MSG)
public class ReqJoinTableHandle implements Handler {

    /**
     * 日志记录器
     */
    private static final Logger logger = LoggerFactory.getLogger(ReqJoinTableHandle.class);

    @Override
    public boolean handler(Sender sender, int clientId, Message msg, long mapId, int sequence) {
        User user = UserManager.getInstance().getUser(clientId);
        if (user == null) {
            logger.error("加入桌子失败，用户不存在或已离线, userId: {}", clientId);
            replyError(sender, clientId, sequence, ConstProto.Result.SERVER_ERROR_VALUE);
            return true;
        }

        LobbyProto.ReqJoinRoomTable request = (LobbyProto.ReqJoinRoomTable) msg;
        int roomId = request.getRoomId();
        TableModel tableModel = TableManager.getInstance().getTableModel(roomId);
        if (tableModel == null) {
            logger.error("加入桌子失败，房间配置不存在, roomId: {}, userId: {}", roomId, clientId);
            replyError(sender, clientId, sequence, ConstProto.Result.TABLE_CONFIG_ERROR_VALUE);
            return true;
        }

        // 先尝试加入已有空位桌子
        if (!joinTable(tableModel, user, sequence, sender)) {
            ConnectHandler gameServer = Lobby.getInstance().getServerManager().getServerClient(ServerType.Game);
            if (gameServer == null) {
                logger.error("游戏服务器不可用，无法自动创建新桌, roomId: {}, userId: {}", roomId, clientId);
                replyError(sender, clientId, sequence, ConstProto.Result.SERVER_NULL_VALUE);
                return true;
            }
            createTable(gameServer, roomId, sequence, clientId, sender);
        }
        return true;
    }

    /**
     * 统一返回错误码报文，附带 clientId 与 sequence 避免网关悬挂。
     *
     * @param sender   消息发送者
     * @param clientId 玩家 ID
     * @param sequence 协议序列号
     * @param result   错误码枚举值
     */
    private void replyError(Sender sender, int clientId, int sequence, int result) {
        TCPMessage errMsg = TCPMessage.newInstance(result);
        errMsg.setClientId(clientId);
        errMsg.setSequence(sequence);
        sender.sendMessage(errMsg);
    }

    /**
     * 尝试加入已有的空闲席位桌子。
     *
     * @param tableModel 房间玩法模型
     * @param user       加入玩家
     * @param sequence   协议序号
     * @param replyTo    发起端连接
     * @return true 若找到空桌并成功加入，false 若无可用桌子需要新建
     */
    private boolean joinTable(TableModel tableModel, User user, int sequence, Sender replyTo) {
        TableInfo canJoinTable = TableManager.getInstance().getCanJoinTable(tableModel.getId());
        if (canJoinTable == null) {
            return false;
        }
        canJoinTable.joinRole(user);
        sendJoinTableAck(canJoinTable.getTableId(), sequence, user, replyTo);
        return true;
    }

    /**
     * 向 Game 服发送创桌请求，并将当前连接会话存入 PendingCreateJoin。
     *
     * @param gameServer 目标游戏服连接
     * @param roomId     房间模版 ID
     * @param sequence   协议序号
     * @param userId     创桌用户 ID
     * @param replyTo    网关发起连接
     */
    private void createTable(ConnectHandler gameServer, int roomId, int sequence, int userId, Sender replyTo) {
        PendingCreateJoin.put(userId, sequence, replyTo);
        HandleManager.sendMsg(
                SMsg.REQ_CREATE_TABLE_MSG,
                buildCreateTableRequest(roomId, userId),
                gameServer,
                ClientProto.PARSER,
                sequence,
                userId,
                true
        );
    }

    /**
     * 组装发往 Game 服的创桌 Protobuf 报文。
     *
     * @param roomId 房间模版 ID
     * @param userId 创桌发起人 ID
     * @return 创桌请求报文
     */
    private ServerProto.ReqCreateGameTable buildCreateTableRequest(int roomId, int userId) {
        ModelProto.RoomRole.Builder role = ModelProto.RoomRole.newBuilder().setRoleId(userId);
        TableModel model = TableManager.getInstance().getTableModel(roomId);
        if (model != null && model.getId() >= 10000) {
            role.setAvatar(com.google.protobuf.ByteString.copyFromUtf8(
                    "TMJSON:" + TableModelJson.toJson(model)));
        }
        return ServerProto.ReqCreateGameTable.newBuilder()
                .setRoomId(roomId)
                .setRoomRole(role.build())
                .build();
    }

    /**
     * 发送加桌成功响应（无指定连接时降级走当前绑定的 Gate）。
     *
     * @param tableId  桌号
     * @param sequence 消息序号
     * @param user     玩家对象
     */
    public static void sendJoinTableAck(long tableId, int sequence, User user) {
        sendJoinTableAck(tableId, sequence, user, null);
    }

    /**
     * 发送加桌成功响应。
     *
     * @param tableId  桌号
     * @param sequence 消息序号
     * @param user     玩家对象
     * @param replyTo  发起端网络连接（若为 null 则通过 Gate 管理器路由）
     */
    public static void sendJoinTableAck(long tableId, int sequence, User user, Sender replyTo) {
        LobbyProto.AckJoinRoomTable ack = LobbyProto.AckJoinRoomTable.newBuilder()
                .setTableId(tableId)
                .build();
        // 优先走原请求连接，保证 Gate 侧 completer 能匹配到 sequence。
        if (replyTo != null) {
            replyTo.sendMessage(LMsg.ACK_JOIN_ROOM_TABLE_MSG, ack, sequence);
            logger.info("玩家加入桌子成功, userId: {}, tableId: {}, via: request-sender", user.getUserId(), tableId);
            return;
        }
        ClientHandler gate = Lobby.getInstance().getServerClientManager()
                .getServerClient(ServerType.Gate, user.getClientId());
        if (gate == null) {
            gate = Lobby.getInstance().getServerClientManager().getServerClient(ServerType.Gate);
        }
        if (gate == null) {
            logger.error("sendJoinTableAck role:{} gate null", user.getUserId());
            return;
        }
        gate.sendMessage(LMsg.ACK_JOIN_ROOM_TABLE_MSG, ack, sequence);
        logger.info("玩家加入桌子成功, userId: {}, tableId: {}, via: fallback-gate", user.getUserId(), tableId);
    }
}
