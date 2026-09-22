package com.cloud.hub.web.command;

import com.cloud.hub.web.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * auth 命令：验证业务 sessionId 并建立 WS 会话映射。
 */
@Component
public class AuthCommand implements WsCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuthCommand.class);

    private final UserService userService;

    public AuthCommand(UserService userService) {
        this.userService = userService;
    }

    @Override
    public String action() {
        return "auth";
    }

    @Override
    public void handle(WsContext ctx, WebSocketSession ws, int seq, Map<String, Object> data) {
        String sessionId = (String) data.get("sessionId");
        if (sessionId == null) {
            ctx.sendError(ws, seq, "缺少sessionId");
            return;
        }
        UserService.UserInfo user = userService.getSession(sessionId);
        if (user == null) {
            ctx.sendError(ws, seq, "会话无效");
            return;
        }
        ctx.bind(ws, sessionId);
        ctx.sendSuccess(ws, "auth", seq, "认证成功", null);
        logger.info("WebSocket认证成功, userId: {}, sessionId: {}", user.getUserId(), sessionId);
    }
}