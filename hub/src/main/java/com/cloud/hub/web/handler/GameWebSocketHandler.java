package com.cloud.hub.web.handler;

import com.cloud.hub.web.command.WsCommandHandler;
import com.cloud.hub.web.command.WsContext;
import com.google.protobuf.Message;
import msg.registor.HandleTypeRegister;
import net.message.TCPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏 WebSocket 接入层。
 *
 * <p>仅负责帧解析与路由分发；具体 action 业务逻辑在 {@code com.cloud.hub.web.command} 包下，
 * 每个命令一个 {@link WsCommandHandler} 实现类，标注 {@code @Component} 后 Spring 自动注入，
 * 无需在此文件手动注册，也不依赖 switch/if 链。
 *
 * <p>新增 WebSocket 命令：在 command 包创建实现类 → 加 {@code @Component} → 完成。
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final com.cloud.hub.web.service.GatewayTransport gateClient;

    /** Gate 推送路由与 session 发送工具（共享 bean）。 */
    private final WsContext wsContext;

    /**
     * Spring 自动收集所有 WsCommandHandler 实现并注入。
     * 新增命令只需在 command 包加一个带 @Component 的实现类。
     */
    private final List<WsCommandHandler> commandHandlers;

    /** action → handler 分发表，启动时从 commandHandlers 构建。 */
    private final Map<String, WsCommandHandler> actionMap = new HashMap<>();

    @Autowired
    public GameWebSocketHandler(com.cloud.hub.web.service.GatewayTransport gateClient,
                                WsContext wsContext,
                                List<WsCommandHandler> commandHandlers) {
        this.gateClient = gateClient;
        this.wsContext = wsContext;
        this.commandHandlers = commandHandlers;
    }

    @PostConstruct
    public void init() {
        gateClient.setPushListener(this::onGatePush);
        // 将 Spring 注入的所有 WsCommandHandler 注册到分发表
        for (WsCommandHandler h : commandHandlers) {
            actionMap.put(h.action(), h);
            logger.info("注册 WebSocket 命令处理器: action={}, handler={}", h.action(), h.getClass().getSimpleName());
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket连接建立, wsSessionId: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String removed = wsContext.sessionId(session);
        wsContext.remove(session);
        if (removed != null) {
            logger.info("WebSocket连接关闭, sessionId: {}, status: {}", removed, status);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String action = "unknown";
        int seq = 0;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(message.getPayload(), Map.class);
            action = (String) msg.get("action");
            seq = msg.get("seq") != null ? ((Number) msg.get("seq")).intValue() : 0;
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) msg.get("data");

            logger.debug("收到WebSocket消息, action: {}, seq: {}, sessionId: {}", action, seq, session.getId());

            // 注册表分发：新增命令只需在 command 包加实现类，此处无需修改
            WsCommandHandler handler = actionMap.get(action);
            if (handler != null) {
                handler.handle(wsContext, session, seq, data);
            } else {
                wsContext.sendError(session, seq, "未知操作: " + action);
            }
        } catch (Exception e) {
            logger.error("处理WebSocket消息异常, action: {}, seq: {}, sessionId: {}", action, seq, session.getId(), e);
            wsContext.sendError(session, seq, "消息处理失败");
        }
    }

    /**
     * Gate 推送 → WebSocket 转发。
     */
    private void onGatePush(String sessionId, TCPMessage tcpMessage) {
        WebSocketSession ws = wsContext.session(sessionId);
        if (ws == null || !ws.isOpen()) return;
        try {
            int msgId = tcpMessage.getMessageId();
            Message proto = HandleTypeRegister.parseMessage(msgId,
                    tcpMessage.getMessage() == null ? new byte[0] : tcpMessage.getMessage());
            String pushAction = GameWsPushFormatter.pushAction(msgId);
            if (pushAction == null) return;
            Object data = GameWsPushFormatter.formatPush(msgId, proto);
            wsContext.send(ws, pushAction, 0, 0, "push", data);
        } catch (Exception e) {
            logger.error("转发推送失败, sessionId: {}, msgId: 0x{}", sessionId,
                    Integer.toHexString(tcpMessage.getMessageId()), e);
        }
    }
}