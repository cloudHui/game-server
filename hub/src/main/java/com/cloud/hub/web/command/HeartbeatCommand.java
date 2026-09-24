package com.cloud.hub.web.command;

import com.cloud.hub.web.service.GatewayTransport;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import proto.GameProto;
import msg.registor.message.GMsg;

import java.util.Map;

/**
 * 牌桌心跳透传命令处理器（heartbeat）。
 * <p>
 * 接收浏览器前端的心跳保活帧并透传给 Game 游戏逻辑层，维持牌桌在线状态。
 *
 * @author cloud
 */
@Component
public class HeartbeatCommand implements WsCommandHandler {

    private final GatewayTransport gateClient;

    /**
     * 构造牌桌心跳命令处理器。
     *
     * @param gateClient 网关传输服务
     */
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
        if (sessionId == null || tableId == null) {
            return;
        }
        gateClient.send(sessionId, GMsg.REQ_TABLE_HEARTBEAT,
                GameProto.ReqTableHeartbeat.newBuilder().setTableId(tableId.longValue()).build());
    }
}