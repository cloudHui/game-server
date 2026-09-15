package lobby.client.handle.role;

import lobby.manager.table.TableManager;
import msg.registor.message.LMsg;
import net.msg.Msg;
import net.msg.MsgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.LobbyProto;

/**
 * 房间列表查询请求处理器
 * <p>
 * 获取当前大厅配置的所有房间及桌子状态信息并返回给前端。
 */
public class ReqRoomListHandle {

    private static final Logger logger = LoggerFactory.getLogger(ReqRoomListHandle.class);

    /**
     * 处理房间列表请求
     *
     * @param ctx 消息上下文
     */
    @Msg(id = LMsg.REQ_ROOM_LIST_MSG, desc = "获取房间列表")
    public void handle(MsgContext<LobbyProto.ReqRoomList> ctx) {
        try {
            int clientId = ctx.getClientId();
            LobbyProto.AckRoomList response = TableManager.getInstance().getAllRoomTable();
            ctx.reply(LMsg.ACK_ROOM_LIST_MSG, 0L, response);
            logger.info("返回房间列表, clientId: {}, 房间数: {}", clientId, response.getRoomListCount());
        } catch (Exception e) {
            logger.error("处理房间列表请求失败, clientId: {}", ctx.getClientId(), e);
        }
    }
}
