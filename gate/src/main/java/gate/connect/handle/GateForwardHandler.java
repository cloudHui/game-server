package gate.connect.handle;

import com.google.protobuf.Message;
import msg.registor.message.GMsg;
import net.client.Sender;
import net.handler.Handler;
import utils.registry.annotation.ProcessType;

/**
 * 游戏服推送批量透传处理器
 * <p>
 * 统一绑定所有单向推送消息 ID，由网关透明转发给对应的客户端长连接。
 */
@ProcessType(values = {
        GMsg.NOT_CARD,
        GMsg.NOT_OP,
        GMsg.ACK_OP,
        GMsg.NOT_RESULT,
        GMsg.NOT_STATE,
        GMsg.NOT_TABLE_STATE,
        GMsg.MJ_TILE_NOT,
        GMsg.NOT_ROUND_RESULT,
        GMsg.NOT_GAME_RESULT,
        GMsg.ACK_ENTER_TABLE_MSG
})
public class GateForwardHandler implements Handler {

    @Override
    public boolean handler(Sender sender, int clientId, Message message, long mapId, int sequence) {
        return true;
    }
}

