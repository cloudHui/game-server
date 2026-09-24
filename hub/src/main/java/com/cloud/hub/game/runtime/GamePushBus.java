package com.cloud.hub.game.runtime;

import net.message.TCPMessage;

import java.util.function.BiConsumer;

/**
 * 游戏内部业务域向同 JVM WebSocket 网关投递消息的桥接总线。
 * <p>
 * 在 Hub 一体化架构中，避免了进程间 TCP 回环序列化开销，直接通过函数式内存槽位 {@link BiConsumer}
 * 将对局下行消息推送到前端长连接通道。
 * </p>
 *
 * @author cloud
 */
public final class GamePushBus {

    /** 绑定的消费槽位（由 WebSocket 网关模块注册） */
    private static volatile BiConsumer<Integer, TCPMessage> sink;

    private GamePushBus() {
    }

    /**
     * 注册/挂载新的下行消息接收端。
     *
     * @param newSink 接收端消费者函数
     */
    public static void install(BiConsumer<Integer, TCPMessage> newSink) {
        sink = newSink;
    }

    /**
     * 清理接收端槽位。
     */
    public static void clear() {
        sink = null;
    }

    /**
     * 向指定玩家投递二进制消息包。
     *
     * @param userId 目标玩家用户 ID
     * @param message 待推送的 TCPMessage
     * @return true 表示成功发布，false 表示当前未安装消费槽位
     */
    public static boolean publish(int userId, TCPMessage message) {
        BiConsumer<Integer, TCPMessage> current = sink;
        if (current == null) {
            return false;
        }
        current.accept(userId, message);
        return true;
    }
}

