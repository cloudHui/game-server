package com.cloud.hub.web.handler;

import com.cloud.hub.web.command.WsCommandHandler;
import com.cloud.hub.web.command.WsContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Message;
import net.message.TCPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.registry.HandlerRegistry;
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
 * 棋牌游戏 WebSocket 文本长连接处理器。
 * <p>
 * 负责客户端连接生命周期维护、文本 JSON 帧解析、命令路由分发（基于 {@link WsCommandHandler}），
 * 以及接收网关异步推送并转发给前端客户端。
 *
 * @author cloud
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final com.cloud.hub.web.service.GatewayTransport gateClient;
    private final WsContext wsContext;
    private final List<WsCommandHandler> commandHandlers;
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
            Map<String, Object> msg = OBJECT_MAPPER.readValue(message.getPayload(), Map.class);
            action = (String) msg.get("action");
            seq = msg.get("seq") != null ? ((Number) msg.get("seq")).intValue() : 0;
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) msg.get("data");

            dispatchCommand(session, action, seq, data);
        } catch (Exception e) {
            logger.error("处理WebSocket消息异常, action: {}, seq: {}, sessionId: {}", action, seq, session.getId(), e);
            wsContext.sendError(session, seq, "消息处理失败");
        }
    }

    /**
     * 派发具体的命令处理器执行。
     */
    private void dispatchCommand(WebSocketSession session, String action, int seq, Map<String, Object> data) {
        WsCommandHandler handler = actionMap.get(action);
        if (handler != null) {
            handler.handle(wsContext, session, seq, data);
        } else {
            wsContext.sendError(session, seq, "未知操作: " + action);
        }
    }

    /**
     * 网关推送回调并转发至对应客户端。
     */
    private void onGatePush(String sessionId, TCPMessage tcpMessage) {
        WebSocketSession ws = wsContext.session(sessionId);
        if (ws == null || !ws.isOpen()) {
            return;
        }
        try {
            int msgId = tcpMessage.getMessageId();
            Message proto = HandlerRegistry.parseMessage(msgId,
                    tcpMessage.getMessage() == null ? new byte[0] : tcpMessage.getMessage());
            String pushAction = GameWsPushFormatter.pushAction(msgId);
            if (pushAction == null) {
                return;
            }
            Object data = GameWsPushFormatter.formatPush(msgId, proto);
            wsContext.send(ws, pushAction, 0, 0, "push", data);
        } catch (Exception e) {
            logger.error("转发推送失败, sessionId: {}, msgId: 0x{}", sessionId,
                    Integer.toHexString(tcpMessage.getMessageId()), e);
        }
    }
}