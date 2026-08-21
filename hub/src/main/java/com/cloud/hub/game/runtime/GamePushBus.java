package com.cloud.hub.game.runtime;

import net.message.TCPMessage;

import java.util.function.BiConsumer;

/** 将游戏域推送交给同 JVM 的 WebSocket 网关。 */
public final class GamePushBus {
    private static volatile BiConsumer<Integer, TCPMessage> sink;

    private GamePushBus() { }

    public static void install(BiConsumer<Integer, TCPMessage> newSink) { sink = newSink; }
    public static void clear() { sink = null; }

    public static boolean publish(int userId, TCPMessage message) {
        BiConsumer<Integer, TCPMessage> current = sink;
        if (current == null) return false;
        current.accept(userId, message);
        return true;
    }
}
