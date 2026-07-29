package game.manager.table.ai;

/**
 * AI 搜索的时间与节点双预算。使用单调时钟，避免系统时间校准影响截止判断。
 */
public final class AiSearchBudget {
	private final long deadlineNanos;
	private final int maxNodes;
	private int nodes;

	public AiSearchBudget(long timeBudgetMillis, int maxNodes) {
		long millis = Math.max(1L, timeBudgetMillis);
		this.deadlineNanos = System.nanoTime() + millis * 1_000_000L;
		this.maxNodes = Math.max(1, maxNodes);
	}

	/** 尝试占用一个搜索节点；预算耗尽时返回 false。 */
	public boolean tryVisit() {
		if (nodes >= maxNodes || System.nanoTime() >= deadlineNanos) {
			return false;
		}
		nodes++;
		return true;
	}

	public boolean isExhausted() {
		return nodes >= maxNodes || System.nanoTime() >= deadlineNanos;
	}

	public int getVisitedNodes() {
		return nodes;
	}
}
