package game.client.handle.role;

import com.google.protobuf.ByteString;
import game.Game;
import game.manager.TableManager;
import game.manager.table.Table;
import game.manager.table.TableUser;
import msg.registor.message.GMsg;
import net.client.Sender;
import net.client.handler.ClientHandler;
import net.message.TCPMessage;
import net.msg.Msg;
import net.msg.MsgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;

/**
 * 玩家请求进入桌子处理器
 * <p>
 * 负责玩家入桌或断线重连逻辑的校验与分发。
 */
public class ReqEnterTableHandle {

    private static final Logger logger = LoggerFactory.getLogger(ReqEnterTableHandle.class);

    /**
     * 处理玩家进入桌子请求
     *
     * @param ctx 消息上下文，包含发送者、客户端ID、序列号等元数据
     */
    @Msg(id = GMsg.REQ_ENTER_TABLE_MSG, desc = "请求进入桌子")
    public void handle(MsgContext<GameProto.ReqEnterTable> ctx) {
        try {
            GameProto.ReqEnterTable request = ctx.getMsg();
            int clientId = ctx.getClientId();
            Sender sender = ctx.getSender();
            long tableId = request.getTableId();

            logger.info("处理进入桌子请求, userId: {}, tableId: {}", clientId, tableId);

            TableManager tableManager = Game.getInstance().getTableManager();
            Table table = tableManager.getTable(tableId);
            if (table == null) {
                logger.warn("桌子不存在, tableId: {}", tableId);
                sender.sendMessage(TCPMessage.newInstance(ConstProto.Result.TABLE_NULL_VALUE));
                return;
            }

            // gateId 用 Gate→Game 连接 id，便于后续推送找对网关
            final int gateConnId = (sender instanceof ClientHandler) ? ((ClientHandler) sender).getId() : 0;
            table.execute(() -> {
                int result = processEnterTable(clientId, tableId, gateConnId, request, table);
                if (result == ConstProto.Result.SUCCESS_VALUE) {
                    GameProto.AckEnterTable response = buildEnterTableResponse(table);
                    ctx.reply(GMsg.ACK_ENTER_TABLE_MSG, tableId, response);
                } else {
                    // 必须带原 sequence，否则 web sendAndWait 会一直等到超时
                    GameProto.AckEnterTable empty = GameProto.AckEnterTable.newBuilder().build();
                    ctx.reply(GMsg.ACK_ENTER_TABLE_MSG, tableId, empty);
                    logger.warn("进入桌子失败, userId: {}, tableId: {}, result: {}", clientId, tableId, result);
                }
                logger.info("进入桌子请求处理完成, userId: {}, tableId: {}, success: {}, gateConnId: {}",
                        clientId, tableId, result, gateConnId);
            }).exceptionally(error -> {
                logger.error("桌子线程处理进入请求失败, tableId: {}", tableId, error);
                return null;
            });
        } catch (Exception e) {
            logger.error("处理进入桌子请求失败, userId: {}", ctx.getMapId(), e);
        }
    }

    /**
     * 进入桌子逻辑处理
     *
     * @param userId  玩家 ID
     * @param tableId 桌子 ID
     * @param gateId  网关连接 ID
     * @param req     请求数据
     * @param table   桌子实例
     * @return 错误码（0 为成功）
     */
    private int processEnterTable(int userId, long tableId, int gateId, GameProto.ReqEnterTable req, Table table) {
        try {
            // 游戏中: 检查断线重连
            if (table.gaming()) {
                TableUser existingUser = table.getUsers().get(userId);
                if (existingUser != null) {
                    logger.info("玩家断线重连, userId: {}, tableId: {}, gateId: {} -> {}",
                            userId, tableId, existingUser.getGateId(), gateId);
                    existingUser.setOnLine(true);
                    existingUser.setGateId(gateId);
                    table.syncGameState(existingUser);
                    return ConstProto.Result.SUCCESS_VALUE;
                }
                return ConstProto.Result.TABLE_START_VALUE;
            }

            TableUser user = table.getUser(userId, gateId, req);
            int result = table.addUser(user);
            if (result == ConstProto.Result.SUCCESS_VALUE) {
                user.addTable(tableId);
                return result;
            }
            table.getUsers().remove(userId);
            return result;
        } catch (Exception e) {
            logger.error("处理进入桌子逻辑失败, userId: {}, tableId: {}", userId, tableId, e);
            return ConstProto.Result.TABLE_ERROR_VALUE;
        }
    }

    /**
     * 构建进入桌子成功响应
     *
     * @param table 桌子实例
     * @return AckEnterTable 响应对象
     */
    private GameProto.AckEnterTable buildEnterTableResponse(Table table) {
        GameProto.AckEnterTable.Builder response = GameProto.AckEnterTable.newBuilder();
        response.setTableInfo(table.buildTableInfo());
        for (TableUser tableUser : table.getUsers().values()) {
            response.addPlayers(GameProto.Player.newBuilder()
                    .setPosition(tableUser.getSeated())
                    .setRoleId(tableUser.getUserId())
                    .setNickName(ByteString.copyFromUtf8(tableUser.getNick()))
                    .setAvatar(ByteString.copyFromUtf8(tableUser.getHead()))
                    .build());
        }
        return response.build();
    }
}
