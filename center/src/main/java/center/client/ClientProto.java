package center.client;

import net.handler.Handlers;
import net.message.Parser;
import net.message.Transfer;
import net.msg.MsgRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 中心服务器客户端协议处理器
 * 负责消息解析和统一路由注册
 */
public class ClientProto {
    private static final Logger logger = LoggerFactory.getLogger(ClientProto.class);

    public static final Transfer TRANSFER = (client, message) -> false;
    public static final MsgRouter ROUTER = MsgRouter.getInstance();
    public static final Handlers HANDLERS = ROUTER;
    public static final Parser PARSER = ROUTER;

    public static void init() {
        try {
            // 扫描并装配中心服消息处理器
            ROUTER.scan("center.client.handle");
            logger.info("中心服务器协议处理器初始化完成, 注册路由总数: {}", ROUTER.getRouteCount());
        } catch (Exception e) {
            logger.error("中心服务器协议处理器初始化失败", e);
            throw new RuntimeException("ClientProto初始化失败", e);
        }
    }
}