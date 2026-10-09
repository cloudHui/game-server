package utils.learning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 艾宾浩斯 / SuperMemo SM-2 自适应间隔重复复习调度器。
 * <p>
 * 根据作答掌握质量分（0 ~ 5 分）动态更新记忆因子，自适应拉长或缩短复习间隔天数，
 * 并支持计算复习紧急度得分，为学习推题提供量化排序。
 *
 * @author cloud
 */
public final class SpacedRepetitionScheduler {

    /** 记忆难度系数下限保底值（防止系数过低导致该项目永久高频出现） */
    public static final double MIN_EASE_FACTOR = 1.3;

    /** 毫秒天数换算基数 */
    public static final long ONE_DAY_MS = 24L * 60 * 60 * 1000;

    private SpacedRepetitionScheduler() {
    }

    /**
     * 根据当前作答质量评估更新记忆单元状态。
     *
     * @param item    待更新的记忆单元
     * @param quality 作答掌握质量（0:完全遗忘, 1:严重错误, 2:答错但有印象, 3:勉强答对, 4:犹豫后正确, 5:秒答熟练）
     * @param now     作答时刻时间戳（毫秒）
     * @return 更新后的记忆单元
     */
    public static MemoryItem review(MemoryItem item, int quality, long now) {
        if (item == null) {
            return null;
        }

        int clampedQuality = Math.max(0, Math.min(5, quality));
        int repetitions = item.getRepetitions();
        int intervalDays = item.getIntervalDays();
        double easeFactor = item.getEaseFactor();

        // 当掌握质量 >= 3 时，视为本次复习达标，拉长复习周期
        if (clampedQuality >= 3) {
            if (repetitions == 0) {
                intervalDays = 1;
            } else if (repetitions == 1) {
                intervalDays = 6;
            } else {
                intervalDays = (int) Math.round(intervalDays * easeFactor);
            }
            repetitions++;
        } else {
            // 答错或遗忘时重置连胜阶段，强制回到 1 天后巩固
            repetitions = 0;
            intervalDays = 1;
        }

        // SM-2 核心记忆因子调节公式：EF' = EF + (0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))
        int diff = 5 - clampedQuality;
        easeFactor = easeFactor + (0.1 - diff * (0.08 + diff * 0.02));
        if (easeFactor < MIN_EASE_FACTOR) {
            easeFactor = MIN_EASE_FACTOR;
        }

        item.setRepetitions(repetitions);
        item.setIntervalDays(intervalDays);
        item.setEaseFactor(easeFactor);
        item.setLastReviewTime(now);
        item.setNextReviewTime(now + (intervalDays * ONE_DAY_MS));

        return item;
    }

    /**
     * 计算该知识点在当前时刻的复习紧急度得分（Urgency Score）。
     * <p>
     * 得分越高代表越急需立即复习。超期未复习时长越长、掌握程度越低，得分呈递增趋势。
     *
     * @param item 记忆单元
     * @param now  当前毫秒时间戳
     * @return 紧急度分值（正数表示已到期或逾期）
     */
    public static double calculateUrgency(MemoryItem item, long now) {
        if (item == null) {
            return 0.0;
        }
        // 计算逾期毫秒差：若为正表示已经逾期
        long overdueMs = now - item.getNextReviewTime();
        double overdueDays = (double) overdueMs / ONE_DAY_MS;

        // 基础得分由逾期天数决定，叠加记忆因子的难度加权（越难的内容逾期权重放大）
        double baseScore = overdueDays * (3.0 - item.getEaseFactor() + 1.0);

        // 如果连胜次数为 0（生词或错题），给予额外基础权重加成
        if (item.getRepetitions() == 0) {
            baseScore += 2.0;
        }

        return baseScore;
    }

    /**
     * 从候选记忆集合中筛选并排序出最紧急需要复习的前 N 个项目。
     *
     * @param items 候选记忆列表
     * @param limit 限制推题数量
     * @param now   当前时间戳
     * @return 按紧急度降序排列的推荐复习列表
     */
    public static List<MemoryItem> pickDueItems(List<MemoryItem> items, int limit, long now) {
        if (items == null || items.isEmpty() || limit <= 0) {
            return new ArrayList<>();
        }

        List<MemoryItem> dueList = new ArrayList<>();
        for (MemoryItem it : items) {
            // 筛选已达到或超过推荐复习时间的项目
            if (now >= it.getNextReviewTime()) {
                dueList.add(it);
            }
        }

        // 按紧急度得分降序排序，优先消化最容易遗忘的薄弱项
        dueList.sort(Comparator.comparingDouble((MemoryItem it) -> calculateUrgency(it, now)).reversed());

        if (dueList.size() > limit) {
            return new ArrayList<>(dueList.subList(0, limit));
        }
        return dueList;
    }
}
