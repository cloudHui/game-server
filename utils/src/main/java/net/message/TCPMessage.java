package net.message;

import io.netty.buffer.ByteBuf;
import utils.net.buffer.PooledByteBufUtil;

/**
 * 通用底层 TCP / WebSocket 消息承载报文。
 * <p>
 * 既支持传统的堆内存 {@code byte[]} 载荷，也深度支持 Netty 池化 {@link ByteBuf} 零拷贝传输，
 * 在进程内网关与长连接编解码管线中流通。
 *
 * @author cloud
 */
public class TCPMessage {

    /** 响应结果码（0 表示成功，非 0 表示各类业务错误码） */
    private int result;

    /** 消息协议 ID（如 GMsg / LMsg 中注册的消息代号） */
    private int messageId;

    /** 客户端/连接端标识符 */
    private int clientId;

    /** 业务场景地图或房间桌子标识（若无则默认为 0） */
    private long mapId;

    /** 请求序列号（流水号，用于请求-响应匹配） */
    private int sequence;

    /** 兼容传统模式的堆内存字节数组载荷 */
    private byte[] message = null;

    /** 高性能 Netty 池化或堆外直接缓冲区载荷（为 null 时回退使用 message） */
    private ByteBuf byteBuf = null;

    public static TCPMessage newInstance(int result) {
        return new TCPMessage(result);
    }

    public static TCPMessage newInstance(int messageId, byte[] message) {
        return new TCPMessage(messageId, message, 0);
    }

    public static TCPMessage newInstance(int messageId, byte[] message, int sequence) {
        return new TCPMessage(messageId, message, sequence);
    }

    public static TCPMessage newInstance(int result, int messageId, int clientId, byte[] message) {
        return new TCPMessage(result, messageId, clientId, message);
    }

    public static TCPMessage newInstance(int result, int messageId, int clientId, byte[] message, int sequence) {
        return new TCPMessage(result, messageId, clientId, message, sequence);
    }

    public static TCPMessage newInstance(int result, int messageId, int clientId, byte[] message, long mapId, int sequence) {
        return new TCPMessage(result, messageId, clientId, message, mapId, sequence);
    }

    public static TCPMessage newInstance(int messageId, int clientId, byte[] message, long mapId, int sequence) {
        return new TCPMessage(0, messageId, clientId, message, mapId, sequence);
    }

    public static TCPMessage newInstance(int result, int messageId, int clientId, ByteBuf byteBuf, long mapId, int sequence) {
        return new TCPMessage(result, messageId, clientId, byteBuf, mapId, sequence);
    }

    public static TCPMessage newInstance(int messageId, ByteBuf byteBuf, int sequence) {
        return new TCPMessage(messageId, byteBuf, sequence);
    }

    public TCPMessage(int result) {
        this.result = result;
    }

    public TCPMessage(int messageId, byte[] message, int sequence) {
        this.messageId = messageId;
        this.message = message;
        this.sequence = sequence;
    }

    public TCPMessage(int messageId, ByteBuf byteBuf, int sequence) {
        this.messageId = messageId;
        this.byteBuf = byteBuf;
        this.sequence = sequence;
    }

    public TCPMessage(int result, int messageId, int clientId, byte[] message) {
        this.result = result;
        this.messageId = messageId;
        this.clientId = clientId;
        this.message = message;
    }

    public TCPMessage(int result, int messageId, int clientId, byte[] message, int sequence) {
        this.result = result;
        this.messageId = messageId;
        this.clientId = clientId;
        this.message = message;
        this.sequence = sequence;
    }

    public TCPMessage(int result, int messageId, int clientId, byte[] message, long mapId, int sequence) {
        this.result = result;
        this.messageId = messageId;
        this.clientId = clientId;
        this.message = message;
        this.mapId = mapId;
        this.sequence = sequence;
    }

    public TCPMessage(int result, int messageId, int clientId, ByteBuf byteBuf, long mapId, int sequence) {
        this.result = result;
        this.messageId = messageId;
        this.clientId = clientId;
        this.byteBuf = byteBuf;
        this.mapId = mapId;
        this.sequence = sequence;
    }

    public TCPMessage(int messageId, int clientId, byte[] message, long mapId, int sequence) {
        this.messageId = messageId;
        this.clientId = clientId;
        this.message = message;
        this.mapId = mapId;
        this.sequence = sequence;
    }

    public int getResult() {
        return this.result;
    }

    public void setResult(int result) {
        this.result = result;
    }

    public int getMessageId() {
        return this.messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public int getClientId() {
        return this.clientId;
    }

    public void setClientId(int clientId) {
        this.clientId = clientId;
    }

    /**
     * 获取载荷字节数组。
     * <p>
     * 若当前载荷为 ByteBuf 形式，则动态导出字节数组以维持向后兼容。
     *
     * @return 载荷字节数组
     */
    public byte[] getMessage() {
        if (this.message != null) {
            return this.message;
        }
        if (this.byteBuf != null && this.byteBuf.isReadable()) {
            // 为向后兼容必须调用 getMessage() 的老代码提供兜底转换，并保留原 readerIndex
            byte[] copy = new byte[this.byteBuf.readableBytes()];
            int readerIdx = this.byteBuf.readerIndex();
            this.byteBuf.getBytes(readerIdx, copy);
            return copy;
        }
        return null;
    }

    public void setMessage(byte[] message) {
        this.message = message;
    }

    /**
     * 判断当前报文是否包含直接的 ByteBuf 池化载荷。
     *
     * @return 是否包含 ByteBuf
     */
    public boolean hasByteBuf() {
        return this.byteBuf != null;
    }

    /**
     * 获取 Netty ByteBuf 载荷。
     *
     * @return ByteBuf 实例
     */
    public ByteBuf getByteBuf() {
        return this.byteBuf;
    }

    public void setByteBuf(ByteBuf byteBuf) {
        this.byteBuf = byteBuf;
    }

    /**
     * 获取载荷的有效二进制长度（自动适配 ByteBuf 与 byte[]）。
     *
     * @return 载荷字节数
     */
    public int getPayloadLength() {
        if (this.byteBuf != null) {
            return this.byteBuf.readableBytes();
        }
        return this.message == null ? 0 : this.message.length;
    }

    /**
     * 安全释放内部持有的池化 ByteBuf 资源。
     */
    public void release() {
        if (this.byteBuf != null) {
            PooledByteBufUtil.safeRelease(this.byteBuf);
            this.byteBuf = null;
        }
    }

    public long getMapId() {
        return mapId;
    }

    public void setMapId(long mapId) {
        this.mapId = mapId;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    @Override
    public String toString() {
        return "TCPMessage{" +
                "result=" + result +
                ", messageId=" + messageId +
                ", sequence=" + sequence +
                ", hasByteBuf=" + hasByteBuf() +
                '}';
    }
}
