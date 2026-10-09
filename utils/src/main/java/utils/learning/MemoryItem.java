package utils.learning;

/**
 * 学习记忆单元状态载体（艾宾浩斯 / SuperMemo 算法模型）。
 * <p>
 * 记录单条知识点（如生词、错题、诗词）的复习周期、遗忘衰减因子及下次应复习时间戳。
 *
 * @author cloud
 */
public class MemoryItem {

    /** 知识点唯一标识（如 wordId 或 questionId） */
    private final String itemId;

    /** 历史已连续成功复习通过次数 */
    private int repetitions;

    /** 当前推荐复习间隔天数 */
    private int intervalDays;

    /** 难度与易记度衰减因子（Ease Factor，SM-2 算法推荐初始值 2.5） */
    private double easeFactor;

    /** 上次作答复习毫秒时间戳 */
    private long lastReviewTime;

    /** 推荐下次复习到期毫秒时间戳 */
    private long nextReviewTime;

    /**
     * 构建默认初始状态的记忆单元。
     *
     * @param itemId 知识点 ID
     * @param now    当前初始时间戳（毫秒）
     */
    public MemoryItem(String itemId, long now) {
        this.itemId = itemId;
        this.repetitions = 0;
        this.intervalDays = 0;
        this.easeFactor = 2.5;
        this.lastReviewTime = now;
        this.nextReviewTime = now;
    }

    public MemoryItem(String itemId, int repetitions, int intervalDays, double easeFactor,
                      long lastReviewTime, long nextReviewTime) {
        this.itemId = itemId;
        this.repetitions = repetitions;
        this.intervalDays = intervalDays;
        this.easeFactor = easeFactor;
        this.lastReviewTime = lastReviewTime;
        this.nextReviewTime = nextReviewTime;
    }

    public String getItemId() {
        return itemId;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public void setRepetitions(int repetitions) {
        this.repetitions = repetitions;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(int intervalDays) {
        this.intervalDays = intervalDays;
    }

    public double getEaseFactor() {
        return easeFactor;
    }

    public void setEaseFactor(double easeFactor) {
        this.easeFactor = easeFactor;
    }

    public long getLastReviewTime() {
        return lastReviewTime;
    }

    public void setLastReviewTime(long lastReviewTime) {
        this.lastReviewTime = lastReviewTime;
    }

    public long getNextReviewTime() {
        return nextReviewTime;
    }

    public void setNextReviewTime(long nextReviewTime) {
        this.nextReviewTime = nextReviewTime;
    }
}
