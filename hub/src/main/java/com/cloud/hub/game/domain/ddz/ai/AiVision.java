package com.cloud.hub.game.domain.ddz.ai;

import com.cloud.hub.game.domain.cards.Card;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 视野与信息感知抽象接口。
 * <p>
 * 控制 AI 能获取多少局内信息，支持三种视野等级：
 * <ul>
 *   <li>{@link #LEVEL_NORMAL} (0) — 正常公平模式：只看自己手牌 + 桌面已出牌（记牌器）</li>
 *   <li>{@link #LEVEL_SEMI} (1) — 半透视模式：额外获知剩余牌池的构成</li>
 *   <li>{@link #LEVEL_FULL} (2) — 全透视模式：额外获知其他玩家的实时手牌</li>
 * </ul>
 * 斗地主与麻将各玩法均实现此接口，AI 策略仅能通过此接口提取对局信息。
 *
 * @author cloud
 * @version 1.0
 * @date 2026-06-11
 * @since 1.0
 */
public interface AiVision {

    // ========== 视野等级（visionLevel） ==========
    /** 正常公平模式：仅可见自己手牌及已出公共牌 */
    int LEVEL_NORMAL = 0;
    /** 半透视模式：自己手牌 + 剩余牌池统计 */
    int LEVEL_SEMI = 1;
    /** 全透视模式：自己手牌 + 剩余牌池 + 全员对手手牌 */
    int LEVEL_FULL = 2;

    // ========== AI 智能等级（aiLevel） ==========
    /** 最低级：无策略，超时自动托管、出最小牌或摸打 */
    int AI_DUMB = 0;
    /** 基础策略：简单贪心启发式跟牌与出牌 */
    int AI_BASIC = 1;
    /** 高级策略：具备智能拆牌、牌力评估与手数推算 */
    int AI_ADVANCED = 2;
    /** 大师策略：在公平视野下进行搜索预算剪枝与全候选评估 */
    int AI_MASTER = 3;

    /**
     * 计算当前 AI 等级下的实际有效视野等级。
     * <p>
     * 大师级 AI 为保证真实竞技性，始终强制回落到正常公平视野 {@link #LEVEL_NORMAL}；其他档位保留管理后台配置。
     *
     * @param configuredVisionLevel 后台配置的视野等级
     * @param aiLevel                当前设定的 AI 智能等级
     * @return 实际生效的视野等级
     */
    static int effectiveVisionLevel(int configuredVisionLevel, int aiLevel) {
        return aiLevel >= AI_MASTER ? LEVEL_NORMAL : configuredVisionLevel;
    }

    /**
     * 获取当前生效的视野等级。
     *
     * @return 视野等级枚举值 (0=正常, 1=半透视, 2=全透视)
     */
    int getVisionLevel();

    /**
     * 获取当前生效的 AI 智能等级。
     *
     * @return 智能等级枚举值 (0=弱智, 1=基础, 2=高级, 3=大师)
     */
    int getAiLevel();

    /**
     * 获取当前 AI 玩家自身的手牌集合（始终可用且真实）。
     *
     * @return 自身手牌列表
     */
    List<Card> getMyHand();

    /**
     * 获取已打出到桌面上的扑克牌 ID 集合（记牌器账本）。
     *
     * @return 已出牌 ID 集合
     */
    Set<Integer> getPlayedCardIds();

    /**
     * 获取剩余未出牌按点数 rank 的分组字典。
     *
     * @return key 为点数 (3-17)，value 为该点数剩余扑克列表；若视野等级不足则返回 null
     */
    Map<Integer, List<Card>> getRemainingPool();

    /**
     * 获取指定座位的其他玩家手牌。
     *
     * @param seat 目标玩家座位编号
     * @return 目标手牌列表；若非全透视等级则返回 null
     */
    List<Card> getOpponentHand(int seat);

    /**
     * 获取对手玩家中的最少手牌剩余张数（用于报单、报双防守与压制）。
     *
     * @return 对手最小余牌张数
     */
    int getMinOpponentCards();

    /**
     * 查询指定点数 rank 在外界（即非己方手牌且尚未打出）的剩余张数。
     *
     * @param rank 扑克牌点数（如 3-17）
     * @return 剩余张数；若视野等级不足则返回 -1 表示未知
     */
    int remainingCountOfRank(int rank);
}
