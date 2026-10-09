package net.codec;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;

import net.message.TCPMessage;

/**
 * WebSocket 二进制帧消息编码器。
 * <p>
 * 将 {@link TCPMessage} 封装为 {@link BinaryWebSocketFrame}。
 * 采用 Channel 上下文绑定的池化分配器（{@link ChannelHandlerContext#alloc()}），
 * 并优先复用消息体持有的直接 {@link ByteBuf}，实现极低 GC 压力的帧打包。
 *
 * @author cloud
 */
public class WSTCPMessageEncoder extends MessageToMessageEncoder<TCPMessage> {

    public WSTCPMessageEncoder() {
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, TCPMessage msg, List<Object> out) {
        int length = msg.getPayloadLength();

        // 采用 Channel 绑定的内存池分配器代替 Unpooled 堆分配，大幅降低高频推送下的 GC 暂停
        ByteBuf buf = ctx.alloc().buffer(length + 20);
        buf.writeInt(msg.getResult());
        buf.writeInt(msg.getMessageId());
        buf.writeInt(length);
        buf.writeInt(msg.getClientId());

        if (length > 0) {
            // 直接由 Netty 缓冲区零拷贝流转至新 WebSocket 帧
            if (msg.hasByteBuf()) {
                ByteBuf body = msg.getByteBuf();
                buf.writeBytes(body, body.readerIndex(), body.readableBytes());
            } else if (msg.getMessage() != null) {
                buf.writeBytes(msg.getMessage());
            }
        }

        out.add(new BinaryWebSocketFrame(buf));
    }
}
