package com.cloud.hub.game.domain.ddz.ai;

import com.cloud.hub.game.domain.ai.AiSearchBudget;
import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.ddz.DdzHand;
import com.cloud.hub.game.domain.ddz.DdzRules;
import proto.GameProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 斗地主大师档（AI_MASTER）出牌与跟牌评估引擎。
 * <p>
 * 基于限时搜索预算机制（{@link AiSearchBudget}），对首出与跟牌候选解进行剩余手牌连贯度评分，
 * 优先选取能让剩余手牌手数最少且保留关键控制牌的打法。
 *
 * @author cloud
 * @version 1.0
 * @since 1.0
 */
final class DdzMasterAi {

    private DdzMasterAi() {
    }

    /**
     * 大师档 AI 主动首出决策：生成出牌候选集，并按剩余手牌残局评分挑选综合代价最小的合法牌型。
     *
     * @param hand   当前 AI 手牌列表
     * @param phase  当前对局阶段（前期/中期/残局）
     * @param budget 搜索步数与耗时预算控制器
     * @return 最终构建的 Protobuf 出牌操作
     */
    static GameProto.OpInfo lead(List<Card> hand, int phase, AiSearchBudget budget) {
        List<DdzHand> candidates = leadCandidates(hand, budget);
        DdzHand best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        // 遍历所有合法首出候选方案，评估出牌后残留手牌的拆分代价
        for (DdzHand candidate : candidates) {
            if (budget.tryVisit()) {
                break;
            }
            double score = scoreResidual(hand, candidate, phase, budget);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        // 若预算内未完成或未搜到满意解，平滑回落至基础 AI 首出逻辑
        return best == null ? DdzSimpleAi.lead(hand, phase, null) : DdzSimpleAi.playHand(best);
    }

    /**
     * 大师档 AI 被动跟牌决策：在所有能压过对手的合法牌型中，综合考虑残局代价与炸弹保留成本进行精细挑选。
     *
     * @param hand        当前手牌列表
     * @param beats       所有能大过上家的合法牌型列表
     * @param minOppCards 对手中最少剩余手牌张数
     * @param budget      搜索预算控制器
     * @return 挑选出的最佳跟牌牌型；若列表为空则由外部处理 PASS
     */
    static DdzHand pickBeat(List<Card> hand, List<DdzHand> beats,
                            int minOppCards, AiSearchBudget budget) {
        // 先以贪心最小代价作为保底解
        DdzHand best = DdzSimpleAi.pickCheapestBeat(beats, minOppCards);
        double bestScore = Double.POSITIVE_INFINITY;

        for (DdzHand beat : beats) {
            if (budget.tryVisit()) {
                break;
            }
            // 计算打出此手跟牌后，剩余手牌的结构完整度
            double score = scoreResidual(hand, beat, DdzSimpleAi.phaseOf(hand.size()), budget);
            // 对手余牌告急时（<=2），大幅降低动用炸弹/火箭的额外惩罚，鼓励积极下炸拦截
            if (beat.isBomb()) {
                score += minOppCards <= 2 ? 20 : 180;
            }
            if (beat.isRocket()) {
                score += minOppCards <= 2 ? 30 : 260;
            }
            if (score < bestScore) {
                bestScore = score;
                best = beat;
            }
        }
        return best;
    }

    /**
     * 评估假设打出 {@code play} 之后，剩余手牌的散乱程度与保留价值（评分越低代表方案越优）。
     *
     * @param hand   当前完整手牌
     * @param play   假定打出的牌型
     * @param phase  对局阶段
     * @param budget 搜索预算控制器
     * @return 残局综合评估分
     */
    private static double scoreResidual(List<Card> hand, DdzHand play,
                                        int phase, AiSearchBudget budget) {
        List<Card> remaining = DdzSimpleAi.removeCards(hand, play.getCards());
        // 若此手牌能一次性直接打光直接获胜，赋予极高优先级（绝对负分）
        if (remaining.isEmpty()) {
            return -100000;
        }
        double score = DdzSimpleAi.scoreLead(play, phase) + DdzSimpleAi.preserveHint(play);
        // 对剩余牌重新进行最优拆牌规划
        List<CardGroup> plan = DdzSplitPlanner.planBest(remaining);
        // 残留组数越多代表需要更多轮次才能跑完，重度惩罚手数
        score += plan.size() * 120;
        for (CardGroup group : plan) {
            if (budget.tryVisit()) {
                break;
            }
            Optional<DdzHand> analyzed = DdzRules.analyze(group.getCards());
            if (analyzed.isPresent()) {
                score += DdzSimpleAi.preserveHint(analyzed.get()) * 0.2;
            }
        }
        return score;
    }

    /**
     * 生成当前手牌的所有可能首出候选解集合。
     *
     * @param hand   当前手牌
     * @param budget 搜索预算
     * @return 首出候选牌型列表
     */
    private static List<DdzHand> leadCandidates(List<Card> hand, AiSearchBudget budget) {
        List<DdzHand> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        // 优先将最优拆牌规划中的各个成套组作为候选
        for (CardGroup group : DdzSplitPlanner.planBest(hand)) {
            DdzSimpleAi.addLeadCandidate(group.getCards(), result, seen);
        }
        // 当余牌较少时（<=10），进行子集枚举扩展候选空间
        if (hand.size() <= 10) {
            addSubsets(hand, result, seen, budget);
        } else {
            for (Card card : hand) {
                DdzSimpleAi.addLeadCandidate(Collections.singletonList(card), result, seen);
            }
        }
        return result;
    }

    /**
     * 位运算枚举手牌子集并抽取合法牌型补充至候选池。
     *
     * @param hand   手牌列表
     * @param result 候选结果列表
     * @param seen   已去重的牌型哈希集
     * @param budget 搜索预算
     */
    private static void addSubsets(List<Card> hand, List<DdzHand> result,
                                   Set<Long> seen, AiSearchBudget budget) {
        int combinations = 1 << hand.size();
        for (int mask = 1; mask < combinations && budget.isExhausted(); mask++) {
            DdzHand candidate = analyzeSubset(hand, mask);
            if (candidate != null && seen.add(DdzLegalBeatFinder.hashHand(candidate))) {
                result.add(candidate);
            }
        }
    }

    /**
     * 根据二进制掩码 mask 从手牌中提取子集并解析为合法牌型。
     *
     * @param hand 手牌列表
     * @param mask 子集二进制掩码
     * @return 若构成合法牌型则返回 DdzHand，否则返回 null
     */
    private static DdzHand analyzeSubset(List<Card> hand, int mask) {
        List<Card> subset = new ArrayList<>();
        for (int i = 0; i < hand.size(); i++) {
            if ((mask & (1 << i)) != 0) {
                subset.add(hand.get(i));
            }
        }
        return DdzRules.analyze(subset).orElse(null);
    }
}
