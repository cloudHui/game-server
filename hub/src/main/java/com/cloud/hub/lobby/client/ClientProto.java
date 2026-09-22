package com.cloud.hub.lobby.client;

import msg.annotation.ProcessType;
import msg.registor.HandleTypeRegister;
import net.handler.Handler;
import net.handler.Handlers;
import net.message.Parser;
import net.message.Transfer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.trace.TracedHandler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ClientProto {
    private static final Logger logger = LoggerFactory.getLogger(ClientProto.class);

    public static final Transfer TRANSFER = (client, message) -> false;
    private static final Map<Integer, Handler> handlers = new HashMap<>();
    public static final Handlers HANDLERS = handlers::get;
    public static final Parser PARSER = HandleTypeRegister::parseMessage;

    public static void init() {
        try {
            handlers.putAll(utils.registry.HandlerRegistry.buildSingle(
                    Arrays.asList(ClientProto.class.getPackage().getName(), "tools.handle"),
                    Handler.class, ProcessType.class, Integer.class));
            TracedHandler.wrapAll(handlers);
            logger.info("Lobby ClientProto 初始化完成, 处理器数量: {}", handlers.size());
        } catch (Exception e) {
            logger.error("ClientProto 初始化失败", e);
            throw new RuntimeException("ClientProto初始化失败", e);
        }
    }
}
