package utils.net.buffer;

import org.junit.Assert;
import org.junit.Test;

import io.netty.buffer.ByteBuf;
import net.message.TCPMessage;

/**
 * Netty 池化缓冲区与安全作用域单元测试。
 */
public class BufferTest {

    @Test
    public void testAllocateAndScopeRelease() {
        ByteBuf buf;
        try (ByteBufScope scope = new ByteBufScope()) {
            buf = scope.attach(PooledByteBufUtil.allocateDirect(128));
            Assert.assertNotNull(buf);
            Assert.assertEquals(1, buf.refCnt());
            buf.writeInt(42);
            Assert.assertEquals(42, buf.readInt());
        }
        // 出作用域后必须已归还池化内存且引用计数为 0
        Assert.assertEquals(0, buf.refCnt());
    }

    @Test
    public void testTcpMessageByteBufIntegration() {
        ByteBuf buf = PooledByteBufUtil.allocateHeap(64);
        buf.writeIntLE(1001);
        TCPMessage msg = TCPMessage.newInstance(0, 101, 1, buf, 0L, 1);
        Assert.assertTrue(msg.hasByteBuf());
        Assert.assertEquals(4, msg.getPayloadLength());
        Assert.assertNotNull(msg.getMessage());
        Assert.assertEquals(4, msg.getMessage().length);
        msg.release();
        Assert.assertEquals(0, buf.refCnt());
    }
}
