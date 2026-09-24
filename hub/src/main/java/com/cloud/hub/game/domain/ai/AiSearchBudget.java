package com.cloud.hub.game.domain.ai;

/**
 * AI 启发式搜索的时间与节点双预算控制器。
 * <p>
 * 采用单调时钟（{@link System#nanoTime()}），避免系统时钟漂移或校准影响截止时间判定。
 * 限制 AI 决策在每步允许的最大耗时（毫秒）与遍历节点数，防止复杂牌型搜索引起主线程卡顿。
 *
 * @author cloud
 */
public final class AiSearchBudget {
    /** 截止时间戳 (纳秒) */
    private final long deadlineNanos;
    /** 允许遍历的最大节点总数 */
    private final int maxNodes;
    /** 已访问节点计数器 */
    private int nodes;

    /**
     * 创建一个搜索预算控制器。
     *
     * @param timeBudgetMillis 最大允许搜索耗时 (毫秒)
     * @param maxNodes         最大允许遍历节点数
     */
    public AiSearchBudget(long timeBudgetMillis, int maxNodes) {
        long millis = Math.max(1L, timeBudgetMillis);
        this.deadlineNanos = System.nanoTime() + millis * 1_000_000L;
        this.maxNodes = Math.max(1, maxNodes);
    }

    /**
     * 尝试访问下一个搜索节点并递增计数。
     *
     * @return 当节点已达上限或已超时时返回 {@code true}（通知调用方应中止搜索循环）；未超限时返回 {@code false}
     */
    public boolean tryVisit() {
        if (nodes >= maxNodes || System.nanoTime() >= deadlineNanos) {
            return true;
        }
        nodes++;
        return false;
    }

    /**
     * 检查当前是否仍在可用预算限额内。
     *
     * @return 若未达到节点上限且未超时则返回 {@code true}，表示可继续执行搜索
     */
    public boolean isExhausted() {
        return nodes < maxNodes && System.nanoTime() < deadlineNanos;
    }

    /**
     * 获取当前已访问的节点总数。
     *
     * @return 已访问节点数
     */
    public int getVisitedNodes() {
        return nodes;
    }
}
