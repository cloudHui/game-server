package lobby.client.handle.server.gate;

import lobby.Lobby;
import lobby.client.LobbyClient;
import msg.registor.message.CMsg;
import net.client.Sender;
import proto.ModelProto;
import tools.handle.AbstractRegisterHandler;
import utils.registry.annotation.ProcessType;
import utils.registry.enums.ServerType;

/**
 * 网关向大厅注册连接请求处理器
 * <p>
 * 接收网关节点注册，记录网关客户端实例并返回大厅元信息。
 */
@ProcessType(CMsg.REQ_REGISTER)
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
