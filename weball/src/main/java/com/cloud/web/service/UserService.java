package com.cloud.web.service;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import msg.registor.message.LMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import proto.LobbyProto;
import web.account.AccountService;
import web.account.AccountUser;
import web.service.GateClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * 用户会话管理：经 Gate 访问 Lobby 登录/注册/房间
 */
@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    private final GateClient gateClient;
    private final AccountService accounts;
    private final ReentrantLock sessionLock = new ReentrantLock();

    private final Map<String, UserInfo> sessions = new ConcurrentHashMap<>();
    private final Map<String, UserInfo> tokenSessions = new ConcurrentHashMap<>();
    private final Map<Integer, String> userSessions = new ConcurrentHashMap<>();

    public UserService(GateClient gateClient, AccountService accounts) {
        this.gateClient = gateClient;
        this.accounts = accounts;
    }

    public UserInfo login(String username, String password) {
        String sessionId = UUID.randomUUID().toString();
        logger.info("用户登录请求, username: {}, sessionId: {}", username, sessionId);
        return accounts.authenticate(username, password)
                .map(account -> session(account, sessionId))
                .orElse(null);
    }

    public UserInfo register(String username, String password, String nickname, String invite) {
        String sessionId = UUID.randomUUID().toString();
        logger.info("用户注册请求, username: {}, sessionId: {}", username, sessionId);
        AccountUser[] created = new AccountUser[1];
        int code = accounts.register(username, password, nickname, invite, created);
        if (code == AccountService.CODE_OK) {
            return session(created[0], sessionId);
        }
        UserInfo failed = new UserInfo(sessionId, 0, username, "", "",
                Collections.emptyList(), Collections.emptyList());
        failed.setErrorCode(code);
        return failed;
    }

    public UserInfo validateToken(String token) {
        UserInfo info = tokenSessions.get(token);
        if (info != null) {
            return info;
        }
        String sessionId = UUID.randomUUID().toString();
        return accounts.authenticateByToken(token)
                .map(account -> session(account, sessionId))
                .orElse(null);
    }

    private UserInfo session(AccountUser account, String sessionId) {
        return storeSession(sessionId, Math.toIntExact(account.id), account.username,
                account.nickname, account.token, Collections.<Long>emptyList(),
                Collections.<TableInfoView>emptyList());
    }

    private UserInfo storeSession(String sessionId, int userId, String username,
                                  String nickname, String token, List<Long> tables,
                                  List<TableInfoView> tableInfos) {
        List<Long> tableList = tables == null ? Collections.emptyList() : new ArrayList<>(tables);
        List<TableInfoView> infos = tableInfos == null ? Collections.emptyList() : tableInfos;
        if (infos.isEmpty() && !tableList.isEmpty()) {
            infos = new ArrayList<>();
            for (Long id : tableList) {
                infos.add(new TableInfoView(id, 0, 0));
            }
        }
        UserInfo userInfo = new UserInfo(sessionId, userId, username, nickname, token, tableList, infos);
        sessionLock.lock();
        try {
            String oldSession = userSessions.put(userId, sessionId);
            if (oldSession != null && !oldSession.equals(sessionId)) {
                sessions.remove(oldSession);
                gateClient.removeConnection(oldSession);
                logger.info("踢掉同用户旧会话, userId: {}, oldSession: {}", userId, oldSession);
            }
            sessions.put(sessionId, userInfo);
            tokenSessions.put(token, userInfo);
        } finally {
            sessionLock.unlock();
        }
        logger.info("会话建立, userId: {}, username: {}, tables: {}, sessionId: {}",
                userId, username, tableList.size(), sessionId);
        return userInfo;
    }

    public UserInfo getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public boolean changePassword(String sessionId, String oldPassword, String newPassword) {
        UserInfo user = sessions.get(sessionId);
        return user != null && accounts.changePassword(user.getUserId(), oldPassword, newPassword);
    }

    public void logout(String sessionId) {
        UserInfo info;
        sessionLock.lock();
        try {
            info = sessions.remove(sessionId);
            if (info != null) {
                tokenSessions.remove(info.getToken());
                userSessions.remove(info.getUserId(), sessionId);
            }
        } finally {
            sessionLock.unlock();
        }
        if (info != null) {
            gateClient.removeConnection(sessionId);
            logger.info("用户登出, userId: {}, sessionId: {}", info.getUserId(), sessionId);
        }
    }

    public void setPushListener(BiConsumer<String, net.message.TCPMessage> listener) {
        gateClient.setPushListener(listener);
    }

    public CompletableFuture<Message> getRoomList(String sessionId) {
        LobbyProto.ReqRoomList request = LobbyProto.ReqRoomList.newBuilder().build();
        return sendAuthenticated(sessionId, LMsg.REQ_ROOM_LIST_MSG, request);
    }

    public CompletableFuture<Message> joinTable(String sessionId, int roomId) {
        LobbyProto.ReqJoinRoomTable request = LobbyProto.ReqJoinRoomTable.newBuilder()
                .setRoomId(roomId)
                .build();
        return sendAuthenticated(sessionId, LMsg.REQ_JOIN_ROOM_TABLE_MSG, request);
    }

    /**
     * 发送需要玩家身份的请求。
     *
     * <p>Gate 的玩家身份绑定在 TCP 连接上，而 Web 会话只保存在 Web 进程内。
     * Gate 重启、网络闪断或空闲连接被关闭后，原来的 sessionId 仍然有效，
     * 但新 TCP 连接的 roleId 会回到 0。此时直接发送房间请求会被 Gate 以
     * “不是安全的消息 ID”拒绝。这里在新连接上先用 token 静默登录，再发送
     * 原始请求，避免用户必须重新刷新登录页面。</p>
     */
    private CompletableFuture<Message> sendAuthenticated(String sessionId, int messageId, Message request) {
        UserInfo user = sessions.get(sessionId);
        if (user == null) {
            return failedFuture(new IllegalStateException("会话不存在"));
        }
        if (gateClient.isAuthenticated(sessionId)) {
            return gateClient.sendAndWait(sessionId, messageId, request, 5);
        }

        LobbyProto.ReqLogin relogin = LobbyProto.ReqLogin.newBuilder()
                .setUsername(ByteString.EMPTY)
                .setPassword(ByteString.EMPTY)
                .setToken(ByteString.copyFromUtf8(user.getToken()))
                .build();
        return gateClient.sendAndWait(sessionId, LMsg.REQ_LOGIN_MSG, relogin, 5)
                .thenCompose(response -> {
                    if (!(response instanceof LobbyProto.AckLogin)
                            || ((LobbyProto.AckLogin) response).getCode() != 0) {
                        return failedFuture(new IllegalStateException("Gate 会话重新认证失败"));
                    }
                    gateClient.markAuthenticated(sessionId);
                    return gateClient.sendAndWait(sessionId, messageId, request, 5);
                });
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    public static class TableInfoView {
        private final long tableId;
        private final int roomId;
        private final int gameType;

        public TableInfoView(long tableId, int roomId, int gameType) {
            this.tableId = tableId;
            this.roomId = roomId;
            this.gameType = gameType;
        }

        public long getTableId() {
            return tableId;
        }

        public int getRoomId() {
            return roomId;
        }

        public int getGameType() {
            return gameType;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new HashMap<>();
            m.put("tableId", tableId);
            m.put("roomId", roomId);
            m.put("gameType", gameType);
            return m;
        }
    }

    public static class UserInfo {
        private final String sessionId;
        private final int userId;
        private final String username;
        private final String nickname;
        private final String token;
        private final List<Long> tables;
        private final List<TableInfoView> tableInfos;
        private int errorCode;

        public UserInfo(String sessionId, int userId, String username, String nickname,
                        String token, List<Long> tables, List<TableInfoView> tableInfos) {
            this.sessionId = sessionId;
            this.userId = userId;
            this.username = username == null ? "" : username;
            this.nickname = nickname;
            this.token = token;
            this.tables = tables == null ? Collections.emptyList() : tables;
            this.tableInfos = tableInfos == null ? Collections.emptyList() : tableInfos;
        }

        public String getSessionId() {
            return sessionId;
        }

        public int getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public String getNickname() {
            return nickname;
        }

        public String getToken() {
            return token;
        }

        public List<Long> getTables() {
            return tables;
        }

        public List<TableInfoView> getTableInfos() {
            return tableInfos;
        }

        public boolean isAdmin() {
            return "admin".equals(username);
        }

        public int getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(int errorCode) {
            this.errorCode = errorCode;
        }
    }
}
