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
 * 休闲小游戏 WebSocket 处理器：五子棋 / 象棋匹配与对战。
 * <p>
 * <b>职责边界与使用场景：</b>
 * <ul>
 *   <li>处理客户端的认证、匹配、落子、认输、离开等 WebSocket 消息；</li>
 *   <li>具体棋类的差异操作（落子参数解析、结果构造、快照格式等）全部委托给
 *       {@link MiniGameEngine} 多态处理，本类不包含任何基于 {@code gameType} 的条件分支；</li>
 *   <li>消息格式：{@code {"action":"...", "seq":1, "data":{...}}}。</li>
 * </ul>
 */
@Component
public class MiniGameWebSocketHandler extends TextWebSocketHandler {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(MiniGameWebSocketHandler.class);

    /** JSON 序列化与反序列化工具实例 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 用户鉴权与会话管理服务 */
    private final UserService userService;

    /** WebSocket 连接 ID 到业务会话 Token 的映射表 <WebSocketSessionId, SessionToken> */
    private final Map<String, String> wsToSession = new ConcurrentHashMap<>();

    /** 业务会话 Token 到对应底层 WebSocketSession 物理连接的映射表 <SessionToken, WebSocketSession> */
    private final Map<String, WebSocketSession> sessionToWs = new ConcurrentHashMap<>();

    /** 当前活跃的小游戏房间集合 <RoomId, MiniRoom> */
    private final Map<String, MiniRoom> rooms = new ConcurrentHashMap<>();

    /** 玩家会话 Token 到当前所在房间 ID 的映射表 <SessionToken, RoomId> */
    private final Map<String, String> sessionRoom = new ConcurrentHashMap<>();

    /** 匹配队列并发互斥锁，保护五子棋与象棋排队列表的线程安全 */
    private final Object queueLock = new Object();

    /** 五子棋实时匹配排队队列 */
    private final List<QueueEntry> gomokuQueue = new ArrayList<>();

    /** 中国象棋实时匹配排队队列 */
    private final List<QueueEntry> chessQueue = new ArrayList<>();

    /**
     * 小游戏前端文本动作派发函数接口。
     */
    @FunctionalInterface
    private interface MiniGameActionHandler {
        /**
         * 执行具体的协议动作处理。
         *
         * @param session 当前客户端 WebSocket 会话
         * @param seq     客户端消息序号，用于回包匹配
         * @param data    载荷数据字典
         * @throws Exception 处理异常
         */
        void handle(WebSocketSession session, int seq, Map<String, Object> data) throws Exception;
    }

    /** 动作路由分发表 <动作标识字符串, 对应的动作处理器> */
    private final Map<String, MiniGameActionHandler> actionHandlers = new HashMap<>();

    /**
     * 构造休闲小游戏 WebSocket 处理器并注册支持的路由动作。
     *
     * @param userService 用户信息与会话服务
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

    /**
     * WebSocket 物理连接断开回调：清理映射关系、退出排队队列并处理房间逃跑。
     *
     * @param session 已关闭的 WebSocket 会话
     * @param status  连接关闭状态码及原因
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sid = wsToSession.remove(session.getId());
        if (sid == null) {
            return;
        }
        // 仅移除当前断开的 session，防止同账号重登时误删新连接
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

    /**
     * 接收并分发客户端 WebSocket 文本消息。
     *
     * @param session 当前客户端 WebSocket 会话
     * @param message 文本消息帧载荷
     */
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
     * 处理客户端登录凭证认证（auth）。
     *
     * @param ws   WebSocket 物理连接会话
     * @param seq  客户端消息序号
     * @param data 包含 {@code sessionId} 凭据的请求载荷
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
        // 若存在旧连接（同一账号多端登录或重连），踢掉旧连接保障单点在线
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
     * 处理玩家快速对战匹配（match）。
     *
     * @param ws   WebSocket 物理连接会话
     * @param seq  客户端消息序号
     * @param data 包含 {@code game}（gomoku/chess）等匹配参数的数据载荷
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
        String game = data.get("game") == null ? "" : String.valueOf(data.get("game"));
        MiniRoom.GameType type;
        if ("gomoku".equalsIgnoreCase(game)) {
            type = MiniRoom.GameType.GOMOKU;
        } else if ("chess".equalsIgnoreCase(game)) {
            type = MiniRoom.GameType.CHESS;
        } else {
            sendError(ws, seq, "不支持的游戏: " + game);
            return;
        }
        UserService.UserInfo user = userService.getSession(sid);
        if (user == null) {
            sendError(ws, seq, "会话无效");
            return;
        }

