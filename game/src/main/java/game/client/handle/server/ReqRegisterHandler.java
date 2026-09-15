package game.client.handle.server;

import game.Game;
import game.client.GameClient;
import msg.registor.enums.ServerType;
import msg.registor.message.CMsg;
import net.client.Sender;
import net.msg.Msg;
import proto.ModelProto;
import tools.handle.AbstractRegisterHandler;

/**
 * 注册服务信息请求处理器
 * <p>
 * 接收网关等其他节点发起的注册握手请求，并登记到当前服务的客户端管理器中。
 */
@Msg(id = CMsg.REQ_REGISTER, desc = "服务器注册请求")
public class ReqRegisterHandler extends AbstractRegisterHandler<Game> {

    @Override
    protected Game getServerInstance() {
        return Game.getInstance();
    }

    @Override
    protected void addServerClient(ServerType serverType, Sender client) {
        getServerInstance().getServerClientManager().addServerClient(serverType, (GameClient) client);
    }

    @Override
    protected ModelProto.ServerInfo getCurrentServerInfo() {
        return getServerInstance().getServerInfo();
    }

    /**
     * 注册前处理：设置对方服务器元信息
     */
    @Override
    protected void beforeRegistration(Sender sender, ModelProto.ServerInfo serverInfo, ServerType serverType) {
        ((GameClient) sender).setServerInfo(serverInfo);
    }
}