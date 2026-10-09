package utils.match;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程安全且支持动态时间窗口放宽的天梯撮合池。
 * <p>
 * 遵循天梯公平竞技原则：入队初期严格限制分差；随着排队时间增长，动态按梯度逐步扩大可匹配分差窗口，
 * 避免冷门时段或高分段玩家长时间滞留队列。
 *
 * @param <T> 玩家自定义业务载荷类型
 * @author cloud
 */
public class MatchmakingQueue<T> {

    /** 基础初始可接受分差（分） */
    private final int baseRatingTolerance;

    /** 每等待一秒允许放宽的分差阈值（分/秒） */
    private final int expansionPerSecond;

    /** 允许放宽的最大分差上限（分） */
    private final int maxRatingTolerance;

    /** 活跃排队中的玩家票据集合 <玩家UserId, 排队票据> */
    private final Map<Integer, MatchTicket<T>> ticketMap = new ConcurrentHashMap<>();

    /**
     * 构建具备自适应窗口的撮合池。
     *
     * @param baseRatingTolerance 初始匹配容差分
     * @param expansionPerSecond  每秒窗口扩张分
     * @param maxRatingTolerance  最大容差分上限
     */
    public MatchmakingQueue(int baseRatingTolerance, int expansionPerSecond, int maxRatingTolerance) {
        this.baseRatingTolerance = baseRatingTolerance;
        this.expansionPerSecond = expansionPerSecond;
        this.maxRatingTolerance = maxRatingTolerance;
    }

    /**
     * 将玩家加入撮合队列。若已存在旧排队则覆盖刷新。
     *
     * @param userId   玩家 ID
     * @param rating   玩家积分
     * @param joinTime 入队时间戳（毫秒）
     * @param payload  业务上下文
     * @return 生成的票据
     */
    public MatchTicket<T> enqueue(int userId, int rating, long joinTime, T payload) {
        MatchTicket<T> ticket = new MatchTicket<>(userId, rating, joinTime, payload);
        ticketMap.put(userId, ticket);
        return ticket;
    }

    /**
     * 取消排队并移出撮合池。
     *
     * @param userId 玩家 ID
     * @return 若原本在队列中则返回对应的票据，否则返回 null
     */
    public MatchTicket<T> cancel(int userId) {
        return ticketMap.remove(userId);
    }

    /**
     * 检查玩家是否正在排队中。
     *
     * @param userId 玩家 ID
     * @return 是否排队中
     */
    public boolean isQueueing(int userId) {
        return ticketMap.containsKey(userId);
    }

    /**
     * 获取当前排队总人数。
     *
     * @return 排队中的玩家数量
     */
    public int size() {
        return ticketMap.size();
    }

    /**
     * 驱动单轮撮合扫描，生成所有当前符合动态窗口条件的匹配对。
     *
     * @param now 当前时间戳（毫秒）
     * @return 成功撮合生成的匹配结果清单
     */
    public synchronized List<MatchResult<T>> pollMatches(long now) {
        List<MatchResult<T>> matchedResults = new ArrayList<>();
        if (ticketMap.size() < 2) {
            return matchedResults;
        }

        // 优先按入队时间升序排列，保障先来先服务的 FIFO 公平性
        List<MatchTicket<T>> candidates = new ArrayList<>(ticketMap.values());
        candidates.sort(Comparator.comparingLong(MatchTicket::getJoinTime));

        List<Integer> matchedUserIds = new ArrayList<>();

        for (int i = 0; i < candidates.size(); i++) {
            MatchTicket<T> ticketA = candidates.get(i);
            if (matchedUserIds.contains(ticketA.getUserId())) {
                continue;
            }

            int toleranceA = calculateTolerance(ticketA.waitDuration(now));
            MatchTicket<T> bestOpponent = null;
            int minDiff = Integer.MAX_VALUE;

            for (int j = i + 1; j < candidates.size(); j++) {
                MatchTicket<T> ticketB = candidates.get(j);
                if (matchedUserIds.contains(ticketB.getUserId())) {
                    continue;
                }

                int diff = Math.abs(ticketA.getRating() - ticketB.getRating());
                int toleranceB = calculateTolerance(ticketB.waitDuration(now));

                // 双方只要有一方的容差窗口能够覆盖彼此，且双方差距在双向阈值合理范围内即视作候选
                int effectiveTolerance = Math.max(toleranceA, toleranceB);
                if (diff <= effectiveTolerance && diff < minDiff) {
                    minDiff = diff;
                    bestOpponent = ticketB;
                }
            }

            // 若找到最佳对手，则立即锁定撮合成对并移出候选池
            if (bestOpponent != null) {
                matchedUserIds.add(ticketA.getUserId());
                matchedUserIds.add(bestOpponent.getUserId());
                ticketMap.remove(ticketA.getUserId());
                ticketMap.remove(bestOpponent.getUserId());
                matchedResults.add(new MatchResult<>(ticketA, bestOpponent));
            }
        }

        return matchedResults;
    }

    /**
     * 根据玩家等待时长计算当前动态放宽后的最大容许分差。
     *
     * @param waitDurationMs 等待时长（毫秒）
     * @return 动态计算得出的分差阈值
     */
    private int calculateTolerance(long waitDurationMs) {
        long waitSeconds = waitDurationMs / 1000;
        long expanded = baseRatingTolerance + (waitSeconds * expansionPerSecond);
        return (int) Math.min(maxRatingTolerance, expanded);
    }
}
