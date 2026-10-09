package utils.match;

/**
 * ELO 天梯积分与期望胜率计算器。
 * <p>
 * 提供标准 ELO 胜率模型与动态 K 值衰减机制，用于棋牌、小游戏竞技对局结算及匹配撮合评级。
 *
 * @author cloud
 */
public final class EloRatingCalculator {

    /** 默认初始积分 */
    public static final int DEFAULT_INITIAL_RATING = 1200;

    /** 新手高波动阶段最大局数阈值 */
    private static final int NOVICE_MATCH_COUNT_LIMIT = 20;

    /** 默认常规积分系数 K 值 */
    private static final int DEFAULT_K_FACTOR = 32;

    /** 高分大师段位平稳 K 值 */
    private static final int MASTER_K_FACTOR = 16;

    /** 新手阶段快速定级 K 值 */
    private static final int NOVICE_K_FACTOR = 48;

    private EloRatingCalculator() {
    }

    /**
     * 计算玩家 A 相对于玩家 B 的期望胜率（范围 0.0 ~ 1.0）。
     *
     * @param ratingA 玩家 A 当前积分
     * @param ratingB 玩家 B 当前积分
     * @return 玩家 A 的期望获胜概率
     */
    public static double calculateExpectedScore(int ratingA, int ratingB) {
        // ELO 标准胜率期望公式：E_A = 1 / (1 + 10^((R_B - R_A) / 400))
        return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
    }

    /**
     * 根据玩家胜负场次动态确定 K 值（用于平抑老玩家波动，加速新玩家定级）。
     *
     * @param rating     当前积分
     * @param matchCount 已进行的历史对局总数
     * @return 适合该玩家当前水平的 K 值
     */
    public static int resolveKFactor(int rating, int matchCount) {
        // 新手阶段赋予更高 K 值，使其能迅速脱离初始积分并收敛到真实竞技水平
        if (matchCount < NOVICE_MATCH_COUNT_LIMIT) {
            return NOVICE_K_FACTOR;
        }
        // 高分段位胜负分值需更为稳健，防止偶然对局造成断崖式掉分
        if (rating >= 2000) {
            return MASTER_K_FACTOR;
        }
        return DEFAULT_K_FACTOR;
    }

    /**
     * 计算单场对局结算后的新积分。
     *
     * @param currentRating 当前积分
     * @param opponentRating 对手当前积分
     * @param actualScore    实际对局结果得分（1.0 为获胜，0.5 为平局，0.0 为失败）
     * @param matchCount     玩家历史对局总数
     * @return 结算后的新积分（保底不低于 100 分）
     */
    public static int calculateNewRating(int currentRating, int opponentRating, double actualScore, int matchCount) {
        double expectedScore = calculateExpectedScore(currentRating, opponentRating);
        int k = resolveKFactor(currentRating, matchCount);

        // 新积分公式：R' = R + K * (S - E)
        long delta = Math.round(k * (actualScore - expectedScore));
        int newRating = (int) (currentRating + delta);

        // 设定最低安全兜底分，防止新手玩家由于连败扣至负数挫伤游戏积极性
        return Math.max(100, newRating);
    }
}
