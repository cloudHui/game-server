package game.client.handle.server;

import game.Game;
import game.manager.TableManager;
import msg.registor.message.SMsg;
import net.msg.Msg;
import net.msg.MsgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ServerProto;

/**
 * 处理房间桌子列表查询请求处理器
 * <p>
 * 响应大厅服务器同步游戏桌列表信息的请求。
 */
public class ReqRoomTablesHandle {

    private static final Logger logger = LoggerFactory.getLogger(ReqRoomTablesHandle.class);

    /**
     * 处理读取所有桌子列表请求
     *
     * @param ctx 消息上下文
     */
    @Msg(id = SMsg.REQ_ROOM_TABLES_MSG, desc = "查询游戏桌子列表")
    public void handle(MsgContext<ServerProto.ReqRoomTables> ctx) {
        try {
            int clientId = ctx.getClientId();
            long mapId = ctx.getMapId();
            TableManager tableManager = Game.getInstance().getTableManager();

            tableManager.getAllTableInfoAsync().whenComplete((tables, error) -> {
                if (error != null) {
                    logger.error("读取桌子列表失败, clientId: {}", clientId, error);
                    return;
                }
                ServerProto.AckRoomTables ack = ServerProto.AckRoomTables.newBuilder()
                        .addAllTables(tables)
                        .build();
                ctx.reply(SMsg.ACK_ROOM_TABLES_MSG, mapId, ack);
                logger.info("返回桌子列表给Lobby, count: {}, clientId: {}", tables.size(), clientId);
            });
        } catch (Exception e) {
            logger.error("处理Lobby桌子列表请求失败, clientId: {}", ctx.getClientId(), e);
        }
    }
}
