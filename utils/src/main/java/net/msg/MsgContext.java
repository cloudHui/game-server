package net.msg;

import com.google.protobuf.Message;
import net.client.Sender;

/**
 * 强类型消息上下文
 * <p>
 * 统一封装网络接收参数（发送者、客户端ID、序列号、地图/桌子ID等），消除零散入参，并提供多种便捷回包能力。
 *
 * @param <T> 请求消息的 Protobuf 类型
 */
public class MsgContext<T extends Message> {

    /** 网络发送者 */
    private final Sender sender;

    /** 发送方客户端/角色 ID */
    private final int clientId;

    /** 关联的业务主体 ID（如桌子 ID、地图 ID 等） */
    private final long mapId;

    /** 请求序列号（用于异步请求应答匹配） */
    private final int sequence;

    /** 接收到的请求消息 ID */
    private final int msgId;

    /** 注解配置的默认响应消息 ID（若未配置则为 0） */
    private final int defaultAckId;

    /** 反序列化后的强类型 Protobuf 消息对象 */
    private final T msg;

    /**
     * 构造消息上下文
     *
     * @param sender       发送者
     * @param clientId     客户端 ID
     * @param mapId        地图/桌子 ID
     * @param sequence     请求序列号
     * @param msgId        请求消息 ID
     * @param defaultAckId 默认响应消息 ID
     * @param msg          解析后的消息对象
     */
    public MsgContext(Sender sender, int clientId, long mapId, int sequence, int msgId, int defaultAckId, T msg) {
        this.sender = sender;
        this.clientId = clientId;
        this.mapId = mapId;
        this.sequence = sequence;
        this.msgId = msgId;
        this.defaultAckId = defaultAckId;
        this.msg = msg;
    }

    /**
     * 获取发送者实例
     *
     * @return 网络发送端
     */
    public Sender getSender() {
        return sender;
    }

    /**
     * 获取客户端或玩家 ID
     *
     * @return 客户端 ID
     */
    public int getClientId() {
        return clientId;
    }

    /**
     * 获取上下文关联的 mapId 或 tableId
     *
     * @return mapId
     */
    public long getMapId() {
        return mapId;
    }

    /**
     * 获取请求序列号
     *
     * @return 序列号
     */
    public int getSequence() {
        return sequence;
    }

    /**
     * 获取当前消息的 ID
     *
     * @return 消息 ID
     */
    public int getMsgId() {
        return msgId;
    }

    /**
     * 获取默认配置的应答消息 ID
     *
     * @return 应答消息 ID
     */
    public int getDefaultAckId() {
        return defaultAckId;
    }

    /**
     * 获取反序列化后的强类型 Protobuf 消息对象
     *
     * @return 强类型请求消息
     */
    public T getMsg() {
        return msg;
    }

    /**
     * 便捷回发响应消息（使用默认绑定的 ack 消息 ID 和上下文中的 mapId）
     *
     * @param ack 响应消息体
     */
    public void reply(Message ack) {
        if (defaultAckId == 0) {
            throw new IllegalStateException("消息 0x" + Integer.toHexString(msgId) + " 未配置默认 ack 消息 ID，请使用 reply(ackMsgId, ack)");
        }
        reply(defaultAckId, mapId, ack);
    }

    /**
     * 便捷回发响应消息（使用默认绑定的 ack 消息 ID，指定目标 mapId）
     *
     * @param mapId 目标 mapId（如桌子 ID）
     * @param ack   响应消息体
     */
    public void reply(long mapId, Message ack) {
        if (defaultAckId == 0) {
            throw new IllegalStateException("消息 0x" + Integer.toHexString(msgId) + " 未配置默认 ack 消息 ID，请使用 reply(ackMsgId, mapId, ack)");
        }
        reply(defaultAckId, mapId, ack);
    }

    /**
     * 便捷回发响应消息（显式指定 ack 消息 ID，使用上下文中的 mapId）
     *
     * @param ackMsgId 响应消息 ID
     * @param ack      响应消息体
     */
    public void reply(int ackMsgId, Message ack) {
        reply(ackMsgId, this.mapId, ack);
    }

    /**
     * 便捷回发响应消息（显式指定 ack 消息 ID 与目标 mapId）
     *
     * @param ackMsgId 响应消息 ID
     * @param mapId    目标 mapId（如 tableId）
     * @param ack      响应消息体
     */
    public void reply(int ackMsgId, long mapId, Message ack) {
        if (sender != null && ack != null) {
            sender.sendMessage(clientId, ackMsgId, mapId, ack, sequence);
        }
    }

    /**
     * 便捷回发错误响应
     *
     * @param ackMsgId 响应消息 ID
     * @param ack      响应消息体
     */
    public void replyError(int ackMsgId, Message ack) {
        reply(ackMsgId, this.mapId, ack);
    }
}
