package utils.concurrent.realtime;

/**
 * 实时帧同步房间标准调度接口。
 * <p>
 * 凡是需要接入 {@link DedicatedGameLoopEngine} 进行高帧率（30/60 FPS）定长物理或逻辑演算的房间实体，均实现此接口。
 *
 * @author cloud
 */
public interface FrameSyncRoom {

    /**
     * 获取实时房间唯一标识。
     *
     * @return 房间唯一 ID
     */
    long getRoomId();

    /**
     * 定长逻辑/物理帧演算回调（Fixed Update）。
     * <p>
     * <b>核心保证：</b>
     * 每次回调代表推进严格定长的物理时间（如 60 FPS 为 16,666,666 ns）。
     * 若真实时间发生卡顿抖动，调度引擎会连续补算调用以追平物理时间，绝不丢弃任何一帧。
     *
     * @param deltaNs 本次定长帧时间步长（纳秒）
     */
    void onFixedTick(long deltaNs);
}
