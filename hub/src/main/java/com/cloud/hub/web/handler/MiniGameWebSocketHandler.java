package com.cloud.hub.web.handler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.cloud.hub.web.minigame.MiniGameEngine;
import com.cloud.hub.web.minigame.MiniRoom;
import com.cloud.hub.web.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 休闲小游戏 WebSocket 处理器：五子棋 / 象棋匹配与实时对战。
 * <p>
 * 负责客户端鉴权、排队匹配、落子走棋、认输和离开房间处理。
 * 差异化对局规则由 {@link MiniGameEngine} 多态承载。
 *
 * @author cloud
 */
@Component
public class MiniGameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(MiniGameWebSocketHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserService userService;

    private final Map<String, String> wsToSession = new ConcurrentHashMap<>();
    private final Map<String, WebSocketSession> sessionToWs = new ConcurrentHashMap<>();
    private final Map<String, MiniRoom> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> sessionRoom = new ConcurrentHashMap<>();

    private final Object queueLock = new Object();
    private final List<QueueEntry> gomokuQueue = new ArrayList<>();
    private final List<QueueEntry> chessQueue = new ArrayList<>();

    @FunctionalInterface
    private interface MiniGameActionHandler {
        void handle(WebSocketSession session, int seq, Map<String, Object> data) throws Exception;
    }

    private final Map<String, MiniGameActionHandler> actionHandlers = new HashMap<>();

    /**
     * 构造小游戏处理器并初始化动作路由映射。
     *
     * @param userService 用户服务
     */
    public MiniGameWebSocketHandler(UserService userService) {
        this.userService = userService;
        actionHandlers.put("auth", this::handleAuth);
        actionHandlers.put("match", this::handleMatch);
        actionHandlers.put("cancelMatch", (s, seq, d) -> handleCancelMatch(s, seq));
        actionHandlers.put("move", this::handleMove);
        actionHandlers.put("resign", (s, seq, d) -> handleResign(s, seq));
        actionHandlers.put("leave", (s, seq, d) -> handleLeave(s, seq));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sid = wsToSession.remove(session.getId());
        if (sid == null) {
            return;
        }
        sessionToWs.remove(sid, session);
        leaveQueue(sid);
        String roomId = sessionRoom.remove(sid);
        if (roomId != null) {
            MiniRoom room = rooms.get(roomId);
            if (room != null) {
                handleDisconnect(room, sid);
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        int seq = 0;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
            String action = (String) msg.get("action");
            seq = msg.get("seq") != null ? ((Number) msg.get("seq")).intValue() : 0;
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) msg.get("data");
            if (data == null) {
                data = new HashMap<>();
            }

            MiniGameActionHandler handler = actionHandlers.get(action == null ? "" : action);
            if (handler != null) {
                handler.handle(session, seq, data);
            } else {
                sendError(session, seq, "未知操作: " + action);
            }
        } catch (Exception e) {
            logger.error("处理小游戏消息失败", e);
            sendError(session, seq, "消息处理失败");
        }
    }

    /**
     * 认证用户会话凭据。
     */
    private void handleAuth(WebSocketSession ws, int seq, Map<String, Object> data) {
        String sessionId = (String) data.get("sessionId");
        if (sessionId == null) {
            sendError(ws, seq, "缺少 sessionId");
            return;
        }
        UserService.UserInfo user = userService.getSession(sessionId);
        if (user == null) {
            sendError(ws, seq, "会话无效");
            return;
        }
        wsToSession.put(ws.getId(), sessionId);
        WebSocketSession old = sessionToWs.put(sessionId, ws);
        if (old != null && old.isOpen() && old != ws) {
            try {
                old.close(CloseStatus.NORMAL);
            } catch (IOException ignored) {
            }
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getUserId());
        payload.put("nickname", user.getNickname());
        sendOk(ws, "auth", seq, "认证成功", payload);
    }

    /**
     * 玩家匹配对战（控制在 30 行以内）。
     */
    private void handleMatch(WebSocketSession ws, int seq, Map<String, Object> data) {
        String sid = requireAuth(ws, seq);
        if (sid == null) {
            return;
        }
        if (sessionRoom.containsKey(sid)) {
            sendError(ws, seq, "已在对局中");
            return;
        }
        MiniRoom.GameType type = resolveGameType(data.get("game"));
        if (type == null) {
            sendError(ws, seq, "不支持的游戏: " + data.get("game"));
            return;
        }
        UserService.UserInfo user = userService.getSession(sid);
        if (user == null) {
            sendError(ws, seq, "会话无效");
            return;
        }

        MiniRoom matched = tryMatchInQueue(sid, user, type);
        if (matched == null) {
            sendOk(ws, "match", seq, "排队中", mapOf("status", "queued", "game", String.valueOf(data.get("game"))));
            return;
        }
        sendOk(ws, "match", seq, "匹配成功", mapOf("status", "matched"));
        notifyMatched(matched);
    }

    private MiniRoom.GameType resolveGameType(Object gameObj) {
        String game = gameObj == null ? "" : String.valueOf(gameObj);
        if ("gomoku".equalsIgnoreCase(game)) {
            return MiniRoom.GameType.GOMOKU;
        }
        if ("chess".equalsIgnoreCase(game)) {
            return MiniRoom.GameType.CHESS;
        }
        return null;
    }

    /**
     * 线程安全地在匹配队列中寻找对手并创建房间。
     */
    private MiniRoom tryMatchInQueue(String sid, UserService.UserInfo user, MiniRoom.GameType type) {
        synchronized (queueLock) {
            leaveQueueLocked(sid);
            List<QueueEntry> queue = type == MiniRoom.GameType.GOMOKU ? gomokuQueue : chessQueue;
            QueueEntry peer = pollAvailablePeer(queue, sid, user.getUserId());
            if (peer != null) {
                MiniRoom matched = new MiniRoom(type, peer.sessionId, peer.userId, peer.name,
                        sid, user.getUserId(), displayName(user));
                rooms.put(matched.getRoomId(), matched);
                sessionRoom.put(matched.getPlayerASession(), matched.getRoomId());
                sessionRoom.put(matched.getPlayerBSession(), matched.getRoomId());
                return matched;
            }
            queue.add(new QueueEntry(sid, user.getUserId(), displayName(user)));
            return null;
        }
    }

    private QueueEntry pollAvailablePeer(List<QueueEntry> queue, String sid, int userId) {
        Iterator<QueueEntry> it = queue.iterator();
        while (it.hasNext()) {
            QueueEntry e = it.next();
            if (e.sessionId.equals(sid)) {
                it.remove();
                continue;
            }
            if (e.userId == userId) {
                continue;
            }
            WebSocketSession peerWs = sessionToWs.get(e.sessionId);
            if (peerWs == null || !peerWs.isOpen()) {
                it.remove();
                continue;
            }
            it.remove();
            return e;
        }
        return null;
    }

    /**
     * 取消排队匹配。
     */
    private void handleCancelMatch(WebSocketSession ws, int seq) {
        String sid = requireAuth(ws, seq);
        if (sid != null) {
            leaveQueue(sid);
            sendOk(ws, "cancelMatch", seq, "已取消", null);
        }
    }

    /**
     * 处理落子或走棋操作。
     */
    private void handleMove(WebSocketSession ws, int seq, Map<String, Object> data) {
        String sid = requireAuth(ws, seq);
        if (sid == null) {
            return;
        }
        MiniRoom room = currentRoom(sid);
        if (room == null) {
            sendError(ws, seq, "不在对局中");
            return;
        }
        MiniGameEngine engine = room.getEngine();
        MiniGameEngine.MoveResult result = engine.applyMove(data, room.isSideA(sid));
        if (!result.isOk()) {
            sendError(ws, seq, result.getError());
            return;
        }
        sendOk(ws, "move", seq, "ok", result.getPayload());
        broadcast(room, "move", result.getPayload(), sid);
        if (engine.isFinished()) {
            finishRoom(room, engine.gameResult());
        }
    }

    /**
     * 处理认输。
     */
    private void handleResign(WebSocketSession ws, int seq) {
        String sid = requireAuth(ws, seq);
        if (sid == null) {
            return;
        }
        MiniRoom room = currentRoom(sid);
        if (room == null) {
            sendError(ws, seq, "不在对局中");
            return;
        }
        MiniGameEngine engine = room.getEngine();
        if (engine.isFinished()) {
            sendError(ws, seq, "对局已结束");
            return;
        }
        Map<String, Object> result = engine.resign(room.isSideA(sid));
        sendOk(ws, "resign", seq, "ok", null);
        finishRoom(room, result);
    }

    /**
     * 处理离开房间。
     */
    private void handleLeave(WebSocketSession ws, int seq) {
        String sid = requireAuth(ws, seq);
        if (sid == null) {
            return;
        }
        leaveQueue(sid);
        String roomId = sessionRoom.remove(sid);
        if (roomId != null) {
            MiniRoom room = rooms.get(roomId);
            if (room != null) {
                handleDisconnect(room, sid);
            }
        }
        sendOk(ws, "leave", seq, "ok", null);
    }

    /**
     * 处理断连或逃跑离开。
     */
    private void handleDisconnect(MiniRoom room, String sid) {
        if (!rooms.containsKey(room.getRoomId())) {
            return;
        }
        MiniGameEngine engine = room.getEngine();
        if (!engine.isFinished()) {
            Map<String, Object> result = engine.resign(room.isSideA(sid));
            result.put("reason", "对手离开");
            finishRoom(room, result);
        } else {
            cleanupRoom(room);
        }
    }

    private void finishRoom(MiniRoom room, Map<String, Object> result) {
        broadcast(room, "gameOver", result, null);
        cleanupRoom(room);
    }

    private void cleanupRoom(MiniRoom room) {
        rooms.remove(room.getRoomId());
        sessionRoom.remove(room.getPlayerASession(), room.getRoomId());
        sessionRoom.remove(room.getPlayerBSession(), room.getRoomId());
    }

    /**
     * 匹配成功通知双方。
     */
    private void notifyMatched(MiniRoom room) {
        MiniGameEngine engine = room.getEngine();
        Map<String, Object> snap = engine.snapshot();

        Map<String, Object> forA = baseMatchInfo(room);
        forA.put("side", engine.sideAName());
        forA.put("youAreA", true);
        forA.put("opponent", room.getPlayerBName());
        forA.put("opponentId", room.getPlayerBUserId());
        forA.putAll(snap);

        Map<String, Object> forB = baseMatchInfo(room);
        forB.put("side", engine.sideBName());
        forB.put("youAreA", false);
        forB.put("opponent", room.getPlayerAName());
        forB.put("opponentId", room.getPlayerAUserId());
        forB.putAll(snap);

        sendEvent(sessionToWs.get(room.getPlayerASession()), "matched", forA);
        sendEvent(sessionToWs.get(room.getPlayerBSession()), "matched", forB);
    }

    private Map<String, Object> baseMatchInfo(MiniRoom room) {
        Map<String, Object> m = new HashMap<>();
        m.put("roomId", room.getRoomId());
        m.put("game", room.getEngine().gameName());
        return m;
    }

    private void broadcast(MiniRoom room, String action, Map<String, Object> data, String exceptSession) {
        if (!room.getPlayerASession().equals(exceptSession)) {
            sendEvent(sessionToWs.get(room.getPlayerASession()), action, data);
        }
        if (!room.getPlayerBSession().equals(exceptSession)) {
            sendEvent(sessionToWs.get(room.getPlayerBSession()), action, data);
        }
    }

    private MiniRoom currentRoom(String sid) {
        String roomId = sessionRoom.get(sid);
        return roomId == null ? null : rooms.get(roomId);
    }

    private void leaveQueue(String sid) {
        synchronized (queueLock) {
            leaveQueueLocked(sid);
        }
    }

    private void leaveQueueLocked(String sid) {
        gomokuQueue.removeIf(e -> e.sessionId.equals(sid));
        chessQueue.removeIf(e -> e.sessionId.equals(sid));
    }

    private String requireAuth(WebSocketSession ws, int seq) {
        String sid = wsToSession.get(ws.getId());
        if (sid == null) {
            sendError(ws, seq, "请先认证");
            return null;
        }
        if (userService.getSession(sid) == null) {
            sendError(ws, seq, "会话无效");
            return null;
        }
        return sid;
    }

    private static String displayName(UserService.UserInfo user) {
        if (user.getNickname() != null && !user.getNickname().isEmpty()) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    private void sendOk(WebSocketSession ws, String action, int seq, String msg, Map<String, Object> data) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("action", action);
        resp.put("seq", seq);
        resp.put("code", 0);
        resp.put("msg", msg);
        if (data != null) {
            resp.put("data", data);
        }
        write(ws, resp);
    }

    private void sendError(WebSocketSession ws, int seq, String msg) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("action", "error");
        resp.put("seq", seq);
        resp.put("code", -1);
        resp.put("msg", msg);
        write(ws, resp);
    }

    private void sendEvent(WebSocketSession ws, String action, Map<String, Object> data) {
        if (ws == null || !ws.isOpen()) {
            return;
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("action", action);
        resp.put("seq", 0);
        resp.put("code", 0);
        resp.put("data", data);
        write(ws, resp);
    }

    private void write(WebSocketSession ws, Map<String, Object> resp) {
        if (ws == null || !ws.isOpen()) {
            return;
        }
        try {
            synchronized (ws) {
                ws.sendMessage(new TextMessage(objectMapper.writeValueAsString(resp)));
            }
        } catch (IOException e) {
            logger.warn("发送小游戏消息失败: {}", e.getMessage());
        }
    }

    private static class QueueEntry {
        final String sessionId;
        final int userId;
        final String name;

        QueueEntry(String sessionId, int userId, String name) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.name = name;
        }
    }
}
