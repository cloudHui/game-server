package com.cloud.hub.web.command;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.domain.table.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

/**
 * 请求向当前牌桌添加陪练机器人命令处理器（addRobot）。
 *
 * @author cloud
 */
@Component
public class AddRobotCommand implements WsCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(AddRobotCommand.class);

    @Override
    public String action() {
        return "addRobot";
    }

    @Override
    public void handle(WsContext ctx, WebSocketSession ws, int seq, Map<String, Object> data) {
        String sessionId = ctx.sessionId(ws);
        if (sessionId == null || data == null) {
            ctx.sendError(ws, seq, "参数错误");
            return;
        }

        long tableId = data.get("tableId") instanceof Number
                ? ((Number) data.get("tableId")).longValue()
                : 0L;

        Table table = Game.getInstance().getTableManager().getTable(tableId);
        if (table == null) {
            ctx.sendError(ws, seq, "桌子不存在");
            return;
        }

        table.execute("添加机器人", () -> {
            boolean success = table.addOneRobot();
            if (success) {
                logger.info("玩家申请添加机器人成功, tableId: {}, sessionId: {}", tableId, sessionId);
                ctx.send(ws, "addRobot", 0, seq, "添加机器人成功", null);
            } else {
                logger.warn("玩家申请添加机器人失败(桌满), tableId: {}", tableId);
                ctx.sendError(ws, seq, "添加机器人失败（座位已满或已在游戏中）");
            }
        });
    }
}
