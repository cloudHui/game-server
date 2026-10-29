package game.runtime;

/**
 * 游戏指标采集轻量级门面。
 * 在独立 Game 进程中提供零依赖的无操作实现，接口与 HubMetrics 完全兼容。
 */
public class HubMetrics {

    private static final HubMetrics INSTANCE = new HubMetrics();

    public static HubMetrics getInstance() {
        return INSTANCE;
    }

    public void recordTableLoop() {
    }

    public void recordTableCreated() {
    }

    public void recordTableDestroyed() {
    }

    public void recordRoundSettled(String gameType) {
    }

    public void recordSettle(String gameType) {
    }
}
