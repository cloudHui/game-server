package com.cloud.hub.lobby.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ModelProto;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 大厅在线用户会话实体。
 * <p>
 * 维护玩家在大厅中的网络网关路由 ID（gateId / clientId）、活跃心跳时间戳、
 * 临时待消费令牌、离线状态以及当前正在参与的桌子 ID 集合。
 * </p>
 *
 * @author cloud
 */
public class User {

    private static final Logger logger = LoggerFactory.getLogger(User.class);

    /** 玩家唯一全局用户 ID */
    private final long userId;
    /** 用户账号名 */
    private String username;
    /** 用户昵称 */
    private String nick;
    /** 绑定的网关连接通道 ID */
    private int gateId;
    /** 最近一次活跃/心跳更新时间戳（毫秒） */
    private long lastActiveTime;
    /** 登录认证中暂存的一次性令牌 */
    private String pendingToken;
    /** 是否正在游戏中 */
    private boolean joinGame;
    /** 是否已判定为网络离线断开 */
    private boolean offline;
    /** 当前用户所加入的桌子 ID 线程安全集合 */
    private final Set<Long> tables = ConcurrentHashMap.newKeySet();

    /**
     * 构造大厅在线用户对象。
     *
     * @param userId 用户 ID
     * @param username 账号名
     * @param nick 昵称
     * @param gateId 网关通道 ID
     */
    public User(long userId, String username, String nick, int gateId) {
        this.userId = userId;
        this.username = username;
        this.nick = nick;
        this.gateId = gateId;
        this.lastActiveTime = System.currentTimeMillis();
        logger.debug("创建用户, userId: {}, username: {}, gateId: {}", userId, username, gateId);
    }

    public long getUserId() {
        return userId;
    }

    public int getUserIdInt() {
        return (int) userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNick() {
        return nick;
    }

    public void setNick(String nick) {
        this.nick = nick;
    }

    public int getGateId() {
        return gateId;
    }

    /**
     * 兼容房间与网关侧的 clientId 命名访问。
     *
     * @return 网关通道 ID
     */
    public int getClientId() {
        return gateId;
    }

    public void setGateId(int gateId) {
        this.gateId = gateId;
        updateActiveTime();
    }

    public long getLastActiveTime() {
        return lastActiveTime;
    }

    /**
     * 刷新最近活跃时间戳为当前时刻。
     */
    public void updateActiveTime() {
        this.lastActiveTime = System.currentTimeMillis();
    }

    public String getPendingToken() {
        return pendingToken;
    }

    public void setPendingToken(String pendingToken) {
        this.pendingToken = pendingToken;
    }

    /**
     * 原子消费暂存的 Token（取出后置空）。
     *
     * @return 之前暂存的 Token 字符串
     */
    public synchronized String consumePendingToken() {
        String token = this.pendingToken;
        this.pendingToken = null;
        return token;
    }

    public boolean getJoinGame() {
        return joinGame;
    }

    public void setJoinGame(boolean joinGame) {
        this.joinGame = joinGame;
    }

    public boolean getOffline() {
        return offline;
    }

    public void setOffline(boolean offline) {
        this.offline = offline;
    }

    /**
     * 构建 Protobuf RoomRole 角色视图对象。
     *
     * @return Protobuf 角色对象
     */
    public ModelProto.RoomRole getRole() {
        return ModelProto.RoomRole.newBuilder()
                .setRoleId(getUserIdInt())
                .build();
    }

    /**
     * 记录用户加入指定桌子。
     *
     * @param tableId 桌子唯一 ID
     */
    public void addTable(long tableId) {
        tables.add(tableId);
        logger.info("用户添加桌子, userId: {}, tableId: {}", userId, tableId);
    }

    /**
     * 记录用户离开指定桌子。
     *
     * @param tableId 桌子唯一 ID
     */
    public void removeTable(long tableId) {
        if (tables.remove(tableId)) {
            logger.info("用户移除桌子, userId: {}, tableId: {}", userId, tableId);
        }
    }

    /**
     * 获取用户当前参与的所有桌子 ID 列表副本。
     *
     * @return 桌子 ID 列表
     */
    public List<Long> getAllTables() {
        return new ArrayList<>(tables);
    }

    /**
     * 销毁并清空用户参与的所有桌子引用。
     */
    public void destroy() {
        tables.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return userId == user.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}

