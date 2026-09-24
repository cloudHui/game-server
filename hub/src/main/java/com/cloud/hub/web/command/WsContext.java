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
 * WebSocket 会话与消息发送上下文管理器。
 * <p>
 * 集中管理长连接的双向映射绑定关系（物理连接 ID 与业务 Session ID），
 * 并提供标准 JSON 格式的成功/错误响应发送辅助工具。
 *
 * @author cloud
 */
@Component
public class WsContext {

    private static final Logger logger = LoggerFactory.getLogger(WsContext.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 物理连接 ID 到业务会话 Token 的映射表 */
    private final Map<String, String> sessionMapping = new ConcurrentHashMap<>();

    /** 业务会话 Token 到物理 WebSocketSession 的映射表 */
    private final Map<String, WebSocketSession> wsBySession = new ConcurrentHashMap<>();

    /**
     * 绑定物理连接与业务会话 Token。
     *
     * @param ws        WebSocket 物理连接会话
     * @param sessionId 业务会话 Token
     */
    public void bind(WebSocketSession ws, String sessionId) {
        sessionMapping.put(ws.getId(), sessionId);
        wsBySession.put(sessionId, ws);
    }

    /**
     * 移除物理连接与对应的业务会话绑定。
     *
     * @param ws WebSocket 物理连接会话
     */
    public void remove(WebSocketSession ws) {
        String sessionId = sessionMapping.remove(ws.getId());
        if (sessionId != null) {
            wsBySession.remove(sessionId, ws);
        }
    }

    /**
     * 根据物理连接查找绑定的业务会话 Token。
     *
     * @param ws WebSocket 物理连接会话
     * @return 业务会话 Token，未认证返回 null
     */
    public String sessionId(WebSocketSession ws) {
        return sessionMapping.get(ws.getId());
    }

    /**
     * 根据业务会话 Token 查找活跃物理连接。
     *
     * @param sessionId 业务会话 Token
     * @return 物理连接会话，不在线返回 null
     */
    public WebSocketSession session(String sessionId) {
        return wsBySession.get(sessionId);
    }

    /**
     * 发送成功响应（code = 0）。
     *
     * @param ws     物理连接会话
     * @param action 动作标识
     * @param seq    请求流水号
     * @param msg    提示信息
     * @param data   业务载荷数据
     */
    public void sendSuccess(WebSocketSession ws, String action, int seq, String msg, Object data) {
        send(ws, action, seq, 0, msg, data);
    }

    /**
     * 发送失败响应（code = 1）。
     *
     * @param ws  物理连接会话
     * @param seq 请求流水号
     * @param msg 错误原因提示
     */
    public void sendError(WebSocketSession ws, int seq, String msg) {
        send(ws, "error", seq, 1, msg, null);
    }

    /**
     * 底层执行 WebSocket 文本帧发送。
     *
     * @param ws     物理连接会话
     * @param action 动作标识
     * @param seq    请求流水号
     * @param code   响应状态码
     * @param msg    提示信息
     * @param data   业务载荷数据
     */
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
            if (data != null) {
                resp.put("data", data);
            }
            String json = MAPPER.writeValueAsString(resp);
            synchronized (ws) {
                if (ws.isOpen()) {
                    ws.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            logger.warn("发送WebSocket消息失败, action: {}, sessionId: {}, cause: {}", action, ws.getId(), e.toString());
        }
    }
}