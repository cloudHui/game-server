package utils.replay;

/**
 * 单步对局回放时序动作帧。
 *
 * @param <T> 动作载荷实体类型
 * @author cloud
 */
public class ReplayFrame<T> {

    /** 动作帧递增流水序号（从 1 开始） */
    private final int frameSeq;

    /** 动作发生的绝对时间戳（毫秒） */
    private final long timestamp;

    /** 动作业务类型代码（如摸牌、出牌、吃碰杠、悔棋等） */
    private final int actionType;

    /** 执行该动作的操作玩家 ID（系统发牌等行为可记为 0） */
    private final int actorId;

    /** 动作具体业务数据结构或状态载荷 */
    private final T payload;

    public ReplayFrame(int frameSeq, long timestamp, int actionType, int actorId, T payload) {
        this.frameSeq = frameSeq;
        this.timestamp = timestamp;
        this.actionType = actionType;
        this.actorId = actorId;
        this.payload = payload;
    }

    public int getFrameSeq() {
        return frameSeq;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getActionType() {
        return actionType;
    }

    public int getActorId() {
        return actorId;
    }

    public T getPayload() {
        return payload;
    }
}
