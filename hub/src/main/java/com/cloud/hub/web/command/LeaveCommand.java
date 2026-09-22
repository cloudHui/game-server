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
 * leave 命令：离开牌桌。
 */
@Component
public class LeaveCommand implements WsCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(LeaveCommand.class);

    private final GatewayTransport gateClient;

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
                        logger.error("离开桌子超时, sessionId: {}, seq: {}, msgId: 0x{}, cause: {}",
                                sessionId, seq, Integer.toHexString(GMsg.REQ_LEAVE), error.toString());
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