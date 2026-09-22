package com.cloud.hub.web.command;

import com.cloud.hub.web.handler.GameWsPushFormatter;
import com.cloud.hub.web.service.GatewayTransport;
import com.cloud.hub.web.service.UserService;
import com.google.protobuf.ByteString;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import proto.GameProto;

import java.util.HashMap;
import java.util.Map;

/**
 * enterTable 命令：请求进入桌子，返回玩家列表与桌子信息。
 */
@Component
public class EnterTableCommand implements WsCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(EnterTableCommand.class);

    private final GatewayTransport gateClient;
    private final UserService userService;

    public EnterTableCommand(GatewayTransport gateClient, UserService userService) {
        this.gateClient = gateClient;
        this.userService = userService;
    }

    @Override
    public String action() {
        return "enterTable";
    }

    @Override
    public void handle(WsContext ctx, WebSocketSession ws, int seq, Map<String, Object> data) {
        String sessionId = ctx.sessionId(ws);
        if (sessionId == null) {
            ctx.sendError(ws, seq, "请先认证");
            return;
        }
        Number tableIdNum = (Number) data.get("tableId");
        if (tableIdNum == null) {
            ctx.sendError(ws, seq, "缺少tableId");
            return;
        }
        long tableId = tableIdNum.longValue();
        UserService.UserInfo user = userService.getSession(sessionId);

        GameProto.ReqEnterTable request = GameProto.ReqEnterTable.newBuilder()
                .setTableId(tableId)
                .setNick(ByteString.copyFromUtf8(user.getNickname()))
                .build();

        gateClient.sendAndWait(sessionId, GMsg.REQ_ENTER_TABLE_MSG, request, 5)
                .whenComplete((response, error) -> {
                    if (error != null) {
                        logger.error("进入桌子超时, sessionId: {}, userId: {}, tableId: {}, seq: {}, msgId: 0x{}, cause: {}",
                                sessionId, user.getUserId(), tableId, seq,
                                Integer.toHexString(GMsg.REQ_ENTER_TABLE_MSG), error.toString());
                        ctx.sendError(ws, seq, "进入桌子超时");
                        return;
                    }
                    try {
                        if (response instanceof GameProto.AckEnterTable) {
                            GameProto.AckEnterTable ack = (GameProto.AckEnterTable) response;
                            if (!ack.hasTableInfo() || ack.getTableInfo().getTableId() == 0) {
                                ctx.sendError(ws, seq, "进入桌子失败（座位已满或状态不允许）");
                                return;
                            }
                            Map<String, Object> resultData = new HashMap<>();
                            resultData.put("players", GameWsPushFormatter.formatPlayers(ack.getPlayersList(), user.getUserId()));
                            resultData.put("tableInfo", GameWsPushFormatter.formatTableInfo(ack.getTableInfo()));
                            ctx.sendSuccess(ws, "enterTable", seq, "success", resultData);
                        } else {
                            ctx.sendError(ws, seq, "进入桌子失败");
                        }
                    } catch (Exception e) {
                        logger.error("处理进入桌子响应异常, sessionId: {}, userId: {}, tableId: {}", sessionId, user.getUserId(), tableId, e);
                        ctx.sendError(ws, seq, "处理响应失败");
                    }
                });
    }
}