package lobby.client.handle.server.gate;

import lobby.Lobby;
import lobby.client.LobbyClient;
import msg.registor.enums.ServerType;
import msg.registor.message.CMsg;
import net.client.Sender;
import net.msg.Msg;
import proto.ModelProto;
import tools.handle.AbstractRegisterHandler;

/**
 * 网关向大厅注册连接请求处理器
 * <p>
 * 接收网关节点注册，记录网关客户端实例并返回大厅元信息。
 */
@Msg(id = CMsg.REQ_REGISTER, desc = "网关注册请求")
public class ReqRegisterHandler extends AbstractRegisterHandler<Lobby> {

    @Override
    protected Lobby getServerInstance() {
        return Lobby.getInstance();
    }

    @Override
    protected void addServerClient(ServerType serverType, Sender client) {
        getServerInstance().serverClientManager.addServerClient(serverType, (LobbyClient) client);
    }

    @Override
    protected ModelProto.ServerInfo getCurrentServerInfo() {
        return getServerInstance().getServerInfo();
    }

    @Override
    protected void beforeRegistration(Sender sender, ModelProto.ServerInfo serverInfo, ServerType serverType) {
        ((LobbyClient) sender).setServerInfo(serverInfo);
    }
}
