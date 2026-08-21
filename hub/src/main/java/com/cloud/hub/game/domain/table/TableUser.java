package com.cloud.hub.game.domain.table;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.domain.cards.Card;
import com.google.protobuf.Message;
import msg.registor.enums.ServerType;
import net.client.handler.ClientHandler;
import net.message.TCPMessage;
import com.cloud.hub.game.runtime.GamePushBus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @className TableUser
 * @description 游戏用户模型，负责游戏桌子的玩家管理
 * @createDate 2026-05-03
 * @since 1.0
 */
public class TableUser {
    private static final Logger logger = LoggerFactory.getLogger(TableUser.class);

    private final Set<Long> tableIds = new HashSet<>();
    private int userId;
    private boolean online;
    private String head;
    private String nick;
    private int gateId;
    private int seated = -1;
    private boolean robot;
    private boolean webHeartbeatManaged;
    private long lastWebHeartbeatAt;
    private final List<Card> cards;
    // private long diamond;

    /**
     * 创建一个游戏用户
     *
     * @param userId 用户ID
     * @param head   用户头像
     * @param nick   用户昵称
     * @param gateId 用户所在的网关ID
     */
    public TableUser(int userId, String head, String nick, int gateId) {
        this.userId = userId;
        this.head = head;
        this.nick = nick;
        this.gateId = gateId;
        online = true;
        cards = new ArrayList<>();
    }

    public boolean isRobot() {
        return robot;
    }

    public void setRobot(boolean robot) {
        this.robot = robot;
    }

    /** 记录网页牌桌心跳，并恢复该连接对应的在线状态。 */
    public void recordWebHeartbeat(long now) {
        webHeartbeatManaged = true;
        lastWebHeartbeatAt = now;
        setOnLine(true);
    }

    /** 判断已启用网页心跳的玩家是否超过允许的静默时间。 */
    public boolean isWebHeartbeatExpired(long now, long timeoutMillis) {
        return webHeartbeatManaged && now - lastWebHeartbeatAt > timeoutMillis;
    }

    public boolean isWebHeartbeatManaged() {
        return webHeartbeatManaged;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
        logger.debug("设置用户ID: {}", userId);
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnLine(boolean online) {
        boolean changed = this.online != online;
        this.online = online;
        if (changed) {
            logger.debug("更新用户在线状态, userId: {}, online: {}", userId, online);
        }
    }

    public String getHead() {
        return head;
    }

    public void setHead(String head) {
        this.head = head;
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

    public void setGateId(int gateId) {
        this.gateId = gateId;
    }

    public int getSeated() {
        return seated;
    }

    public void setSeated(int seated) {
        int old = this.seated;
        this.seated = seated;
        logger.info("更新用户入座状态, userId: {}, old:{} new:{}", userId, old, seated);
    }

    /**
     * 摸牌
     */
    public void addCards(Card card) {
        cards.add(card);
    }

    public List<Card> getCards() {
        return cards;
    }

    /**
     * 出牌
     */
    public boolean outCards(Card card) {
        return cards.remove(card);
    }

    /**
     * 按牌 id 从手牌移除（出牌校验成功后调用）
     */
    public boolean removeCardsByProtoIds(List<Integer> ids) {
        for (int id : ids) {
            boolean removed = false;
            Iterator<Card> iterator = cards.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getId() == id) {
                    iterator.remove();
                    removed = true;
                    break;
                }
            }
            if (!removed) {
                return false;
            }
        }
        return true;
    }

    public Set<Long> getTableIds() {
        return new HashSet<>(tableIds); // 返回副本避免外部修改
    }

    /**
     * 添加桌子ID到用户
     */
    public void addTable(long tableId) {
        if (tableIds.add(tableId)) {
            logger.info("用户添加桌子, userId: {}, tableId: {}", userId, tableId);
        }
    }

    /**
     * 从用户移除桌子ID
     */
    public void removeTable(long tableId) {
        if (tableIds.remove(tableId)) {
            logger.debug("用户移除桌子, userId: {}, tableId: {}", userId, tableId);
        }
    }

    /**
     * 检查用户是否在指定桌子中
     */
    public boolean isInTable(long tableId) {
        return tableIds.contains(tableId);
    }

    /**
     * 获取用户所在的桌子数量
     */
    public int getTableCount() {
        return tableIds.size();
    }

    /**
     * 清理用户所有桌子关联
     */
    public void clearTables() {
        int count = tableIds.size();
        tableIds.clear();
        logger.debug("清理用户所有桌子关联, userId: {}, 数量: {}", userId, count);
    }

    /**
     * 发送玩家消息
     *
     * @param message 消息
     */
    public void sendRoleMessage(Message message, int messageId, long tableId) {
        sendRoleMessageBytes(messageId, message.toByteArray(), tableId);
    }

    public void sendRoleMessageBytes(int messageId, byte[] payload, long tableId) {
        if (robot) {
            logger.debug("跳过机器人消息, userId: {}, msgId: 0x{}, table:{}", userId, Integer.toHexString(messageId), tableId);
            return;
        }
        TCPMessage localMessage = TCPMessage.newInstance(0, messageId, userId, payload, tableId, 0);
        if (GamePushBus.publish(userId, localMessage)) {
            logger.debug("sendRole:{} local push table:{} msgId:0x{}", userId, tableId,
                    Integer.toHexString(messageId));
            return;
        }
        ClientHandler serverClient = Game.getInstance().getServerClientManager().getServerClient(ServerType.Gate,
                gateId);
        if (serverClient == null) {
            // 入桌时可能误存了 roleId；单网关场景回退到任意 Gate 连接
            serverClient = Game.getInstance().getServerClientManager().getServerClient(ServerType.Gate);
        }

        if (serverClient == null) {
            logger.error("sendRole:{} Message error gate:{} null table:{}", userId, gateId, tableId);
            return;
        }
        // clientId=玩家 roleId，mapId=桌号，供 gate 转发到对应客户端
        serverClient.sendMessage(localMessage);
        logger.info("sendRole:{} Message success gate:{} table:{} msgId:0x{}",
                userId, gateId, tableId, Integer.toHexString(messageId));
    }

    @Override
    public String toString() {
        return "GameUser{" +
                "tableIds=" + tableIds +
                ", userId=" + userId +
                ", online=" + online +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TableUser tableUser = (TableUser) o;
        return userId == tableUser.userId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId);
    }
}
