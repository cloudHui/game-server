package game.client;

import net.handler.Handler;
import net.handler.Handlers;
import net.message.Parser;
import net.message.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.registry.HandlerRegistry;
import utils.trace.TracedHandler;

import java.util.HashMap;
import java.util.Map;

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

    private static final Map<Integer, Handler> HANDLER_MAP = new HashMap<>();
    public static final Handlers HANDLERS = HANDLER_MAP::get;
    public static final Parser PARSER = HandlerRegistry::parseMessage;

    /**
     * 初始化协议处理器
     */
    public static void init() {
        try {
            HandlerRegistry.bind("game.client.handle", HANDLER_MAP);
            TracedHandler.wrapAll(HANDLER_MAP);
            logger.info("游戏客户端协议处理器初始化完成, 注册处理器数量: {}", HANDLER_MAP.size());
        } catch (Exception e) {
            logger.error("游戏客户端协议处理器初始化失败", e);
            throw new RuntimeException("ClientProto初始化失败", e);
        }
    }
}
