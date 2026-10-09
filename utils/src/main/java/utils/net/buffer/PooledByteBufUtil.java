package utils.net.buffer;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;

import com.google.protobuf.MessageLite;

/**
 * Netty 池化缓冲区高性能管理与零拷贝工具类。
 * <p>
 * 统一封装 Netty 的 {@link PooledByteBufAllocator}，消除堆内存频繁分配与垃圾回收开销；
 * 提供 Protobuf 消息向 {@link ByteBuf} 的直接内存流式序列化以及引用计数安全释放防护。
 *
 * @author cloud
 */
public final class PooledByteBufUtil {

    /** 默认全局池化内存分配器 */
    private static final ByteBufAllocator ALLOCATOR = PooledByteBufAllocator.DEFAULT;

    private PooledByteBufUtil() {
    }

    /**
     * 从默认内存池分配指定容量的堆外直接缓冲区（Direct ByteBuf）。
     *
     * @param initialCapacity 初始容量大小（字节）
     * @return 池化的直接缓冲区实例
     */
    public static ByteBuf allocateDirect(int initialCapacity) {
        // 直接内存避免操作系统内核态与 JVM 用户态之间的二次复制，最适合网络 IO 发送
        return ALLOCATOR.directBuffer(initialCapacity);
    }

    /**
     * 从默认内存池分配指定容量的堆内存缓冲区（Heap ByteBuf）。
     *
     * @param initialCapacity 初始容量大小（字节）
     * @return 池化的堆内存缓冲区实例
     */
    public static ByteBuf allocateHeap(int initialCapacity) {
        // 在涉及需要与旧代码中 byte[] 强交互的过渡场景下，使用池化堆内存降低 GC 压力
        return ALLOCATOR.heapBuffer(initialCapacity);
    }

    /**
     * 将 Protobuf 消息直接序列化写入池化 ByteBuf，避免中间产生 byte[] 临时对象。
     *
     * @param message 待序列化的 Protobuf 消息
     * @return 包含序列化后二进制数据的 ByteBuf，调用方使用完毕后必须负责释放或交由下游管理
     */
    public static ByteBuf encodeProtobuf(MessageLite message) {
        if (message == null) {
            return Unpooled.EMPTY_BUFFER;
        }
        int serializedSize = message.getSerializedSize();
        if (serializedSize == 0) {
            return Unpooled.EMPTY_BUFFER;
        }

        // 精确预分配序列化所需尺寸，杜绝缓冲区动态扩容导致的内存二次搬迁
        ByteBuf buffer = allocateDirect(serializedSize);
        try (ByteBufOutputStream out = new ByteBufOutputStream(buffer)) {
            message.writeTo(out);
            return buffer;
        } catch (IOException e) {
            // 写入异常时必须主动归还已申请的直接内存，防止产生悬挂句柄与内存泄漏
            safeRelease(buffer);
            throw new IllegalStateException("Protobuf 序列化至 ByteBuf 异常", e);
        }
    }

    /**
     * 包装现有字节数组为不可变或普通非池化 ByteBuf。
     *
     * @param bytes 源字节数组
     * @return 包装后的 ByteBuf
     */
    public static ByteBuf wrap(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return Unpooled.EMPTY_BUFFER;
        }
        return Unpooled.wrappedBuffer(bytes);
    }

    /**
     * 安全释放 Netty 引用计数对象，兼容 null 与非引用计数对象。
     *
     * @param target 目标对象
     * @return 若执行安全释放未抛出异常则返回 true，否则返回 false
     */
    public static boolean safeRelease(Object target) {
        if (target == null) {
            return false;
        }
        try {
            ReferenceCountUtil.safeRelease(target);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 递增 ByteBuf 引用计数，供广播或多个并发下游消费使用。
     *
     * @param buf 目标缓冲区
     * @return 递增引用计数后的原缓冲区或切片
     */
    public static ByteBuf retain(ByteBuf buf) {
        if (buf == null) {
            return null;
        }
        return buf.retain();
    }
}
