package utils.replay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * 完整场次对局动作序列回放带（Replay Tape）。
 * <p>
 * 统一聚合局初开局状态快照（Initial Snapshot）与整局严格有序的动作流，
 * 既可在内存中支持重连快照回放，也能序列化存盘供 Canvas 可视化棋谱回放系统消费。
 *
 * @param <T> 帧数据载荷类型
 * @author cloud
 */
public class ReplayTape<T> {

    /** 对局唯一标识流水号 */
    private final String matchId;

    /** 游戏玩法类型编码（如五子棋、象棋、麻将等） */
    private final int gameType;

    /** 对局开始毫秒时间戳 */
    private final long startTime;

    /** 对局结束毫秒时间戳（进行中为 0） */
    private volatile long endTime;

    /** 开局全量初始快照（如初始手牌、底分倍率、发牌种子等） */
    private volatile Object initialSnapshot;

    /** 顺序记录的所有动作帧集合 */
    private final List<ReplayFrame<T>> frames = Collections.synchronizedList(new ArrayList<>());

    /**
     * 初始化一盘新对局的回放带。
     *
     * @param matchId   对局 ID
     * @param gameType  游戏类型
     * @param startTime 开始时间戳
     */
    public ReplayTape(String matchId, int gameType, long startTime) {
        this.matchId = matchId;
        this.gameType = gameType;
        this.startTime = startTime;
    }

    /**
     * 写入开局全量快照。
     *
     * @param snapshot 开局状态快照对象
     */
    public void setInitialSnapshot(Object snapshot) {
        this.initialSnapshot = snapshot;
    }

    /**
     * 获取开局状态快照。
     *
     * @return 初始快照
     */
    public Object getInitialSnapshot() {
        return initialSnapshot;
    }

    /**
     * 记录一步新对局动作帧。
     *
     * @param actionType 动作类型代码
     * @param actorId    操作玩家 ID
     * @param payload    动作载荷
     * @return 生成的新动作帧
     */
    public ReplayFrame<T> record(int actionType, int actorId, T payload) {
        int seq;
        synchronized (frames) {
            seq = frames.size() + 1;
            ReplayFrame<T> frame = new ReplayFrame<>(seq, System.currentTimeMillis(), actionType, actorId, payload);
            frames.add(frame);
            return frame;
        }
    }

    /**
     * 标记当前对局结束，记录封盘时间戳。
     */
    public void finish() {
        this.endTime = System.currentTimeMillis();
    }

    public String getMatchId() {
        return matchId;
    }

    public int getGameType() {
        return gameType;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    /**
     * 获取不可修改的已记录动作帧快照列表。
     *
     * @return 动作帧列表副本
     */
    public List<ReplayFrame<T>> getFrames() {
        synchronized (frames) {
            return new ArrayList<>(frames);
        }
    }

    /**
     * 动作总步数计数。
     *
     * @return 步数
     */
    public int frameCount() {
        return frames.size();
    }

    /**
     * 按时序回放每一帧动作。
     *
     * @param consumer 帧消费回调
     */
    public void playback(Consumer<ReplayFrame<T>> consumer) {
        List<ReplayFrame<T>> copy = getFrames();
        for (ReplayFrame<T> frame : copy) {
            consumer.accept(frame);
        }
    }
}
