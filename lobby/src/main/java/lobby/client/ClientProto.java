package lobby.client;

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
 * 大厅客户端协议处理器
 * <p>
 * 负责大厅端消息路由与统一分发管理。
 */
public class ClientProto {
    private static final Logger logger = LoggerFactory.getLogger(ClientProto.class);

    public static final Transfer TRANSFER = (client, message) -> false;
    private static final Map<Integer, Handler> HANDLER_MAP = new HashMap<>();
    public static final Handlers HANDLERS = HANDLER_MAP::get;
    public static final Parser PARSER = HandlerRegistry::parseMessage;

    /**
     * 初始化大厅协议处理器
     */
    public static void init() {
        try {
            HandlerRegistry.bind("lobby.client.handle", HANDLER_MAP);
            TracedHandler.wrapAll(HANDLER_MAP);
            logger.info("Lobby ClientProto 初始化完成, 注册处理器总数: {}", HANDLER_MAP.size());
        } catch (Exception e) {
            logger.error("ClientProto 初始化失败", e);
            throw new RuntimeException("ClientProto初始化失败", e);
        }
    }
}
