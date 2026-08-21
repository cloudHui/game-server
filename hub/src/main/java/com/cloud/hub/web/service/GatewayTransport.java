package com.cloud.hub.web.service;

import com.google.protobuf.Message;
import net.message.TCPMessage;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;

/** Web 到内置网关的统一传输契约。 */
public interface GatewayTransport {
    void bind(String sessionId, int userId, String username, String nickname);
    void removeConnection(String sessionId);
    CompletableFuture<Message> sendAndWait(String sessionId, int msgId, Message msg, int timeoutSeconds);
    CompletableFuture<TCPMessage> sendAndWaitTcp(String sessionId, int msgId, Message msg, int timeoutSeconds);
    void send(String sessionId, int msgId, Message msg);
    boolean isAuthenticated(String sessionId);
    void markAuthenticated(String sessionId);
    void setPushListener(BiConsumer<String, TCPMessage> listener);
}
