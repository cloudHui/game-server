package game.client.handle.server;

import game.Game;
import game.manager.table.Table;
import msg.registor.message.SMsg;
import net.msg.Msg;
import net.msg.MsgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ModelProto;
import proto.ServerProto;

/**
 * 房间服务请求创建桌子处理器
 * <p>
 * 负责游戏桌子的异步创建与座位分配，支持机器人自动补齐。
 */
public class ReqCreateTableHandle {

    private static final Logger logger = LoggerFactory.getLogger(ReqCreateTableHandle.class);

    /**
     * 处理大厅发起的创建桌子请求
     *
     * @param ctx 消息上下文
     */
    @Msg(id = SMsg.REQ_CREATE_TABLE_MSG, desc = "大厅请求创建桌子")
    public void handle(MsgContext<ServerProto.ReqCreateGameTable> ctx) {
        ServerProto.ReqCreateGameTable request = ctx.getMsg();
        int clientId = ctx.getClientId();
        long mapId = ctx.getMapId();
        final int roomId = request.getRoomId();
        final ModelProto.RoomRole role = request.getRoomRole();

        logger.info("处理创建桌子请求, clientId: {}, roomId: {}", clientId, roomId);

        Game.getInstance().getTableManager().createTableAsync(roomId, role)
                .whenComplete((table, error) -> {
                    if (error == null && table != null && role.getRoleId() < 0) {
                        table.execute(table::fillRobotSeats);
                    }
                    sendCreateResponse(ctx, clientId, mapId, table, error);
                });
    }

    /**
     * 在生命周期线程完成后返回结果，网络线程不阻塞等待桌子创建
     *
     * @param ctx      消息上下文
     * @param clientId 客户端 ID
     * @param mapId    桌子 ID
     * @param table    创建完成的桌子实例
     * @param error    创建异常（若有）
     */
    private void sendCreateResponse(MsgContext<ServerProto.ReqCreateGameTable> ctx,
                                    int clientId, long mapId, Table table, Throwable error) {
        if (error != null || table == null) {
            logger.error("创建桌子失败, clientId: {}", clientId, error);
            ctx.reply(SMsg.ACK_CREATE_TABLE_MSG, mapId, ServerProto.AckCreateGameTable.getDefaultInstance());
            return;
        }

        ServerProto.AckCreateGameTable response = ServerProto.AckCreateGameTable.newBuilder()
                .setTables(ModelProto.RoomTableInfo.newBuilder()
                        .setTableId(table.getTableId())
                        .setRoomId(table.getRoomId())
                        .build())
                .build();
        ctx.reply(SMsg.ACK_CREATE_TABLE_MSG, mapId, response);
        logger.info("创建桌子请求处理完成, clientId: {}, tableId: {}", clientId, table.getTableId());
    }
}
