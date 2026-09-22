package com.cloud.hub.web.command;

import com.cloud.hub.web.service.GatewayTransport;
import msg.registor.message.GMsg;
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
 * op 命令：玩家操作（碰/杠/胡/吃/出牌等），结果由广播推送确认。
 */
@Component
public class OpCommand implements WsCommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(OpCommand.class);

    /** Result 错误码 → 可读提示；新增错误码在此追加一行。 */
    private static final Map<Integer, String> RESULT_MESSAGES = new HashMap<>();
    static {
        RESULT_MESSAGES.put(ConstProto.Result.OP_CURR_ERROR_VALUE,   "当前无法操作");
        RESULT_MESSAGES.put(ConstProto.Result.TABLE_NOT_START_VALUE, "牌局未开始");
        RESULT_MESSAGES.put(ConstProto.Result.TIME_OUT_VALUE,        "操作超时");
    }

    private final GatewayTransport gateClient;

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
        GameProto.OpInfo.Builder opBuilder = GameProto.OpInfo.newBuilder().setChoice(opEnum);

        @SuppressWarnings("unchecked")
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

        GameProto.ReqOp request = GameProto.ReqOp.newBuilder().setOp(opBuilder.build()).build();

        gateClient.sendAndWaitTcp(sessionId, GMsg.REQ_OP, request, 5).whenComplete((tcp, error) -> {
            if (error != null) {
                logger.error("操作超时, sessionId: {}, seq: {}, choice: {}, msgId: 0x{}, cause: {}",
                        sessionId, seq, opChoice, Integer.toHexString(GMsg.REQ_OP), error.toString());
                ctx.sendError(ws, seq, "操作超时");
                return;
            }
            try {
                if (tcp.getResult() != 0 && tcp.getResult() != ConstProto.Result.SUCCESS_VALUE) {
                    ctx.sendError(ws, seq, RESULT_MESSAGES.getOrDefault(tcp.getResult(), "操作失败"));
                }
                // 成功时等待广播确认（NotMjState/NotCard），无需额外回包
            } catch (Exception e) {
                logger.error("处理操作响应异常, sessionId: {}, seq: {}", sessionId, seq, e);
                ctx.sendError(ws, seq, "处理响应失败");
            }
        });
    }
}