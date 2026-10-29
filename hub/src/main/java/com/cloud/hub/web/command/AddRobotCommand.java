package com.cloud.hub.web.command;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.web.service.UserService;

/**
 * 请求向当前等待中的牌桌添加一名陪练机器人命令处理器（addRobot）。
 */
@Component
public class AddRobotCommand implements WsCommandHandler {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(AddRobotCommand.class);

    private final UserService userService;

    public AddRobotCommand(UserService userService) {
        this.userService = userService;
    }

    @Override
    public String action() {
        return "addRobot";
    }

    @Override
    public void handle(WsContext ctx, WebSocketSession ws, int seq, Map<String, Object> data) {
        String sessionId = ctx.sessionId(ws);
        if (sessionId == null) {
            ctx.sendError(ws, seq, "请先认证");
            return;
        }
        UserService.UserInfo user = userService.getSession(sessionId);
        if (user == null) {
            ctx.sendError(ws, seq, "用户未登录");
            return;
        }
        int userId = user.getUserId();
        if (data == null) {
            ctx.sendError(ws, seq, "参数错误");
            return;
        }

        long tableId = parseLong(data.get("tableId"));
        Table table = Game.getInstance().getTableManager().getTable(tableId);
        if (table == null) {
            ctx.sendError(ws, seq, "桌子不存在");
            return;
        }

        table.execute("添加机器人", () -> {
            // 仅允许已在桌内的玩家在首局开局前添加机器人，防止跨桌越权塞入或对局中途加人破坏计分
            if (!table.getUsers().containsKey(userId)) {
                ctx.sendError(ws, seq, "仅桌内玩家可添加机器人");
                return;
            }
            if (table.addOneRobot()) {
                logger.info("玩家申请添加机器人成功, tableId: {}, userId: {}", tableId, userId);
                ctx.sendSuccess(ws, "addRobot", seq, "添加机器人成功", null);
            } else {
                ctx.sendError(ws, seq, "添加机器人失败（座位已满或对局已开始）");
            }
        });
    }

    private static long parseLong(Object raw) {
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        if (raw != null) {
            try {
                return Long.parseLong(String.valueOf(raw).trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
