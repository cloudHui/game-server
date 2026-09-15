package gate.connect;

import gate.client.GateTcpClient;
import io.netty.channel.ChannelHandler;
import msg.registor.HandleTypeRegister;
import msg.registor.message.GMsg;
import net.client.Sender;
import net.client.handler.ClientHandler;
import net.handler.Handler;
import net.handler.Handlers;
import net.message.Parser;
import net.message.TCPMessage;
import net.message.Transfer;
import net.msg.MsgRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class ConnectProcessor {
    private static final Logger logger = LoggerFactory.getLogger(ConnectProcessor.class);

    private final static Map<Integer, Handler> HANDLER_MAP = new HashMap<>();
    private final static MsgRouter FORWARD_ROUTER = new MsgRouter();

    public final static Parser PARSER = HandleTypeRegister::parseMessage;
    public final static Handlers HANDLERS = HANDLER_MAP::get;
    public final static Transfer TRANSFER = ConnectProcessor::handleServerTrans;

    public static void init() {
        HandleTypeRegister.initFactory(ConnectProcessor.class, HANDLER_MAP);
        FORWARD_ROUTER.register(new GateForwardController());
        logger.info("ConnectProcessor 初始化完成, 转发路由数: {}", FORWARD_ROUTER.getRouteCount());
    }

    /**
     * 处理服务转发消息特殊处理
     */
    private static boolean handleServerTrans(ChannelHandler connectHandler, TCPMessage tcpMessage) {
        int msgId = tcpMessage.getMessageId();

        // 带 sequence 的请求回复走 completer，不要当推送吞掉（入桌/出牌/离开）
        if (tcpMessage.getSequence() != 0
                && (msgId == GMsg.ACK_ENTER_TABLE_MSG
                || msgId == GMsg.ACK_OP
                || msgId == GMsg.ACK_LEAVE)) {
            return false;
        }

        if (FORWARD_ROUTER.getRoute(msgId) != null) {
            Sender sender = (connectHandler instanceof Sender) ? (Sender) connectHandler : null;
            return FORWARD_ROUTER.dispatch(sender, tcpMessage);
        }
        return false;
    }

    /**
     * game→gate 推送：按 roleId(clientId) 转发到玩家连接
     */
    public static void forwardToPlayer(TCPMessage tcpMessage) {
        int roleId = tcpMessage.getClientId();
        if (roleId == 0) {
            logger.warn("推送缺少 roleId, msgId: 0x{}", Integer.toHexString(tcpMessage.getMessageId()));
            return;
        }
        GateTcpClient target = findClientByRoleId(roleId);
        if (target == null) {
            logger.warn("找不到玩家连接, roleId: {}, msgId: 0x{}",
                    roleId, Integer.toHexString(tcpMessage.getMessageId()));
            return;
        }
        target.sendMessage(tcpMessage);
        logger.debug("已转发推送, roleId: {}, msgId: 0x{}, mapId: {}",
                roleId, Integer.toHexString(tcpMessage.getMessageId()), tcpMessage.getMapId());
    }

    private static GateTcpClient findClientByRoleId(int roleId) {
        for (Map.Entry<Integer, Sender> entry : ClientHandler.getAllClient().entrySet()) {
            Sender sender = entry.getValue();
            if (sender instanceof GateTcpClient) {
                GateTcpClient client = (GateTcpClient) sender;
                if (client.getRoleId() == roleId) {
                    return client;
                }
            }
        }
        return null;
    }
}
