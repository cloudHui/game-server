package com.cloud.hub.game.client.handle.role;

import com.cloud.hub.game.client.handle.TableHandlerHelper;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import utils.registry.annotation.ProcessType;
import msg.registor.message.GMsg;
import net.client.Sender;
import net.client.handler.ClientHandler;
import net.handler.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;

/**
 * 处理玩家请求进入牌桌处理器。
 * <p>
 * 统一接入 {@link TableHandlerHelper#dispatchOrReplyNull} 进行调度：
 * 自动完成查桌、判空向客户端回发 TABLE_NULL_VALUE，并在桌串行队列排他执行入桌或断线重连逻辑。
 */
@ProcessType(GMsg.REQ_ENTER_TABLE_MSG)
public class ReqEnterTableHandle implements Handler {

    /**
     * 日志记录器。
     */
    private static final Logger logger = LoggerFactory.getLogger(ReqEnterTableHandle.class);

    /**
     * 接收客户端进入桌子请求。
     *
     * @param sender   发送方句柄
     * @param clientId 玩家 ID
     * @param message  ReqEnterTable 协议体
     * @param mapId    桌号
     * @param sequence 消息序列号
     * @return 恒为 true
     */
    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        GameProto.ReqEnterTable request = (GameProto.ReqEnterTable) message;
        long tableId = request.getTableId() != 0 ? request.getTableId() : mapId;
        logger.info("收到进入桌子请求, userId: {}, tableId: {}", clientId, tableId);

        // gateId 用 Gate→Game 连接 id，便于后续推送找对网关
        final int gateConnId = (sender instanceof ClientHandler) ? ((ClientHandler) sender).getId() : 0;

        return TableHandlerHelper.dispatchOrReplyNull(sender, tableId, "进入桌子", table -> {
            int result = processEnterTable(clientId, tableId, gateConnId, request, table);
            if (result == ConstProto.Result.SUCCESS_VALUE) {
                GameProto.AckEnterTable response = buildEnterTableResponse(table);
                sender.sendMessage(clientId, GMsg.ACK_ENTER_TABLE_MSG, tableId, response, sequence);
            } else {
                // 必须带原 sequence，否则 web sendAndWait 会一直等到超时
                GameProto.AckEnterTable empty = GameProto.AckEnterTable.newBuilder().build();
                sender.sendMessage(clientId, GMsg.ACK_ENTER_TABLE_MSG, tableId, empty, sequence);
                logger.warn("进入桌子失败, userId: {}, tableId: {}, result: {}", clientId, tableId, result);
            }
            logger.info("进入桌子请求处理完成, userId: {}, tableId: {}, success: {}, gateConnId: {}",
                    clientId, tableId, result, gateConnId);
        });
    }

    /**
     * 执行具体的入桌与断线重连逻辑。
     *
     * @param userId   玩家 ID
     * @param tableId  桌号
     * @param gateId   网关连接 ID
     * @param req      协议请求
     * @param table    当前桌子
     * @return 结果状态码
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
                    table.syncGameState(existingUser); // 多态调用
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
     * 构建进入桌子应答协议包。
     *
     * @param table 当前桌子
     * @return 应答协议包
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
