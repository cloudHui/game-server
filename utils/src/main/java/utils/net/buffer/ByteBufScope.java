package utils.net.buffer;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;

/**
 * ByteBuf 安全生命周期守卫范围（RAII 模式包装器）。
 * <p>
 * 实现 {@link AutoCloseable}，结合 try-with-resources 语法树确保申请的直接内存和池化缓冲区在
 * 异常或业务分支退出时被 100% 幂等释放，杜绝 Netty Direct Memory 泄漏。
 *
 * @author cloud
 */
public final class ByteBufScope implements AutoCloseable {

    /** 作用域内受管辖的 ByteBuf 资源清单 */
    private final List<ByteBuf> resources = new ArrayList<>(4);

    /** 是否已经关闭释放标志 */
    private volatile boolean closed = false;

    /**
     * 创建一个空的作用域容器。
     */
    public ByteBufScope() {
    }

    /**
     * 将已分配的 ByteBuf 注册至当前作用域托管。
     *
     * @param buf 待托管的缓冲区
     * @return 注册后的原缓冲区
     */
    public ByteBuf attach(ByteBuf buf) {
        if (buf != null) {
            // 在关闭后若有新资源加入，说明时序异常，需立即释放以防泄漏
            if (closed) {
                PooledByteBufUtil.safeRelease(buf);
                throw new IllegalStateException("ByteBufScope 已关闭，无法再托管新缓冲区");
            }
            resources.add(buf);
        }
        return buf;
    }

    /**
     * 将受管辖的 ByteBuf 安全派发并剥离当前作用域管理，由外部接管。
     *
     * @param buf 待剥离的缓冲区
     * @return 剥离后的原缓冲区
     */
    public ByteBuf detach(ByteBuf buf) {
        if (buf != null) {
            resources.remove(buf);
        }
        return buf;
    }

    /**
     * 复制并增加引用计数，返回一个由调用方自行负责生命周期的切片。
     *
     * @param buf 待复制的缓冲区
     * @return 引用计数已递增的副本
     */
    public ByteBuf retainForTransfer(ByteBuf buf) {
        if (buf == null) {
            return null;
        }
        // 调用 retain 确保即便当前 Scope 结束调用了 release，远端或下游异步线程仍然持有合法引用
        return buf.retainedDuplicate();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        // 倒序释放托管的所有缓冲区，确保依赖顺序正确
        for (int i = resources.size() - 1; i >= 0; i--) {
            ByteBuf buf = resources.get(i);
            PooledByteBufUtil.safeRelease(buf);
        }
        resources.clear();
    }
}
