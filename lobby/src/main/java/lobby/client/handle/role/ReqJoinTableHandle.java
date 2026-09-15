package lobby.client.handle.role;

import com.google.protobuf.ByteString;
import lobby.Lobby;
import lobby.client.ClientProto;
import lobby.manager.User;
import lobby.manager.UserManager;
import lobby.manager.table.TableInfo;
import lobby.manager.table.TableManager;
import model.tablemodel.TableModel;
import model.tablemodel.TableModelJson;
import msg.registor.enums.ServerType;
import msg.registor.message.LMsg;
import msg.registor.message.SMsg;
import net.client.Sender;
import net.client.handler.ClientHandler;
import net.connect.handle.ConnectHandler;
import net.message.TCPMessage;
import net.msg.Msg;
import net.msg.MsgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.LobbyProto;
import proto.ModelProto;
import proto.ServerProto;
import tools.manager.HandleManager;

/**
 * 玩家加入或匹配桌子请求处理器
 * <p>
 * 寻找可加入的空闲桌子；若无可用桌子则向游戏服务异步请求创建新桌。
 */
public class ReqJoinTableHandle {

    private static final Logger logger = LoggerFactory.getLogger(ReqJoinTableHandle.class);

    /**
     * 处理玩家加入桌子请求
     *
     * @param ctx 消息上下文
     */
    @Msg(id = LMsg.REQ_JOIN_ROOM_TABLE_MSG, desc = "玩家加入桌子请求")
    public void handle(MsgContext<LobbyProto.ReqJoinRoomTable> ctx) {
        int clientId = ctx.getClientId();
        Sender sender = ctx.getSender();
        int sequence = ctx.getSequence();

        User user = UserManager.getInstance().getUser(clientId);
        if (user == null) {
            logger.error("用户不存在, userId: {}", clientId);
            sender.sendMessage(TCPMessage.newInstance(ConstProto.Result.SERVER_ERROR_VALUE));
            return;
        }

        LobbyProto.ReqJoinRoomTable request = ctx.getMsg();
        int roomId = request.getRoomId();
        TableModel tableModel = TableManager.getInstance().getTableModel(roomId);
        if (tableModel == null) {
            logger.error("房间配置不存在, roomId: {}", roomId);
            sender.sendMessage(TCPMessage.newInstance(ConstProto.Result.TABLE_CONFIG_ERROR_VALUE));
            return;
        }

        if (!joinTable(tableModel, user, sequence, sender)) {
            ConnectHandler gameServer = Lobby.getInstance().getServerManager().getServerClient(ServerType.Game);
            if (gameServer == null) {
                logger.error("游戏服务器不可用");
                sender.sendMessage(TCPMessage.newInstance(ConstProto.Result.SERVER_NULL_VALUE));
                return;
            }
            createTable(gameServer, roomId, sequence, clientId, sender);
        }
    }

    /**
     * 尝试加入已有桌子
     *
     * @param tableModel 房间配置
     * @param user       用户对象
     * @param sequence   请求序列号
     * @param replyTo    回复发送端
     * @return 是否成功加入
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
     * 请求游戏服创建新桌子
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
     * 构造创建桌子请求
     */
    private ServerProto.ReqCreateGameTable buildCreateTableRequest(int roomId, int userId) {
        ModelProto.RoomRole.Builder role = ModelProto.RoomRole.newBuilder().setRoleId(userId);
        TableModel model = TableManager.getInstance().getTableModel(roomId);
        if (model != null && model.getId() >= 10000) {
            role.setAvatar(ByteString.copyFromUtf8(
                    "TMJSON:" + TableModelJson.toJson(model)));
        }
        return ServerProto.ReqCreateGameTable.newBuilder()
                .setRoomId(roomId)
                .setRoomRole(role.build())
                .build();
    }

    /**
     * 发送加入桌子成功应答（默认网关）
     *
     * @param tableId  桌子 ID
     * @param sequence 序列号
     * @param user     玩家对象
     */
    public static void sendJoinTableAck(long tableId, int sequence, User user) {
        sendJoinTableAck(tableId, sequence, user, null);
    }

    /**
     * 发送加入桌子成功应答（优先走请求连接）
     *
     * @param tableId  桌子 ID
     * @param sequence 序列号
     * @param user     玩家对象
     * @param replyTo  原请求链路 Sender
     */
    public static void sendJoinTableAck(long tableId, int sequence, User user, Sender replyTo) {
        LobbyProto.AckJoinRoomTable ack = LobbyProto.AckJoinRoomTable.newBuilder()
                .setTableId(tableId)
                .build();
        // 优先走原请求连接，保证 Gate 侧 completer 能匹配到 sequence
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