        MiniRoom matched = null;
        synchronized (queueLock) {
            leaveQueueLocked(sid);
            List<QueueEntry> queue = type == MiniRoom.GameType.GOMOKU ? gomokuQueue : chessQueue;
            QueueEntry peer = null;
            Iterator<QueueEntry> it = queue.iterator();
            while (it.hasNext()) {
                QueueEntry e = it.next();
                if (e.sessionId.equals(sid)) {
                    it.remove();
                    continue;
                }
                if (e.userId == user.getUserId()) {
                    continue;
                }
                WebSocketSession peerWs = sessionToWs.get(e.sessionId);
                if (peerWs == null || !peerWs.isOpen()) {
                    it.remove();
                    continue;
                }
                peer = e;
                it.remove();
                break;
            }
            if (peer != null) {
                matched = new MiniRoom(type, peer.sessionId, peer.userId, peer.name,
                        sid, user.getUserId(), displayName(user));
                rooms.put(matched.getRoomId(), matched);
                sessionRoom.put(matched.getPlayerASession(), matched.getRoomId());
                sessionRoom.put(matched.getPlayerBSession(), matched.getRoomId());
            } else {
                queue.add(new QueueEntry(sid, user.getUserId(), displayName(user)));
            }
        }

        if (matched == null) {
            sendOk(ws, "match", seq, "排队中", mapOf("status", "queued", "game", game));
            return;
        }
        sendOk(ws, "match", seq, "匹配成功", mapOf("status", "matched"));
        notifyMatched(matched);
    }

    /**
     * 处理客户端取消排队匹配请求（cancelMatch）。
     *
     * @param ws  WebSocket 物理连接会话
     * @param seq 客户端消息序号
     */
    private void handleCancelMatch(WebSocketSession ws, int seq) {
        String sid = requireAuth(ws, seq);
        if (sid == null) {
            return;
        }
        leaveQueue(sid);
        sendOk(ws, "cancelMatch", seq, "已取消", null);
    }

    /**
     * 处理落子/走棋请求（move）。
     * <p>
     * 参数解析、合法性校验与载荷构造全部委托给 {@link MiniGameEngine#applyMove}，
     * 本方法仅负责房间查找、结果广播与对局结束判定。
     *
     * @param ws   WebSocket 物理连接会话
     * @param seq  客户端消息序号
     * @param data 落子坐标与棋步参数
     */
    private void handleMove(WebSocketSession ws, int seq, Map<String, Object> data) {
        String sid = requireAuth(ws, seq);
        if (sid == null) {
            return;
        }
        String roomId = sessionRoom.get(sid);
        if (roomId == null) {
            sendError(ws, seq, "不在对局中");
            return;
        }
        MiniRoom room = rooms.get(roomId);
        if (room == null) {
            sendError(ws, seq, "房间不存在");
            return;
        }

        // 委托引擎多态处理落子/走棋，消除 gameType 分支
        MiniGameEngine engine = room.getEngine();
        MiniGameEngine.MoveResult result = engine.applyMove(data, room.isSideA(sid));
        if (!result.isOk()) {
            sendError(ws, seq, result.getError());
            return;
        }

        sendOk(ws, "move", seq, "ok", result.getPayload());
        broadcast(room, "move", result.getPayload(), sid);

        // 对局结束判定：统一通过引擎多态查询
        if (engine.isFinished()) {
            finishRoom(room, engine.gameResult());
        }
    }

    /**
     * 处理认输请求（resign）。
     * <p>
     * 委托 {@link MiniGameEngine#resign} 多态处理，消除五子棋/象棋的差异分支。
     *
     * @param ws  WebSocket 物理连接会话
     * @param seq 客户端消息序号
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
     * 处理玩家主动离开房间（leave）。
     *
     * @param ws  WebSocket 物理连接会话
     * @param seq 客户端消息序号
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
     * 处理玩家断连/离开：未结束则视为认输，已结束则仅清理房间。
     * <p>
     * 通过 {@link MiniGameEngine} 多态判断与操作，消除 gameType 分支。
     *
     * @param room 所在小游戏房间
     * @param sid  断连或离开玩家的会话 Token
     */
    private void handleDisconnect(MiniRoom room, String sid) {
        if (!rooms.containsKey(room.getRoomId())) {
            return;
        }
        MiniGameEngine engine = room.getEngine();
        if (!engine.isFinished()) {
            // 断连方视为认输，对手获胜
            Map<String, Object> result = engine.resign(room.isSideA(sid));
            result.put("reason", "对手离开");
            finishRoom(room, result);
        } else {
            cleanupRoom(room);
        }
    }

    /**
     * 结算并销毁对局房间，向双方推送 game_over 结果。
     *
     * @param room   小游戏房间
     * @param result 对局结果字典
     */
    private void finishRoom(MiniRoom room, Map<String, Object> result) {
        broadcast(room, "gameOver", result, null);
        cleanupRoom(room);
    }

    /**
     * 清理房间内存与用户关联映射。
     *
     * @param room 待清理的房间
     */
    private void cleanupRoom(MiniRoom room) {
        rooms.remove(room.getRoomId());
        sessionRoom.remove(room.getPlayerASession(), room.getRoomId());
        sessionRoom.remove(room.getPlayerBSession(), room.getRoomId());
    }

    /**
     * 匹配成功后通知双方玩家。
     * <p>
     * 阵营名称与棋盘快照通过 {@link MiniGameEngine} 多态获取，消除 gameType 分支。
     *
     * @param room 匹配成功的对局房间
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

    /**
     * 构建匹配成功基础信息。
     *
     * @param room 小游戏房间
     * @return 包含 roomId 与 game 名称的基础字典
     */
    private Map<String, Object> baseMatchInfo(MiniRoom room) {
        Map<String, Object> m = new HashMap<>();
        m.put("roomId", room.getRoomId());
        m.put("game", room.getEngine().gameName());
        return m;
    }

    /**
     * 向房间内玩家广播事件消息。
     *
     * @param room          目标房间
     * @param action        广播动作名
     * @param data          载荷数据
     * @param exceptSession 排除的玩家会话 Token（通常为操作发起方），为 null 时全员广播
     */
    private void broadcast(MiniRoom room, String action, Map<String, Object> data, String exceptSession) {
        if (!room.getPlayerASession().equals(exceptSession)) {
            sendEvent(sessionToWs.get(room.getPlayerASession()), action, data);
        }
        if (!room.getPlayerBSession().equals(exceptSession)) {
            sendEvent(sessionToWs.get(room.getPlayerBSession()), action, data);
        }
    }

    /**
     * 查询指定会话玩家当前所在的活跃对局房间。
     *
     * @param sid 玩家会话 Token
     * @return 所在房间，若不在对局中则返回 null
     */
    private MiniRoom currentRoom(String sid) {
        String roomId = sessionRoom.get(sid);
        return roomId == null ? null : rooms.get(roomId);
    }

    /**
     * 安全将指定会话玩家从所有小游戏匹配队列中移除（内部加排队互斥锁）。
     *
     * @param sid 玩家会话 Token
     */
    private void leaveQueue(String sid) {
        synchronized (queueLock) {
            leaveQueueLocked(sid);
        }
    }

    /**
     * 在持有排队互斥锁的上下文中执行队列移除。
     *
     * @param sid 玩家会话 Token
     */
    private void leaveQueueLocked(String sid) {
        gomokuQueue.removeIf(e -> e.sessionId.equals(sid));
        chessQueue.removeIf(e -> e.sessionId.equals(sid));
    }

    /**
     * 校验当前 WebSocket 连接是否已通过身份鉴权。
     *
     * @param ws  WebSocket 物理连接
     * @param seq 当前消息序号
     * @return 校验通过返回对应会话 Token，校验失败则立即向客户端返回错误并返回 null
     */
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

    /**
     * 获取玩家用于显示的昵称或账号名。
     *
     * @param user 用户信息
     * @return 优先返回非空昵称，否则返回用户名
     */
    private static String displayName(UserService.UserInfo user) {
        if (user.getNickname() != null && !user.getNickname().isEmpty()) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    /**
     * 快捷构建键值对 Map。
     *
     * @param kv 连续的键值对参数序列
     * @return Map 对象
     */
    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    /**
     * 向客户端发送成功的应答消息（code=0）。
     *
     * @param ws     WebSocket 连接
     * @param action 动作标识
     * @param seq    对应的请求序号
     * @param msg    提示描述信息
     * @param data   业务数据对象（可为 null）
     */
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

    /**
     * 向客户端发送失败的错误应答（code=-1）。
     *
     * @param ws  WebSocket 连接
     * @param seq 对应的请求序号
     * @param msg 错误原因提示
     */
    private void sendError(WebSocketSession ws, int seq, String msg) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("action", "error");
        resp.put("seq", seq);
        resp.put("code", -1);
        resp.put("msg", msg);
        write(ws, resp);
    }

    /**
     * 向客户端主动推送单向通知事件消息（seq=0）。
     *
     * @param ws     WebSocket 连接
     * @param action 事件名称
     * @param data   事件载荷
     */
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

    /**
     * 将对象转为 JSON 并通过 WebSocket 安全写入客户端连接。
     * <p>
     * 使用 {@code synchronized(ws)} 保障并发写入 Netty/Tomcat WebSocketSession 时的线程安全。
     *
     * @param ws   目标连接
     * @param resp 待序列化的消息字典
     */
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

    /**
     * 实时匹配队列条目实体。
     */
    private static class QueueEntry {
        /** 玩家会话 Token */
        final String sessionId;
        /** 玩家用户 ID */
        final int userId;
        /** 玩家展示名称 */
        final String name;

        /**
         * 构造匹配排队条目。
         *
         * @param sessionId 玩家会话 Token
         * @param userId    玩家用户 ID
         * @param name      玩家展示名称
         */
        QueueEntry(String sessionId, int userId, String name) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.name = name;
        }
    }
}

