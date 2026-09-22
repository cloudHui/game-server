package com.cloud.hub.web.command;

import com.cloud.hub.web.service.GatewayTransport;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import proto.GameProto;
import msg.registor.message.GMsg;

import java.util.Map;

/**
 * heartbeat 命令：将浏览器牌桌心跳透传给 Game，无回包。
 */
@Component
public class HeartbeatCommand implements WsCommandHandler {

    private final GatewayTransport gateClient;

    public HeartbeatCommand(GatewayTransport gateClient) {
        this.gateClient = gateClient;
    }

    @Override
    public String action() {
        return "heartbeat";
    }

    @Override
    public void handle(WsContext ctx, WebSocketSession ws, int seq, Map<String, Object> data) {
        String sessionId = ctx.sessionId(ws);
        Number tableId = data == null ? null : (Number) data.get("tableId");
        if (sessionId == null || tableId == null) return;
        gateClient.send(sessionId, GMsg.REQ_TABLE_HEARTBEAT,
                GameProto.ReqTableHeartbeat.newBuilder().setTableId(tableId.longValue()).build());
    }
}