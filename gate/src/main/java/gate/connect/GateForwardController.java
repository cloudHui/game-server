package gate.connect;

import msg.registor.message.GMsg;
import net.client.Sender;
import net.message.TCPMessage;
import net.msg.Msg;

/**
 * Gate 消息透传与推送控制器
 * 集中管理游戏服务向客户端的单向推送
 */
public class GateForwardController {

    @Msg(forward = {
            GMsg.NOT_CARD,
            GMsg.NOT_OP,
            GMsg.ACK_OP,
            GMsg.NOT_RESULT,
            GMsg.NOT_STATE,
            GMsg.NOT_TABLE_STATE,
            GMsg.MJ_TILE_NOT,
            GMsg.NOT_ROUND_RESULT,
            GMsg.NOT_GAME_RESULT
    }, desc = "游戏推送转玩家连接")
    public void forwardGamePush(Sender sender, TCPMessage tcpMessage) {
        ConnectProcessor.forwardToPlayer(tcpMessage);
    }

    @Msg(value = GMsg.ACK_ENTER_TABLE_MSG, desc = "入桌无序推送转玩家连接")
    public void forwardSeatUpdate(Sender sender, TCPMessage tcpMessage) {
        if (tcpMessage.getSequence() == 0) {
            ConnectProcessor.forwardToPlayer(tcpMessage);
        }
    }
}
