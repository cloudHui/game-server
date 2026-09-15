package game.client;

import net.handler.Handlers;
import net.message.Parser;
import net.message.Transfer;
import net.msg.MsgRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 游戏客户端协议处理器
 * 负责统一消息路由与分发
 */
public class ClientProto {
    private static final Logger logger = LoggerFactory.getLogger(ClientProto.class);

    /**
     * 消息转发接口 - 游戏服务器不直接转发消息,返回false
     */
    public static final Transfer TRANSFER = (client, message) -> false;

    public static final MsgRouter ROUTER = MsgRouter.getInstance();
    public static final Handlers HANDLERS = ROUTER;
    public static final Parser PARSER = ROUTER;

    /**
     * 初始化协议处理器
     */
    public static void init() {
        try {
            // 扫描并自动装配游戏模块消息处理器
            ROUTER.scan("game.client.handle");
            logger.info("游戏客户端协议处理器初始化完成, 注册路由总数: {}", ROUTER.getRouteCount());
        } catch (Exception e) {
            logger.error("游戏客户端协议处理器初始化失败", e);
            throw new RuntimeException("ClientProto初始化失败", e);
        }
    }
}