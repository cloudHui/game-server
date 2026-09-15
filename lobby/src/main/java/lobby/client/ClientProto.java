package lobby.client;

import net.handler.Handlers;
import net.message.Parser;
import net.message.Transfer;
import net.msg.MsgRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 大厅客户端协议处理器
 * <p>
 * 负责大厅端消息路由与统一分发管理。
 */
public class ClientProto {
    private static final Logger logger = LoggerFactory.getLogger(ClientProto.class);

    public static final Transfer TRANSFER = (client, message) -> false;
    public static final MsgRouter ROUTER = MsgRouter.getInstance();
    public static final Handlers HANDLERS = ROUTER;
    public static final Parser PARSER = ROUTER;

    /**
     * 初始化大厅协议处理器
     */
    public static void init() {
        try {
            // 扫描并装配大厅角色与服务端处理器
            ROUTER.scan("lobby.client.handle");
            logger.info("Lobby ClientProto 初始化完成, 注册路由总数: {}", ROUTER.getRouteCount());
        } catch (Exception e) {
            logger.error("ClientProto 初始化失败", e);
            throw new RuntimeException("ClientProto初始化失败", e);
        }
    }
}
