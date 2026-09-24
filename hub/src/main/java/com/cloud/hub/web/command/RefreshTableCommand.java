package com.cloud.hub.web.command;

import com.cloud.hub.web.handler.GameWsPushFormatter;
import com.cloud.hub.web.service.GatewayTransport;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import proto.GameProto;
import msg.registor.message.GMsg;

import java.util.Map;

/**
 * 刷新牌桌快照命令处理器（refreshTable）。
 * <p>
 * 向底层网关查询当前桌子的最新完整状态快照并推送给客户端同步。
 *
 * @author cloud
 */
@Component
public class RefreshTableCommand implements WsCommandHandler {

    private final GatewayTransport gateClient;

    /**
     * 构造刷新牌桌命令处理器。
     *
     * @param gateClient 网关传输服务
     */
    public RefreshTableCommand(GatewayTransport gateClient) {
        this.gateClient = gateClient;
    }

    @Override
    public String action() {
        return "refreshTable";
    }

    @Override
    public void handle(WsContext ctx, WebSocketSession ws, int seq, Map<String, Object> data) {
        String sessionId = ctx.sessionId(ws);
        Number tableIdValue = data == null ? null : (Number) data.get("tableId");
        if (sessionId == null || tableIdValue == null) {
            ctx.sendError(ws, seq, "无法刷新牌桌");
            return;
        }
        GameProto.ReqTableSnapshot request = GameProto.ReqTableSnapshot.newBuilder()
                .setTableId(tableIdValue.longValue()).build();
        gateClient.sendAndWait(sessionId, GMsg.REQ_TABLE_SNAPSHOT, request, 5)
                .whenComplete((response, error) -> {
                    if (error != null || !(response instanceof GameProto.AckTableSnapshot)) {
                        ctx.sendError(ws, seq, "刷新牌桌失败");
                        return;
                    }
                    ctx.sendSuccess(ws, "refreshTable", seq, "success",
                            GameWsPushFormatter.formatSnapshot((GameProto.AckTableSnapshot) response));
                });
    }
}