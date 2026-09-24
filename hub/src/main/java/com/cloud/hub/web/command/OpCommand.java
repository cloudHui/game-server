package com.cloud.hub.web.command;

import com.cloud.hub.web.service.GatewayTransport;
import msg.registor.message.GMsg;
import net.message.TCPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import proto.ConstProto;
import proto.GameProto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 玩家出牌/碰杠胡等操作命令处理器（op）。
 * <p>
 * 接收客户端选择的操作类型与所选牌型，通过网关转发至具体牌桌引擎处理。
 *
 * @author cloud
 */
@Component
public class OpCommand implements WsCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(OpCommand.class);

    private static final Map<Integer, String> RESULT_MESSAGES = new HashMap<>();
    static {
        RESULT_MESSAGES.put(ConstProto.Result.OP_CURR_ERROR_VALUE,   "当前无法操作");
        RESULT_MESSAGES.put(ConstProto.Result.TABLE_NOT_START_VALUE, "牌局未开始");
        RESULT_MESSAGES.put(ConstProto.Result.TIME_OUT_VALUE,        "操作超时");
    }

    private final GatewayTransport gateClient;

    /**
     * 构造操作命令处理器。
     *
     * @param gateClient 网关传输服务
     */
    public OpCommand(GatewayTransport gateClient) {
        this.gateClient = gateClient;
    }

    @Override
    public String action() {
        return "op";
    }

    @Override
    public void handle(WsContext ctx, WebSocketSession ws, int seq, Map<String, Object> data) {
        String sessionId = ctx.sessionId(ws);
        if (sessionId == null) {
            ctx.sendError(ws, seq, "请先认证");
            return;
        }
        Number opChoice = (Number) data.get("choice");
        if (opChoice == null) {
            ctx.sendError(ws, seq, "缺少choice");
            return;
        }
        ConstProto.Operation opEnum = ConstProto.Operation.forNumber(opChoice.intValue());
        if (opEnum == null) {
            ctx.sendError(ws, seq, "无效操作类型: " + opChoice);
            return;
        }

        GameProto.ReqOp request = GameProto.ReqOp.newBuilder().setOp(buildOpInfo(opEnum, data)).build();
        gateClient.sendAndWaitTcp(sessionId, GMsg.REQ_OP, request, 5)
                .whenComplete((tcp, error) -> handleTcpResponse(ctx, ws, seq, sessionId, opChoice, tcp, error));
    }

    /**
     * 构建操作描述对象 OpInfo（含操作牌型列表）。
     */
    @SuppressWarnings("unchecked")
    private GameProto.OpInfo buildOpInfo(ConstProto.Operation opEnum, Map<String, Object> data) {
        GameProto.OpInfo.Builder opBuilder = GameProto.OpInfo.newBuilder().setChoice(opEnum);
        List<Map<String, Object>> cards = (List<Map<String, Object>>) data.get("cards");
        if (cards != null) {
            GameProto.CardInfo.Builder cardInfo = GameProto.CardInfo.newBuilder();
            for (Map<String, Object> card : cards) {
                Number value = (Number) card.get("value");
                if (value != null) {
                    cardInfo.addCards(GameProto.Card.newBuilder().setValue(value.intValue()).build());
                }
            }
            opBuilder.addOpCards(cardInfo.build());
        }
        return opBuilder.build();
    }

    /**
     * 处理操作响应。
     */
    private void handleTcpResponse(WsContext ctx, WebSocketSession ws, int seq, String sessionId,
                                   Number opChoice, TCPMessage tcp, Throwable error) {
        if (error != null) {
            logger.error("操作超时, sessionId: {}, seq: {}, choice: {}, cause: {}",
                    sessionId, seq, opChoice, error.toString());
            ctx.sendError(ws, seq, "操作超时");
            return;
        }
        try {
            if (tcp.getResult() != 0 && tcp.getResult() != ConstProto.Result.SUCCESS_VALUE) {
                ctx.sendError(ws, seq, RESULT_MESSAGES.getOrDefault(tcp.getResult(), "操作失败"));
            }
        } catch (Exception e) {
            logger.error("处理操作响应异常, sessionId: {}, seq: {}", sessionId, seq, e);
            ctx.sendError(ws, seq, "处理响应失败");
        }
    }
}