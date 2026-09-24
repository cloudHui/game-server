package com.cloud.hub.game.domain.ddz.ai;

import com.cloud.hub.game.domain.card.CardConst;

/**
 * 斗地主 AI 常量与启发式评估权重配置。
 * <p>
 * 集中管理拆牌权重、对局阶段阈值、出牌加减分以及跟牌防守成本，通过调整此处数值即可微调 AI 风格。
 *
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @since 1.0
 */
public final class DdzAiConstants {

    private DdzAiConstants() {
    }

    // ========== 阶段定义（按自身剩余手牌张数划分） ==========
    /** ≥ 该张数视为前期（以试探和跑小牌为主，非必要不动炸弹） */
    public static final int PHASE_EARLY_MIN_CARDS = 14;
    /** ≥ 该张数视为中期（逐步确立牌局控场） */
    public static final int PHASE_MID_MIN_CARDS = 8;
    /** ≤ 该张数时启用终局精确搜索（穷举最快出完手牌的路径） */
    public static final int PHASE_ENDGAME_MAX_CARDS = 5;

    // ========== 拆牌管线分支权重（分值越大越倾向整组保留，避免拆散） ==========
    /** 王炸（火箭）拆牌保留权重（极高，不可拆散） */
    public static final int SPLIT_WEIGHT_ROCKET = 5000;
    /** 四张炸弹拆牌保留权重 */
    public static final int SPLIT_WEIGHT_BOMB = 3500;
    /** 飞机机身按「段」加权分值（越大越倾向保留整副飞机） */
    public static final int SPLIT_WEIGHT_PLANE = 550;
    /** 拆出一组飞机时在每段加权基础上额外增加的固定加成 */
    public static final int SPLIT_WEIGHT_PLANE_GROUP_BONUS = 900;
    /** 单顺子每张牌的基础保留权重 */
    public static final int SPLIT_WEIGHT_STRAIGHT_PER_CARD = 120;
    /** 单顺子成牌额外固定奖励分 */
    public static final int SPLIT_WEIGHT_STRAIGHT_MIN_BONUS = 400;
    /** 双顺（连对）每对牌的基础保留权重 */
    public static final int SPLIT_WEIGHT_STRAIGHT_DOUBLE_PER_PAIR = 280;
    /** 双顺成牌额外固定奖励分 */
    public static final int SPLIT_WEIGHT_STRAIGHT_DOUBLE_MIN_BONUS = 350;
    /** 三张相同牌型（三条）保留分 */
    public static final int SPLIT_WEIGHT_TRIPLE = 900;
    /** 对子保留分 */
    public static final int SPLIT_WEIGHT_PAIR = 200;
    /** 普通单张牌保留分 */
    public static final int SPLIT_WEIGHT_SINGLE = 50;
    /** 高点数大单张 (2/A/K/王) 额外追加保留分（尽量用于顶牌或回手，不随意作为杂散单出） */
    public static final int SPLIT_WEIGHT_SINGLE_TOP_RANK_EXTRA = 180;

    // ========== 首出偏好加减分（在合法牌型上的惩罚与奖励，分值越小越倾向先出） ==========
    /** 首出中等单张（7-10）的奖励加成（便于试探） */
    public static final int LEAD_BONUS_SINGLE_RANK_7_TO_10 = -80;
    /** 首出大单张的惩罚分（避免轻易将大牌首发浪费） */
    public static final int LEAD_PENALTY_SINGLE_HIGH = 200;
    /** 首出大对子的惩罚分 */
    public static final int LEAD_PENALTY_PAIR_HIGH_RANK = 150;
    /** 首出小连对或小对子的奖励分（积极消耗手中冗余小牌） */
    public static final int LEAD_BONUS_SMALL_PAIR_LOW_RANK = -60;
    /** 前期首出炸弹的高额惩罚分 */
    public static final int LEAD_PENALTY_BOMB_EARLY = 800;
    /** 前期首出王炸的超高额惩罚分 */
    public static final int LEAD_PENALTY_ROCKET_EARLY = 2000;
    /** 拆牌保留权重折算进首出成本的缩放系数 */
    public static final double LEAD_PRESERVE_WEIGHT_SCALE = 0.35;
    /** 出牌后剩余手牌被拆成独立散组的手数惩罚（手数越多惩罚越重，鼓励能够成套连贯出牌的打法） */
    public static final double LEAD_RESIDUAL_GROUP_PENALTY = 15.0;

    // ========== 跟牌压制偏好与炸弹控制 ==========
    /** 牌力差距（strengthKey 差）惩罚系数（避免用过大的牌压过小的牌，倾向于「最小压制」） */
    public static final double FOLLOW_STRENGTH_MARGIN_PENALTY = 2.0;
    /** 普通炸弹打出时的基础保留代价 */
    public static final int FOLLOW_BOMB_BASE_COST = 400;
    /** 王炸打出时的基础保留代价 */
    public static final int FOLLOW_ROCKET_COST = 2500;
    /** 前期对手打出的牌力不超过此上限时，禁止动用炸弹强压 */
    public static final int FOLLOW_SOFT_LAST_STRENGTH_MAX = 8;
    /** 对手手牌剩余张数低于此值时，解除炸弹限制 */
    public static final int FOLLOW_BOMB_WHEN_OPP_HAND_AT_MOST = 10;
    /** 危险预警：对手手牌 ≤ 此张数时，判定为报单/极度危险 */
    public static final int FOLLOW_BOMB_DANGER_OPP_CARDS = 3;
    /** 危险情况下炸弹打出代价的折扣系数（允许积极轰炸压制） */
    public static final double FOLLOW_BOMB_DANGER_DISCOUNT = 0.4;

    // ========== 农民阵营配合策略 ==========
    /** 队友农民刚打出牌且控场时，是否默认让行（PASS）以助攻队友逃牌 */
    public static final boolean AI_PASS_AFTER_TEAMMATE_PLAY = true;
    /** 队友出牌牌力低于该阈值时视为过弱，不可盲目让行，己方应主动接手接牌 */
    public static final int FARMER_TEAMMATE_WEAK_THRESHOLD = 4;
    /** 地主手牌 ≤ 该张数时进入斩杀防线，农民必须无条件阻击 */
    public static final int FARMER_DANGER_LANDLORD_CARDS = 3;

    /**
     * 判断单张牌点数是否属于高端控制牌（K, A, 2, 小王, 大王）。
     *
     * @param cardVal 扑克牌点数（13=K, 14=A, 15=2, 16=小王, 17=大王）
     * @return true 表示为高端牌
     */
    private static boolean isTopRankSingle(int cardVal) {
        return (cardVal >= 13 && cardVal <= 15) || cardVal >= CardConst.SMALL_JOKER_VAL;
    }

    /**
     * 计算单张牌根据其点数所获得的额外拆牌保留分。
     *
     * @param cardVal 扑克牌点数
     * @return 额外保留加分
     */
    public static int splitSingleExtra(int cardVal) {
        if (isTopRankSingle(cardVal)) {
            return SPLIT_WEIGHT_SINGLE_TOP_RANK_EXTRA;
        }
        return 0;
    }

    /**
     * 计算一组连续飞机牌型的综合保留分。
     *
     * @param segmentsK 飞机连续段数（例如 333444 为 2 段）
     * @return 该飞机组合的总保留分
     */
    public static int splitPlaneGroupScore(int segmentsK) {
        return SPLIT_WEIGHT_PLANE * segmentsK + SPLIT_WEIGHT_PLANE_GROUP_BONUS;
    }
}
