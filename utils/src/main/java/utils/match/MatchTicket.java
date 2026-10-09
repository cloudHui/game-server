package utils.match;

/**
 * 撮合队列中的玩家票据载体。
 *
 * @param <T> 玩家自定义附加载荷类型
 * @author cloud
 */
public class MatchTicket<T> {

    /** 玩家唯一标识（如 userId） */
    private final int userId;

    /** 玩家当前天梯积分 */
    private final int rating;

    /** 玩家入队排队毫秒时间戳 */
    private final long joinTime;

    /** 业务方自定义上下文或玩家扩展实体 */
    private final T payload;

    /**
     * 构建撮合排队票据。
     *
     * @param userId   玩家唯一标识
     * @param rating   玩家积分
     * @param joinTime 入队时间戳（毫秒）
     * @param payload  业务扩展数据
     */
    public MatchTicket(int userId, int rating, long joinTime, T payload) {
        this.userId = userId;
        this.rating = rating;
        this.joinTime = joinTime;
        this.payload = payload;
    }

    public int getUserId() {
        return userId;
    }

    public int getRating() {
        return rating;
    }

    public long getJoinTime() {
        return joinTime;
    }

    public T getPayload() {
        return payload;
    }

    /**
     * 计算该玩家在当前时刻已经排队等待的时长（毫秒）。
     *
     * @param now 当前时间戳（毫秒）
     * @return 已排队毫秒数
     */
    public long waitDuration(long now) {
        return Math.max(0, now - joinTime);
    }
}
