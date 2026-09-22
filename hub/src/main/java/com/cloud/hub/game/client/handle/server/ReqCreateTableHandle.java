package com.cloud.hub.game.client.handle.server;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.domain.table.Table;
import com.google.protobuf.Message;
import msg.annotation.ProcessType;
import msg.registor.message.SMsg;
import net.client.Sender;
import net.handler.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ModelProto;
import proto.ServerProto;

/**
 * 处理房间服务请求创建桌子处理器。
 * <p>
 * 负责游戏桌子的异步生命周期创建与桌号分配。
 */
@ProcessType(SMsg.REQ_CREATE_TABLE_MSG)
public class ReqCreateTableHandle implements Handler {

    /**
     * 日志记录器。
     */
    private static final Logger logger = LoggerFactory.getLogger(ReqCreateTableHandle.class);

    /**
     * 接收房间服务（Lobby）发起的建桌请求。
     *
     * @param sender   发送方句柄
     * @param clientId 客户端/服务连接 ID
     * @param message  建桌协议请求体
     * @param mapId    桌号
     * @param sequence 消息序列号
     * @return 恒为 true
     */
    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        final int roomId;
        final ModelProto.RoomRole role;
        try {
            ServerProto.ReqCreateGameTable request = (ServerProto.ReqCreateGameTable) message;
            roomId = request.getRoomId();
            role = request.getRoomRole();
            logger.info("处理创建桌子请求, clientId: {}, roomId: {}", clientId, roomId);

        } catch (Exception e) {
            logger.error("处理创建桌子请求失败, clientId: {}", clientId, e);
            return true;
        }
        Game.getInstance().getTableManager().createTableAsync(roomId, role)
                .whenComplete((table, error) -> {
                    if (error == null && table != null && role.getRoleId() < 0) {
                        table.execute("填充机器人座位", table::fillRobotSeats);
                    }
                    sendCreateResponse(sender, clientId, mapId, sequence, table, error);
                });
        return true;
    }

    /**
     * 在生命周期线程完成后异步回送建桌结果，网络线程不阻塞等待。
     *
     * @param sender   发送方
     * @param clientId 连接 ID
     * @param mapId    桌号
     * @param sequence 序列号
     * @param table    创建完成的牌桌实例
     * @param error    异常信息（若创建失败）
     */
    private void sendCreateResponse(Sender sender, int clientId, long mapId, int sequence,
                                    Table table, Throwable error) {
        if (error != null || table == null) {
            logger.error("创建桌子失败, clientId: {}", clientId, error);
            sender.sendMessage(clientId, SMsg.ACK_CREATE_TABLE_MSG, mapId,
                    ServerProto.AckCreateGameTable.getDefaultInstance(), sequence);
            return;
        }
        ServerProto.AckCreateGameTable response = ServerProto.AckCreateGameTable.newBuilder()
                .setTables(ModelProto.RoomTableInfo.newBuilder().setTableId(table.getTableId())
                        .setRoomId(table.getRoomId()).build()).build();
        sender.sendMessage(clientId, SMsg.ACK_CREATE_TABLE_MSG, mapId, response, sequence);
        logger.info("创建桌子请求处理完成, clientId: {}, tableId: {}", clientId, table.getTableId());
    }
}
