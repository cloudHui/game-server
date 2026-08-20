package com.cloud.weball.web.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import msg.registor.HandleTypeRegister;
import msg.registor.message.GMsg;
import net.message.TCPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import proto.ConstProto;
import proto.GameProto;
import web.service.GateClient;
import web.service.UserService;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 游戏WebSocket处理器
 * 浏览器通过WebSocket与游戏服务器通信
 * 消息格式: {"action":"xxx","seq":1,"data":{...}}
 */
@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(GameWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UserService userService;
    private final GateClient gateClient;

    /**
     * WebSocketSessionId -> sessionId
     */
    private final Map<String, String> sessionMapping = new ConcurrentHashMap<>();
    /**
     * sessionId -> WebSocketSession（推送用）
     */
    private final Map<String, WebSocketSession> wsBySession = new ConcurrentHashMap<>();

    public GameWebSocketHandler(UserService userService, GateClient gateClient) {
        this.userService = userService;
        this.gateClient = gateClient;
    }

    @PostConstruct
    public void init() {
        gateClient.setPushListener(this::onGatePush);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.info("WebSocket连接建立, wsSessionId: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = sessionMapping.remove(session.getId());
        if (sessionId != null) {
            wsBySession.remove(sessionId, session);
            logger.info("WebSocket连接关闭, sessionId: {}, status: {}", sessionId, status);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String action = "unknown";
        int seq = 0;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
            action = (String) msg.get("action");
            seq = msg.get("seq") != null ? ((Number) msg.get("seq")).intValue() : 0;
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) msg.get("data");

            logger.debug("收到WebSocket消息, action: {}, seq: {}, sessionId: {}", action, seq, session.getId());

            switch (action) {
                case "auth":
                    handleAuth(session, seq, data);
                    break;
                case "enterTable":
                    handleEnterTable(session, seq, data);
                    break;
                case "op":
                    handleOp(session, seq, data);
                    break;
                case "refreshTable":
                    handleRefreshTable(session, seq, data);
                    break;
                case "leave":
                    handleLeave(session, seq);
                    break;
                case "heartbeat":
                    handleHeartbeat(session, data);
                    break;
                default:
                    sendError(session, seq, "未知操作: " + action);
            }
        } catch (Exception e) {
            logger.error("处理WebSocket消息异常, action: {}, seq: {}, sessionId: {}", action, seq, session.getId(), e);
            sendError(session, seq, "消息处理失败");
        }
    }

    private void handleRefreshTable(WebSocketSession wsSession, int seq, Map<String, Object> data) {
        String sessionId = getSessionId(wsSession);
        Number tableIdValue = data == null ? null : (Number) data.get("tableId");
        if (sessionId == null || tableIdValue == null) {
            sendError(wsSession, seq, "无法刷新牌桌");
            return;
        }
        GameProto.ReqTableSnapshot request = GameProto.ReqTableSnapshot.newBuilder()
                .setTableId(tableIdValue.longValue()).build();
        gateClient.sendAndWait(sessionId, GMsg.REQ_TABLE_SNAPSHOT, request, 5)
                .whenComplete((response, error) -> {
                    if (error != null || !(response instanceof GameProto.AckTableSnapshot)) {
                        sendError(wsSession, seq, "刷新牌桌失败");
                        return;
                    }
                    sendResponse(wsSession, "refreshTable", seq, 0, "success",
                            GameWsPushFormatter.formatSnapshot((GameProto.AckTableSnapshot) response));
                });
    }

    private void handleAuth(WebSocketSession wsSession, int seq, Map<String, Object> data) {
        String sessionId = (String) data.get("sessionId");
        if (sessionId == null) {
            sendError(wsSession, seq, "缺少sessionId");
            return;
        }

        UserService.UserInfo user = userService.getSession(sessionId);
        if (user == null) {
            sendError(wsSession, seq, "会话无效");
            return;
        }

        sessionMapping.put(wsSession.getId(), sessionId);
        wsBySession.put(sessionId, wsSession);

        sendResponse(wsSession, "auth", seq, 0, "认证成功", null);
        logger.info("WebSocket认证成功, userId: {}, sessionId: {}", user.getUserId(), sessionId);
    }

    /** 将浏览器牌桌心跳透传给 Game；无回包以保持链路轻量。 */
    private void handleHeartbeat(WebSocketSession wsSession, Map<String, Object> data) {
        String sessionId = getSessionId(wsSession);
        Number tableId = data == null ? null : (Number) data.get("tableId");
        if (sessionId == null || tableId == null) return;
        gateClient.send(sessionId, GMsg.REQ_TABLE_HEARTBEAT,
                GameProto.ReqTableHeartbeat.newBuilder().setTableId(tableId.longValue()).build());
    }

    private void handleEnterTable(WebSocketSession wsSession, int seq, Map<String, Object> data) {
        String sessionId = getSessionId(wsSession);
        if (sessionId == null) {
            sendError(wsSession, seq, "请先认证");
            return;
        }

        Number tableIdNum = (Number) data.get("tableId");
        if (tableIdNum == null) {
            sendError(wsSession, seq, "缺少tableId");
            return;
        }

        long tableId = tableIdNum.longValue();
        UserService.UserInfo user = userService.getSession(sessionId);

        GameProto.ReqEnterTable request = GameProto.ReqEnterTable.newBuilder()
                .setTableId(tableId)
                .setNick(ByteString.copyFromUtf8(user.getNickname()))
                .build();

        CompletableFuture<Message> future = gateClient.sendAndWait(
                sessionId, GMsg.REQ_ENTER_TABLE_MSG, request, 5);

        future.whenComplete((response, error) -> {
            if (error != null) {
                logger.error("进入桌子超时, sessionId: {}, userId: {}, tableId: {}, seq: {}, msgId: 0x{}, cause: {}",
                        sessionId, user.getUserId(), tableId, seq,
                        Integer.toHexString(GMsg.REQ_ENTER_TABLE_MSG), error.toString());
                sendError(wsSession, seq, "进入桌子超时");
                return;
            }

            try {
                if (response instanceof GameProto.AckEnterTable) {
                    GameProto.AckEnterTable ack = (GameProto.AckEnterTable) response;
                    if (!ack.hasTableInfo() || ack.getTableInfo().getTableId() == 0) {
                        sendError(wsSession, seq, "进入桌子失败（座位已满或状态不允许）");
                        return;
                    }
                    Map<String, Object> resultData = new HashMap<>();
                    resultData.put("players", GameWsPushFormatter.formatPlayers(ack.getPlayersList(), user.getUserId()));
                    resultData.put("tableInfo", GameWsPushFormatter.formatTableInfo(ack.getTableInfo()));
                    sendResponse(wsSession, "enterTable", seq, 0, "success", resultData);
                } else {
                    sendError(wsSession, seq, "进入桌子失败");
                }
            } catch (Exception e) {
                logger.error("处理进入桌子响应异常, sessionId: {}, userId: {}, tableId: {}",
                        sessionId, user.getUserId(), tableId, e);
                sendError(wsSession, seq, "处理响应失败");
            }
        });
    }

    private void handleOp(WebSocketSession wsSession, int seq, Map<String, Object> data) {
        String sessionId = getSessionId(wsSession);
        if (sessionId == null) {
            sendError(wsSession, seq, "请先认证");
            return;
        }

        Number opChoice = (Number) data.get("choice");
        if (opChoice == null) {
            sendError(wsSession, seq, "缺少choice");
            return;
        }

        ConstProto.Operation opEnum = ConstProto.Operation.forNumber(opChoice.intValue());
        if (opEnum == null) {
            sendError(wsSession, seq, "无效操作类型: " + opChoice);
            return;
        }
        GameProto.OpInfo.Builder opBuilder = GameProto.OpInfo.newBuilder()
                .setChoice(opEnum);

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

        // 用 TCP 回包：失败时常为无 body 的 Result，Message 解析会丢 result 导致一直等到超时
        CompletableFuture<TCPMessage> future = gateClient.sendAndWaitTcp(
                sessionId, GMsg.REQ_OP, request, 5);

        future.whenComplete((tcp, error) -> {
            if (error != null) {
                logger.error("操作超时, sessionId: {}, seq: {}, choice: {}, msgId: 0x{}, cause: {}",
                        sessionId, seq, opChoice, Integer.toHexString(GMsg.REQ_OP), error.toString());
                sendError(wsSession, seq, "操作超时");
                return;
            }

            try {
                if (tcp.getResult() != 0 && tcp.getResult() != ConstProto.Result.SUCCESS_VALUE) {
                    sendError(wsSession, seq, resultMsg(tcp.getResult()));
                    return;
                }
                if (tcp.getMessageId() == GMsg.ACK_OP && tcp.getMessage() != null && tcp.getMessage().length > 0) {
                    // 成功操作只由桌面正式广播确认并驱动展示，避免请求回包与广播形成双数据源。
                } else if (tcp.getResult() == 0 || tcp.getResult() == ConstProto.Result.SUCCESS_VALUE) {
                    // 麻将成功不发送单独确认，等待 NotMjState/NotCard 广播。
                } else {
                    sendError(wsSession, seq, "操作失败");
                }
            } catch (Exception e) {
                logger.error("处理操作响应异常, sessionId: {}, seq: {}", sessionId, seq, e);
                sendError(wsSession, seq, "处理响应失败");
            }
        });
    }


    /**
     * 将协议 Result 转为可读提示，避免一律显示「操作超时」
     */
    private static String resultMsg(int result) {
        if (result == ConstProto.Result.OP_CURR_ERROR_VALUE) return "当前无法操作";
        if (result == ConstProto.Result.TABLE_NOT_START_VALUE) return "牌局未开始";
        if (result == ConstProto.Result.TIME_OUT_VALUE) return "操作超时";
        return "操作失败";
    }

    private void handleLeave(WebSocketSession wsSession, int seq) {
        String sessionId = getSessionId(wsSession);
        if (sessionId == null) {
            sendError(wsSession, seq, "请先认证");
            return;
        }

        GameProto.ReqLeaveTable request = GameProto.ReqLeaveTable.newBuilder().build();

        CompletableFuture<Message> future = gateClient.sendAndWait(
                sessionId, GMsg.REQ_LEAVE, request, 5);

        future.whenComplete((response, error) -> {
            if (error != null) {
                logger.error("离开桌子超时, sessionId: {}, seq: {}, msgId: 0x{}, cause: {}",
                        sessionId, seq, Integer.toHexString(GMsg.REQ_LEAVE), error.toString());
                sendError(wsSession, seq, "离开桌子超时");
                return;
            }
            if (response instanceof GameProto.AckLeaveTable) {
                sendResponse(wsSession, "leave", seq, 0, "success", null);
                return;
            }
            // 失败时常为 Result 枚举数值消息
            sendError(wsSession, seq, "离开桌子失败");
        });
    }

    /**
     * Gate 推送 → WebSocket
     */
    private void onGatePush(String sessionId, TCPMessage tcpMessage) {
        WebSocketSession ws = wsBySession.get(sessionId);
        if (ws == null || !ws.isOpen()) {
            return;
        }
        try {
            int msgId = tcpMessage.getMessageId();
            Message proto = HandleTypeRegister.parseMessage(msgId,
                    tcpMessage.getMessage() == null ? new byte[0] : tcpMessage.getMessage());
            String action = GameWsPushFormatter.pushAction(msgId);
            if (action == null) {
                return;
            }
            Object data = GameWsPushFormatter.formatPush(msgId, proto);
            sendResponse(ws, action, 0, 0, "push", data);
        } catch (Exception e) {
            logger.error("转发推送失败, sessionId: {}, msgId: 0x{}",
                    sessionId, Integer.toHexString(tcpMessage.getMessageId()), e);
        }
    }

    private String getSessionId(WebSocketSession wsSession) {
        return sessionMapping.get(wsSession.getId());
    }

    private void sendResponse(WebSocketSession session, String action, int seq, int code, String msg, Object data) {
        // 会话已关闭则跳过，避免回调超时后二次报错
        if (session == null || !session.isOpen()) {
            logger.warn("跳过已关闭WebSocket发送, action: {}, seq: {}, sessionId: {}",
                    action, seq, session == null ? null : session.getId());
            return;
        }
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("action", action);
            response.put("seq", seq);
            response.put("code", code);
            response.put("msg", msg);
            if (data != null) {
                response.put("data", data);
            }
            String json = objectMapper.writeValueAsString(response);
            synchronized (session) {
                if (!session.isOpen()) return;
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            logger.warn("发送WebSocket消息失败, action: {}, sessionId: {}, cause: {}",
                    action, session.getId(), e.toString());
        }
    }

    private void sendError(WebSocketSession session, int seq, String errorMsg) {
        sendResponse(session, "error", seq, -1, errorMsg, null);
    }
}
