package com.cloud.weball.game.domain.pdk.ai;

import com.cloud.weball.game.domain.card.CardConst;

/**
 * 跑得快 AI 可调参数（拆牌权重、阶段、跟牌/炸弹倾向）。
 * 参照斗地主大师档思路，按 16 张三人、有牌必管微调。
 */
public final class PdkAiConstants {

	private PdkAiConstants() {}

	/** ≥ 该张数视为前期（少动炸、先出结构牌） */
	public static final int PHASE_EARLY_MIN_CARDS = 12;
	/** ≥ 该张数视为中期 */
	public static final int PHASE_MID_MIN_CARDS = 7;
	/** ≤ 该张数启用终局最少手数搜索 */
	public static final int PHASE_ENDGAME_MAX_CARDS = 7;

	public static final int SPLIT_WEIGHT_BOMB = 3500;
	public static final int SPLIT_WEIGHT_TRIPLE_DOUBLE = 1100;
	public static final int SPLIT_WEIGHT_TRIPLE_ONE = 1000;
	public static final int SPLIT_WEIGHT_TRIPLE = 900;
	public static final int SPLIT_WEIGHT_STRAIGHT_MIN_BONUS = 400;
	public static final int SPLIT_WEIGHT_STRAIGHT_PER_CARD = 120;
	public static final int SPLIT_WEIGHT_STRAIGHT_DOUBLE_MIN_BONUS = 350;
	public static final int SPLIT_WEIGHT_STRAIGHT_DOUBLE_PER_PAIR = 280;
	public static final int SPLIT_WEIGHT_PAIR = 200;
	public static final int SPLIT_WEIGHT_SINGLE = 50;
	public static final int SPLIT_WEIGHT_SINGLE_TOP_EXTRA = 180;

	/** 出牌后剩余拆牌组数惩罚（越少越好） */
	public static final double RESIDUAL_GROUP_PENALTY = 120.0;
	/** 一手出完绝对优先 */
	public static final double FINISH_NOW_BONUS = -100000.0;
	/** 剩余一手可出完 */
	public static final double FINISH_NEXT_BONUS = -800.0;

	public static final int LEAD_PENALTY_BOMB_EARLY = 800;
	public static final int LEAD_PENALTY_SINGLE_HIGH = 180;
	public static final int LEAD_BONUS_SINGLE_MID = -70;
	public static final int LEAD_BONUS_SMALL_PAIR = -50;
	public static final int LEAD_PENALTY_HIGH_PAIR = 140;
	public static final double LEAD_PRESERVE_SCALE = 0.30;

	public static final double FOLLOW_STRENGTH_PENALTY = 2.0;
	public static final int FOLLOW_BOMB_BASE_COST = 420;
	public static final int FOLLOW_BOMB_DANGER_OPP_CARDS = 2;
	public static final double FOLLOW_BOMB_DANGER_DISCOUNT = 0.35;

	/** 大师档搜索：时间(ms) / 节点 */
	public static final long MASTER_TIME_MS = 80L;
	public static final int MASTER_MAX_NODES = 2400;
	/** 手牌 ≤ 该值时枚举子集首出候选 */
	public static final int MASTER_SUBSET_MAX_CARDS = 10;

	static boolean isTopSingle(int cardVal) {
		return cardVal >= CardConst.K_VAL && cardVal <= CardConst.ER_VAL;
	}
}
