package com.cloud.hub.web.service;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

import com.google.protobuf.Message;
import net.message.TCPMessage;

/**
 * Web 服务到底层网关/游戏逻辑的统一传输契约接口。
 * <p>
 * 提供请求同步等待（异步 Future）、广播单向发送、用户会话绑定与网关反向推送监听。
 *
 * @author cloud
 */
public interface GatewayTransport {

    /**
     * 绑定 Web 会话 Token 与具体玩家基本信息。
     *
     * @param sessionId 会话 Token
     * @param userId    用户 ID
     * @param username  用户名
     * @param nickname  用户昵称
     */
    void bind(String sessionId, int userId, String username, String nickname);

    /**
     * 移除会话连接绑定。
     *
     * @param sessionId 会话 Token
     */
    void removeConnection(String sessionId);

    /**
     * 发送 Protobuf 消息并异步等待回包结果。
     *
     * @param sessionId      会话 Token
     * @param msgId          协议消息 ID
     * @param msg            请求 Protobuf 载荷
     * @param timeoutSeconds 超时时间（秒）
     * @return 响应消息 Future
     */
    CompletableFuture<Message> sendAndWait(String sessionId, int msgId, Message msg, int timeoutSeconds);

    /**
     * 发送 Protobuf 消息并异步等待底层 TCPMessage 回包。
     *
     * @param sessionId      会话 Token
     * @param msgId          协议消息 ID
     * @param msg            请求 Protobuf 载荷
     * @param timeoutSeconds 超时时间（秒）
     * @return 响应 TCPMessage Future
     */
    CompletableFuture<TCPMessage> sendAndWaitTcp(String sessionId, int msgId, Message msg, int timeoutSeconds);

    /**
     * 单向异步发送消息（无需等待回包）。
     *
     * @param sessionId 会话 Token
     * @param msgId     协议消息 ID
     * @param msg       请求 Protobuf 载荷
     */
    void send(String sessionId, int msgId, Message msg);

    /**
     * 判断当前会话是否已完成身份认证。
     *
     * @param sessionId 会话 Token
     * @return true 为已认证
     */
    boolean isAuthenticated(String sessionId);

    /**
     * 标记会话为已通过认证状态。
     *
     * @param sessionId 会话 Token
     */
    void markAuthenticated(String sessionId);

    /**
     * 设置网关下发推送消息的全局监听回调。
     *
     * @param listener 双参数回调（sessionId, TCPMessage）
     */
    void setPushListener(BiConsumer<String, TCPMessage> listener);
}
