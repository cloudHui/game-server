package com.cloud.hub.web.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话上下文（Spring 单例 Bean）。
 *
 * <p>集中管理会话 mapping（wsSessionId → sessionId）与
 * WebSocket 发送工具（sendSuccess、sendError），
 * 供所有 {@link WsCommandHandler} 实现类注入使用，
 * 避免在每个 Handler 里重复持有相同引用。
 */
@Component
public class WsContext {

    private static final Logger logger = LoggerFactory.getLogger(WsContext.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** wsSessionId → sessionId（认证后绑定） */
    private final Map<String, String> sessionMapping = new ConcurrentHashMap<>();
    /** sessionId → WebSocketSession（Gate 推送时反查） */
    private final Map<String, WebSocketSession> wsBySession = new ConcurrentHashMap<>();

    // ==================== session 管理 ====================

    /**
     * 绑定 WS sessionId → 业务 sessionId，认证成功时调用。
     */
    public void bind(WebSocketSession ws, String sessionId) {
        sessionMapping.put(ws.getId(), sessionId);
        wsBySession.put(sessionId, ws);
    }

    /**
     * 断开连接时清理映射。
     */
    public void remove(WebSocketSession ws) {
        String sessionId = sessionMapping.remove(ws.getId());
        if (sessionId != null) {
            wsBySession.remove(sessionId, ws);
        }
    }

    /**
     * 根据 WebSocket 会话查找业务 sessionId；未认证时返回 null。
     */
    public String sessionId(WebSocketSession ws) {
        return sessionMapping.get(ws.getId());
    }

    /**
     * 根据业务 sessionId 查找 WebSocket 会话；用于 Gate 推送路由。
     */
    public WebSocketSession session(String sessionId) {
        return wsBySession.get(sessionId);
    }

    // ==================== 发送工具 ====================

    /** 发送成功响应（code=0）。 */
    public void sendSuccess(WebSocketSession ws, String action, int seq, String msg, Object data) {
        send(ws, action, seq, 0, msg, data);
    }

    /** 发送错误响应（code=1）。 */
    public void sendError(WebSocketSession ws, int seq, String msg) {
        send(ws, "error", seq, 1, msg, null);
    }

    /** 底层发送，所有回包均走此方法。 */
    public void send(WebSocketSession ws, String action, int seq, int code, String msg, Object data) {
        if (ws == null || !ws.isOpen()) {
            logger.warn("跳过已关闭WebSocket发送, action: {}, seq: {}", action, seq);
            return;
        }
        try {
            Map<String, Object> resp = new HashMap<>();
            resp.put("action", action);
            resp.put("seq", seq);
            resp.put("code", code);
            resp.put("msg", msg);
            if (data != null) resp.put("data", data);
            String json = MAPPER.writeValueAsString(resp);
            synchronized (ws) {
                if (!ws.isOpen()) return;
                ws.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            logger.warn("发送WebSocket消息失败, action: {}, sessionId: {}, cause: {}", action, ws.getId(), e.toString());
        }
    }
}