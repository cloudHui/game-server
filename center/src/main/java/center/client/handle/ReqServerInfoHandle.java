package center.client.handle;

import center.Center;
import center.client.CenterClient;
import utils.registry.enums.ServerType;
import msg.registor.message.CMsg;
import net.client.handler.ClientHandler;
import net.msg.Msg;
import net.msg.MsgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ServerProto;
import tools.ServerClientManager;

import java.util.List;

/**
 * 服务信息查询请求处理器
 * <p>
 * 返回指定类型服务器的信息列表（如大厅向中心服拉取网关或游戏服列表）。
 */
public class ReqServerInfoHandle {

    private static final Logger logger = LoggerFactory.getLogger(ReqServerInfoHandle.class);

    /**
     * 处理服务信息查询请求
     *
     * @param ctx 消息上下文
     */
    @Msg(id = CMsg.REQ_SERVER, desc = "查询各服务节点信息")
    public void handle(MsgContext<ServerProto.ReqServerInfo> ctx) {
        try {
            ServerProto.ReqServerInfo request = ctx.getMsg();
            ServerClientManager manager = Center.getInstance().getServerManager();

            logger.info("处理服务信息查询请求, 查询类型数量: {}", request.getServerTypeCount());

            ServerProto.AckServerInfo response = buildServerInfoResponse(request, manager);
            ctx.reply(CMsg.ACK_SERVER, response);

            logger.info("返回服务信息, 服务器数量: {}", response.getServersCount());
        } catch (Exception e) {
            logger.error("处理服务信息查询请求失败", e);
        }
    }

    /**
     * 构建服务器信息响应
     *
     * @param request 查询请求
     * @param manager 服务器客户端管理器
     * @return 包含所有指定类型在线服务器信息的应答
     */
    private ServerProto.AckServerInfo buildServerInfoResponse(ServerProto.ReqServerInfo request, ServerClientManager manager) {
        ServerProto.AckServerInfo.Builder response = ServerProto.AckServerInfo.newBuilder();
        List<Integer> serverTypes = request.getServerTypeList();

        for (int serverTypeValue : serverTypes) {
            ServerType serverType = ServerType.get(serverTypeValue);
            if (serverType == null) {
                logger.error("未知的服务器类型值: {}", serverTypeValue);
                continue;
            }

            addServerInfoToResponse(manager, response, serverType);
        }

        return response.build();
    }

    /**
     * 添加指定类型的服务器信息到响应
     *
     * @param manager    服务器客户端管理器
     * @param response   响应构建器
     * @param serverType 目标服务器类型
     */
    private void addServerInfoToResponse(ServerClientManager manager,
                                         ServerProto.AckServerInfo.Builder response,
                                         ServerType serverType) {
        List<ClientHandler> servers = manager.getAllTypeServer(serverType);
        if (servers == null || servers.isEmpty()) {
            logger.info("该类型服务器暂无在线实例: {}", serverType);
            return;
        }

        int addedCount = 0;
        for (ClientHandler client : servers) {
            CenterClient centerClient = (CenterClient) client;
            if (centerClient.getServerInfo() != null) {
                response.addServers(centerClient.getServerInfo());
                addedCount++;
            }
        }

        logger.info("添加服务器信息到响应, serverType: {}, 数量: {}", serverType, addedCount);
    }
}
