package game.client.handle.server;

import com.google.protobuf.Message;
import game.Game;
import game.domain.table.Table;
import msg.registor.message.SMsg;
import net.client.Sender;
import net.handler.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ModelProto;
import proto.ServerProto;
import utils.registry.annotation.ProcessType;

/**
 * 房间服务请求创建桌子处理器
 * <p>
 * 负责游戏桌子的异步创建与座位分配，支持机器人自动补齐。
 */
@ProcessType(SMsg.REQ_CREATE_TABLE_MSG)
public class ReqCreateTableHandle implements Handler {

    private static final Logger logger = LoggerFactory.getLogger(ReqCreateTableHandle.class);

    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        ServerProto.ReqCreateGameTable request = (ServerProto.ReqCreateGameTable) message;
        final int roomId = request.getRoomId();
        final ModelProto.RoomRole role = request.getRoomRole();

        logger.info("处理创建桌子请求, clientId: {}, roomId: {}", clientId, roomId);

        Game.getInstance().getTableManager().createTableAsync(roomId, role)
                .whenComplete((table, error) -> {
                    if (error == null && table != null && role.getRoleId() < 0) {
                        table.execute(table::fillRobotSeats);
                    }
                    sendCreateResponse(sender, clientId, mapId, sequence, table, error);
                });
        return true;
    }

    /**
     * 在生命周期线程完成后返回结果，网络线程不阻塞等待桌子创建
     */
    private void sendCreateResponse(Sender sender, int clientId, long mapId, int sequence, Table table, Throwable error) {
        if (error != null || table == null) {
            logger.error("创建桌子失败, clientId: {}", clientId, error);
            sender.sendMessage(clientId, SMsg.ACK_CREATE_TABLE_MSG, mapId, ServerProto.AckCreateGameTable.getDefaultInstance(), sequence);
            return;
        }

        table.getRoomTableInfoAsync().whenComplete((info, infoError) -> {
            if (infoError != null || info == null) {
                logger.error("获取桌子信息失败, tableId: {}", table.getTableId(), infoError);
                sender.sendMessage(clientId, SMsg.ACK_CREATE_TABLE_MSG, mapId, ServerProto.AckCreateGameTable.getDefaultInstance(), sequence);
                return;
            }
            ServerProto.AckCreateGameTable response = ServerProto.AckCreateGameTable.newBuilder()
                    .setTables(info)
                    .build();
            sender.sendMessage(clientId, SMsg.ACK_CREATE_TABLE_MSG, mapId, response, sequence);
            logger.info("创建桌子成功, tableId: {}, roomId: {}", table.getTableId(), table.getModel().getId());
        });
    }
}
