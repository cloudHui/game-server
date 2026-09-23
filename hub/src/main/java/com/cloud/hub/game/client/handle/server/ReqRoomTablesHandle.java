package com.cloud.hub.game.client.handle.server;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.manager.TableManager;
import com.google.protobuf.Message;
import utils.registry.annotation.ProcessType;
import msg.registor.message.SMsg;
import net.client.Sender;
import net.handler.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ServerProto;

/**
 * 处理房间服务（Lobby）拉取牌桌列表请求处理器。
 */
@ProcessType(SMsg.REQ_ROOM_TABLES_MSG)
public class ReqRoomTablesHandle implements Handler {

    /**
     * 日志记录器。
     */
    private static final Logger logger = LoggerFactory.getLogger(ReqRoomTablesHandle.class);

    /**
     * 接收拉取房间牌桌列表请求并异步返回。
     *
     * @param sender   发送方句柄
     * @param clientId 连接 ID
     * @param message  协议请求
     * @param mapId    桌号
     * @param sequence 消息序列号
     * @return 恒为 true
     */
    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        try {
            TableManager tableManager = Game.getInstance().getTableManager();
            tableManager.getAllTableInfoAsync().whenComplete((tables, error) -> {
                if (error != null) {
                    logger.error("读取桌子列表失败, clientId: {}", clientId, error);
                    return;
                }
                ServerProto.AckRoomTables ack = ServerProto.AckRoomTables.newBuilder()
                        .addAllTables(tables).build();
                sender.sendMessage(clientId, SMsg.ACK_ROOM_TABLES_MSG, mapId, ack, sequence);
                logger.info("返回桌子列表给Lobby, count: {}, clientId: {}", tables.size(), clientId);
            });
        } catch (Exception e) {
            logger.error("处理Lobby桌子列表请求失败, clientId: {}", clientId, e);
        }
        return true;
    }
}
