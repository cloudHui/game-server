package net.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

import net.message.TCPMessage;

/**
 * TCP 二进制网络消息编码器。
 * <p>
 * 将 {@link TCPMessage} 转换为底层字节流。深度适配 Netty 池化 {@link ByteBuf}，
 * 若报文包含直接缓冲区，则直接零拷贝写入，避免传统的 byte 数组中转开销。
 *
 * @author cloud
 */
public class TCPMessageEncoder extends MessageToByteEncoder<TCPMessage> {

    public TCPMessageEncoder() {
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, TCPMessage msg, ByteBuf out) {
        out.writeIntLE(msg.getResult());
        out.writeIntLE(msg.getMessageId());

        int length = msg.getPayloadLength();
        out.writeIntLE(length);
        out.writeIntLE(msg.getClientId());
        out.writeLongLE(msg.getMapId());
        out.writeIntLE(msg.getSequence());

        if (length > 0) {
            // 优先利用池化 ByteBuf 进行内存切片传输，彻底避免堆内存中间数组分配
            if (msg.hasByteBuf()) {
                ByteBuf body = msg.getByteBuf();
                out.writeBytes(body, body.readerIndex(), body.readableBytes());
            } else if (msg.getMessage() != null) {
                out.writeBytes(msg.getMessage());
            }
        }
    }
}
