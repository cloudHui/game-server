package lobby.client.handle.role;

import com.google.protobuf.Message;
import lobby.manager.table.TableManager;
import msg.registor.message.LMsg;
import net.client.Sender;
import net.handler.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.LobbyProto;
import utils.registry.annotation.ProcessType;

/**
 * 房间列表查询请求处理器
 * <p>
 * 获取当前大厅配置的所有房间及桌子状态信息并返回给前端。
 */
@ProcessType(LMsg.REQ_ROOM_LIST_MSG)
public class ReqRoomListHandle implements Handler {

    private static final Logger logger = LoggerFactory.getLogger(ReqRoomListHandle.class);

    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        try {
            LobbyProto.AckRoomList response = TableManager.getInstance().getAllRoomTable();
            sender.sendMessage(clientId, LMsg.ACK_ROOM_LIST_MSG, 0L, response, sequence);
            logger.info("返回房间列表, clientId: {}, 房间数: {}", clientId, response.getRoomListCount());
        } catch (Exception e) {
            logger.error("处理房间列表请求失败, clientId: {}", clientId, e);
        }
        return true;
    }
}
