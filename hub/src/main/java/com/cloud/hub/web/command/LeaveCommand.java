package com.cloud.hub.web.command;

import com.cloud.hub.web.service.GatewayTransport;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import proto.GameProto;

import java.util.Map;

/**
 * 离开牌桌命令处理器（leave）。
 * <p>
 * 向底层网关发送离桌请求，等待 Game 处理完成确认后回包。
 *
 * @author cloud
 */
@Component
public class LeaveCommand implements WsCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(LeaveCommand.class);

    private final GatewayTransport gateClient;

    /**
     * 构造离开牌桌命令处理器。
     *
     * @param gateClient 网关传输服务
     */
    public LeaveCommand(GatewayTransport gateClient) {
        this.gateClient = gateClient;
    }

    @Override
    public String action() {
        return "leave";
    }

    @Override
    public void handle(WsContext ctx, WebSocketSession ws, int seq, Map<String, Object> data) {
        String sessionId = ctx.sessionId(ws);
        if (sessionId == null) {
            ctx.sendError(ws, seq, "请先认证");
            return;
        }
        gateClient.sendAndWait(sessionId, GMsg.REQ_LEAVE, GameProto.ReqLeaveTable.newBuilder().build(), 5)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        logger.error("离开桌子超时, sessionId: {}, seq: {}, cause: {}", sessionId, seq, error.toString());
                        ctx.sendError(ws, seq, "离开桌子超时");
                        return;
                    }
                    if (response instanceof GameProto.AckLeaveTable) {
                        ctx.sendSuccess(ws, "leave", seq, "success", null);
                    } else {
                        ctx.sendError(ws, seq, "离开桌子失败");
                    }
                });
    }
}