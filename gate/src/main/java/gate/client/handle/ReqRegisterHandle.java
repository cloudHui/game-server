package gate.client.handle;

import com.google.protobuf.Message;
import msg.registor.message.CMsg;
import net.client.Sender;
import net.handler.Handler;
import proto.ModelProto;
import proto.ServerProto;
import utils.registry.annotation.ProcessType;
import utils.registry.enums.ServerType;

/**
 * 机器人注册服务信息请求
 */
@ProcessType(CMsg.REQ_REGISTER)
public class ReqRegisterHandle implements Handler {

    @Override
    public boolean handler(Sender sender, int clientId, Message reqRegister, long mapId, int sequence) {
        ServerProto.ReqRegister req = (ServerProto.ReqRegister) reqRegister;
        ModelProto.ServerInfo serverInfo = req.getServerInfo();
        ServerType serverType = ServerType.get(serverInfo.getServerType());
        if (serverType == null) {
            return true;
        }

        ServerProto.AckRegister.Builder ackRegister = ServerProto.AckRegister.newBuilder();
        sender.sendMessage(clientId, CMsg.ACK_REGISTER, mapId, ackRegister.build(), sequence);
        return true;
    }
}
