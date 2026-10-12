package utils.concurrent.pool;

/**
 * 池化对象安全句柄（实现 {@link AutoCloseable}，完美支持 Java {@code try-with-resources} 语法糖）。
 * <p>
 * <b>核心优势：</b>
 * 调用方通过 {@code try (PooledHandle<T> handle = pool.acquire()) { ... }} 借出对象，
 * 代码块执行完毕后（无论正常退出还是抛出异常）均保证 100% 自动归还入池，彻底杜绝人为遗漏导致的内存泄漏。
 *
 * @param <T> 池化对象类型
 * @author cloud
 */
public final class PooledHandle<T> implements AutoCloseable {

    /** 借出的实际业务对象引用 */
    private final T object;

    /** 来源对象池，用于执行归还入池 */
    private final ObjectPool<T> pool;

    /** 标记是否已执行过回收，防止重复归还 */
    private boolean recycled;

    /**
     * 内部构造函数，仅由 {@link ObjectPool#acquire()} 实例化。
     *
     * @param object 借出的对象
     * @param pool   所属对象池
     */
    PooledHandle(T object, ObjectPool<T> pool) {
        this.object = object;
        this.pool = pool;
        this.recycled = false;
    }

    /**
     * 获取借出的实际业务对象。
     *
     * @return 业务对象实例
     */
    public T get() {
        return object;
    }

    /**
     * 关闭句柄并自动将业务对象归还至对象池中。
     */
    @Override
    public void close() {
        if (!recycled) {
            recycled = true;
            pool.recycle(object);
        }
    }
}
