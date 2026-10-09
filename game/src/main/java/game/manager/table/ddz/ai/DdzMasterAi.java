package game.manager.table.ddz.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import game.manager.table.ai.AiSearchBudget;
import game.manager.table.cards.Card;
import game.manager.table.ddz.DdzHand;
import game.manager.table.ddz.DdzRules;
import proto.GameProto;

/**
 * 斗地主大师档 AI：基于启发式拆牌与节点预算控制的深度手牌评估器。
 * <p>
 * 核心决策思想：
 * <ul>
 *   <li><b>首出决策：</b>将手牌通过最佳拆牌规划与残局子集枚举展开为候选集，通过极小化出牌后“剩余手数+破坏惩罚”选出最优首发；</li>
 *   <li><b>跟牌决策：</b>在能管的合法牌型中，综合比较管牌后剩余手牌的结构完整度；根据对手剩余张数动态调节炸弹与火箭的使用门槛；</li>
 *   <li><b>算力保护：</b>依赖 {@link AiSearchBudget} 进行限时与节点配额早停，防止搜索超时影响对局流畅度。</li>
 * </ul>
 */
final class DdzMasterAi {

    private DdzMasterAi() {}

    /**
     * 大师档首出（主动领出）决策。
     * <p>
     * 穷举合法候选出牌并评估模拟出牌后的剩余手牌代价，选取剩余牌型最健康的一手打出。
     *
     * @param hand   当前手牌（点数有序）
     * @param phase  对局阶段（0:前期手牌>=12，1:中期手牌>=7，2:残局）
     * @param budget 搜索步数与耗时预算控制器
     * @return 最终决定的出牌操作协议对象
     */
    static GameProto.OpInfo lead(List<Card> hand, int phase, AiSearchBudget budget) {
        // 1. 综合最佳拆牌方案与残局子集枚举，生成首出候选牌型集合
        List<DdzHand> candidates = leadCandidates(hand, budget);
        DdzHand best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        // 2. 遍历所有候选牌型进行模拟出牌评估（代价越小打法越优）
        for (DdzHand candidate : candidates) {
            // 搜索预算耗尽熔断：早停以确保服务端单步响应耗时可控
            if (budget.tryNotVisit()) {
                break;
            }
            double score = scoreResidual(hand, candidate, phase, budget);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        // 3. 兜底策略：若未搜出解或预算耗尽，降级调用标准规则启发式选牌
        return best == null ? DdzSimpleAi.lead(hand, phase, null) : DdzSimpleAi.playHand(best);
    }

    /**
     * 大师档跟牌（被动管牌）挑选。
     * <p>
     * 在所有能管上家的候选牌型中，评估管牌后自身手牌的破碎程度，并动态权衡炸弹/王炸的使用代价。
     *
     * @param hand        当前手牌
     * @param beats       所有能管住上家的合法牌型列表（非空）
     * @param minOppCards 场上对手中最小剩余手牌张数
     * @param budget      搜索预算控制器
     * @return 综合代价最低的最佳管牌方案
     */
    static DdzHand pickBeat(List<Card> hand, List<DdzHand> beats,
                            int minOppCards, AiSearchBudget budget) {
        // 初始候选默认为普通启发式挑选的代价最低管牌
        DdzHand best = DdzSimpleAi.pickCheapestBeat(beats, minOppCards);
        double bestScore = Double.POSITIVE_INFINITY;

        for (DdzHand beat : beats) {
            // 算力配额早停防护
            if (budget.tryNotVisit()) {
                break;
            }

            // 计算出牌后剩余手牌的结构代价（越小越优）
            double score = scoreResidual(hand, beat, DdzSimpleAi.phaseOf(hand.size()), budget);

            // 炸弹与火箭使用权衡：
            // 若对手手牌 <= 2 张进入濒危斩杀期，大幅降低出炸弹/火箭的门槛以实施强力阻截；
            // 否则在常规期大幅增加出炸代价，避免过早浪费关键翻倍与控场杀手锏。
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
     * 评估模拟打出某手牌后，剩余手牌的综合负面代价。
     * <p>
     * 评估模型：剩余拆牌组数越少（手数越短）越优；打出牌本身保留价值越高代价越大；能一把清直接获胜。
     *
     * @param hand   当前手牌
     * @param play   拟打出的候选牌型
     * @param phase  当前对局阶段
     * @param budget 搜索预算控制器
     * @return 综合代价（数值越低越优）
     */
    private static double scoreResidual(List<Card> hand, DdzHand play,
                                        int phase, AiSearchBudget budget) {
        List<Card> remaining = DdzSimpleAi.removeCards(hand, play.getCards());
        // 斩杀判定：打完后手牌清空直接独赢，给予极大幅度的负向加分（奖励）
        if (remaining.isEmpty()) {
            return -100000;
        }

        // 基础得分：首发牌型的顺畅度偏好 + 该牌型本身的保留权值（保留权值越高，打出它的代价越大）
        double score = DdzSimpleAi.scoreLead(play, phase) + DdzSimpleAi.preserveHint(play);

        // 剩余牌型拆解：拆出的组数每多一组，代表多需一轮出牌，施加 120 的手数惩罚
        List<CardGroup> plan = DdzSplitPlanner.planBest(remaining);
        score += plan.size() * 120;

        // 遍历剩余牌组，微调剩余高质量牌型（如炸弹、大对子）的保留健康度
        for (CardGroup group : plan) {
            if (budget.tryNotVisit()) {
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
     * 生成首出候选牌型全集。
     * <p>
     * 聚合拆牌规划器的优质组块，并在残局（手牌 <= 10）穷举所有子集，保证不漏掉长连对/长单顺等杀着。
     *
     * @param hand   当前手牌
     * @param budget 搜索预算控制器
     * @return 去重后的首出候选牌型列表
     */
    private static List<DdzHand> leadCandidates(List<Card> hand, AiSearchBudget budget) {
        List<DdzHand> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        // 1. 拆牌规划推荐的牌组优先作为候选
        for (CardGroup group : DdzSplitPlanner.planBest(hand)) {
            DdzSimpleAi.addLeadCandidate(group.getCards(), result, seen);
        }

        // 2. 残局处理：手牌 <= 10 张时组合数受控（<= 1024 种），穷举子集以发现潜在非常规完整牌型；
        // 牌较多时仅补充单牌试探，避免组合爆炸
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
     * 基于二进制掩码穷举手牌的所有非空子集并识别合法牌型。
     *
     * @param hand   当前手牌
     * @param result 输出候选牌型列表
     * @param seen   牌型哈希去重集合
     * @param budget 搜索预算控制器
     */
    private static void addSubsets(List<Card> hand, List<DdzHand> result,
                                   Set<Long> seen, AiSearchBudget budget) {
        int combinations = 1 << hand.size();
        for (int mask = 1; mask < combinations && budget.isNotExhausted(); mask++) {
            DdzHand candidate = analyzeSubset(hand, mask);
            // 去重并加入候选列表
            if (candidate != null && seen.add(DdzLegalBeatFinder.hashHand(candidate))) {
                result.add(candidate);
            }
        }
    }

    /**
     * 提取掩码对应的手牌子集并判定其是否构成合法的斗地主牌型。
     *
     * @param hand 当前完整手牌
     * @param mask 二进制子集掩码
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

