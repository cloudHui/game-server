package utils.concurrent.pool;

/**
 * 可回收对象契约接口。
 * <p>
 * 凡是需要进入 {@link ObjectPool} 复用的实体（如物理向量、帧事件、网络数据包），均实现此接口。
 *
 * @author cloud
 */
public interface Recyclable {

    /**
     * 重置对象状态。
     * <p>
     * 在对象归还入池或重新借出时由对象池自动调用，用于清理历史脏数据，杜绝数据残留。
     */
    void reset();
}
