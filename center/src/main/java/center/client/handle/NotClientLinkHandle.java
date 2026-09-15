package center.client.handle;

import msg.registor.message.CMsg;
import net.msg.Msg;
import net.msg.MsgContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ServerProto;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 网关服务器通知客户端连接建立事件处理器
 * <p>
 * 跟踪客户端 IP 与网关地址的映射关系，用于后续连接管理和负载均衡。
 */
public class NotClientLinkHandle {

    private static final Logger logger = LoggerFactory.getLogger(NotClientLinkHandle.class);

    /** 客户端 IP 到网关地址的映射 */
    private static final ConcurrentHashMap<String, String> clientToGateMap = new ConcurrentHashMap<>();

    /**
     * 客户端断开连接
     *
     * @param clientIp 客户端 IP
     */
    public static void clientDisconnect(String clientIp) {
        String removedGate = clientToGateMap.remove(clientIp);
        if (removedGate != null) {
            logger.info("客户端断开连接, clientIp: {}, gate: {}", clientIp, removedGate);
        } else {
            logger.warn("客户端断开连接但未找到映射, clientIp: {}", clientIp);
        }
    }

    /**
     * 客户端建立连接
     *
     * @param clientIp    客户端 IP
     * @param gateAddress 网关地址
     */
    public static void addClientConnection(String clientIp, String gateAddress) {
        if (clientIp == null || gateAddress == null) {
            logger.error("无效的客户端连接参数, clientIp: {}, gate: {}", clientIp, gateAddress);
            return;
        }

        String previousGate = clientToGateMap.put(clientIp, gateAddress);
        if (previousGate != null) {
            logger.info("客户端切换网关, clientIp: {}, 旧网关: {}, 新网关: {}",
                    clientIp, previousGate, gateAddress);
        } else {
            logger.info("客户端连接新网关, clientIp: {}, gate: {}", clientIp, gateAddress);
        }
    }

    /**
     * 获取客户端连接的网关地址
     *
     * @param clientIp 客户端 IP
     * @return 网关地址
     */
    public static String getClientGate(String clientIp) {
        return clientToGateMap.get(clientIp);
    }

    /**
     * 获取当前连接的客户端数量
     *
     * @return 客户端数量
     */
    public static int getClientCount() {
        return clientToGateMap.size();
    }

    /**
     * 处理网关通知的客户端连接建立事件
     *
     * @param ctx 消息上下文
     */
    @Msg(id = CMsg.NOT_LINK, desc = "客户端连接建立通知")
    public void handle(MsgContext<ServerProto.NotRegisterClient> ctx) {
        try {
            ServerProto.NotRegisterClient notification = ctx.getMsg();
            String clientIp = notification.getCert().toStringUtf8();
            String gateAddress = notification.getGate().toStringUtf8();

            addClientConnection(clientIp, gateAddress);
        } catch (Exception e) {
            logger.error("处理客户端连接通知失败, clientId: {}", ctx.getClientId(), e);
        }
    }
}