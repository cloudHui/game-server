package com.cloud.hub.game.domain.pdk.ai;

import com.cloud.hub.game.domain.ai.AiSearchBudget;
import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.ddz.DdzHand;
import com.cloud.hub.game.domain.ddz.ai.CardGroup;
import com.cloud.hub.game.domain.pdk.PdkRules;
import com.cloud.hub.game.domain.pdk.PdkTable;
import com.cloud.hub.game.domain.table.TableUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 跑得快大师档 AI：基于启发式拆牌与受控预算搜索。
 * <p>
 * 拆牌规划生成候选 → 评估出牌后剩余结构（组数/保留分）→ 限时节点预算搜索；
 * 跟牌在「有牌必管」下选最不伤手、危险时才积极用炸。
 *
 * @author cloud
 */
public final class PdkMasterAi {

    private static final Logger log = LoggerFactory.getLogger(PdkMasterAi.class);

    @FunctionalInterface
    private interface PreserveEvaluator {
        double evaluate(DdzHand h);
    }

    /** 牌型保留价值评估策略分发映射表 */
    private static final Map<ConstProto.CardType, PreserveEvaluator> PRESERVE_EVALUATORS = new EnumMap<>(ConstProto.CardType.class);

    static {
        PRESERVE_EVALUATORS.put(ConstProto.CardType.STRAIGHT, h -> PdkAiConstants.SPLIT_WEIGHT_STRAIGHT_MIN_BONUS
                + h.getCards().size() * PdkAiConstants.SPLIT_WEIGHT_STRAIGHT_PER_CARD);
        PRESERVE_EVALUATORS.put(ConstProto.CardType.STRAIGHT_DOUBLE, h -> PdkAiConstants.SPLIT_WEIGHT_STRAIGHT_DOUBLE_MIN_BONUS
                + h.getStraightLen() * PdkAiConstants.SPLIT_WEIGHT_STRAIGHT_DOUBLE_PER_PAIR);
        PRESERVE_EVALUATORS.put(ConstProto.CardType.TRIPLE_DOUBLE, h -> PdkAiConstants.SPLIT_WEIGHT_TRIPLE_DOUBLE);
        PRESERVE_EVALUATORS.put(ConstProto.CardType.TRIPLE_ONE, h -> PdkAiConstants.SPLIT_WEIGHT_TRIPLE_ONE);
        PRESERVE_EVALUATORS.put(ConstProto.CardType.TRIPLE, h -> PdkAiConstants.SPLIT_WEIGHT_TRIPLE);
        PRESERVE_EVALUATORS.put(ConstProto.CardType.DOUBLE, h -> PdkAiConstants.SPLIT_WEIGHT_PAIR);
    }

    private PdkMasterAi() {}

    /**
     * 大师级跑得快出牌决策入口。
     *
     * @param table 当前牌桌
     * @param user  决策玩家
     * @return 最终出牌或过牌操作指令
     */
    static GameProto.OpInfo decide(PdkTable table, TableUser user) {
        List<Card> hand = new ArrayList<>(user.getCards());
        if (hand.isEmpty()) {
            return PdkSimpleAi.pass();
        }
        Collections.sort(hand);
        int minOpp = PdkSimpleAi.minOpponentCards(table, user);
        DdzHand last = table.getPdk().getLastHand();
        AiSearchBudget budget = new AiSearchBudget(
                PdkAiConstants.MASTER_TIME_MS, PdkAiConstants.MASTER_MAX_NODES);

        GameProto.OpInfo decision;
        if (last == null) {
            decision = lead(hand, minOpp, budget);
            log.info("[PDK-MasterAI] TableId: {}, User: {}, Lead decision made, HandSize: {}, VisitedNodes: {}",
                    table.getTableId(), user.getUserId(), hand.size(), budget.getVisitedNodes());
        } else {
            List<DdzHand> beats = PdkRules.findAllBeatingHands(hand, last);
            if (beats.isEmpty()) {
                log.debug("[PDK-MasterAI] TableId: {}, User: {}, No beats available -> PASS", table.getTableId(), user.getUserId());
                return PdkSimpleAi.pass();
            }
            DdzHand picked = pickBeat(hand, beats, minOpp, budget);
            log.info("[PDK-MasterAI] TableId: {}, User: {}, Beat chosen: {} (strength: {}), VisitedNodes: {}",
                    table.getTableId(), user.getUserId(), picked.getType(), picked.getStrengthKey(), budget.getVisitedNodes());
            decision = PdkSimpleAi.playHand(picked);
        }
        return decision;
    }

    /**
     * 首出牌型决策搜索。
     */
    private static GameProto.OpInfo lead(List<Card> hand, int minOpp, AiSearchBudget budget) {
        // 终局：最少手数出完
        if (hand.size() <= PdkAiConstants.PHASE_ENDGAME_MAX_CARDS) {
            DdzHand end = PdkSimpleAi.endgameFirst(hand);
            if (end != null) {
                return PdkSimpleAi.playHand(end);
            }
        }
        List<DdzHand> candidates = leadCandidates(hand, budget);
        DdzHand best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int phase = PdkSimpleAi.phaseOf(hand.size());
        for (DdzHand candidate : candidates) {
            if (budget.tryVisit()) {
                break;
            }
            double score = scorePlay(hand, candidate, phase, minOpp, budget);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best == null) {
            return PdkSimpleAi.playHand(PdkSimpleAi.pickLead(hand, minOpp));
        }
        return PdkSimpleAi.playHand(best);
    }

    /**
     * 从所有可管上的牌型中挑选最优打法。
     */
    private static DdzHand pickBeat(
            List<Card> hand, List<DdzHand> beats, int minOpp, AiSearchBudget budget) {
        int phase = PdkSimpleAi.phaseOf(hand.size());
        DdzHand best = beats.get(0);
        double bestScore = Double.POSITIVE_INFINITY;
        for (DdzHand beat : beats) {
            if (budget.tryVisit()) {
                break;
            }
            double score = scorePlay(hand, beat, phase, minOpp, budget) + beatCost(beat, minOpp);
            if (score < bestScore) {
                bestScore = score;
                best = beat;
            }
        }
        return best;
    }

    /**
     * 评估出牌代价：剩余组数为主，能一把清优先；炸弹视对手危险度加减。
     */
    private static double scorePlay(
            List<Card> hand, DdzHand play, int phase, int minOpp, AiSearchBudget budget) {
        List<Card> left = PdkSimpleAi.removeCards(hand, play.getCards());
        if (left.isEmpty()) {
            return PdkAiConstants.FINISH_NOW_BONUS;
        }
        double score = scoreLeadShape(play, phase) + preserveHint(play) * PdkAiConstants.LEAD_PRESERVE_SCALE;
        List<CardGroup> plan = PdkSplitPlanner.planBest(left);
        score += plan.size() * PdkAiConstants.RESIDUAL_GROUP_PENALTY;
        if (PdkRules.analyze(left).isPresent()) {
            score += PdkAiConstants.FINISH_NEXT_BONUS;
        }
        for (CardGroup group : plan) {
            if (budget.tryVisit()) {
                break;
            }
            Optional<DdzHand> analyzed = PdkRules.analyze(group.getCards());
            if (analyzed.isPresent()) {
                score += preserveHint(analyzed.get()) * 0.15;
            }
        }
        // 对手将尽时，避免送弱控场
        if (minOpp <= PdkAiConstants.FOLLOW_BOMB_DANGER_OPP_CARDS
                && play.getType() == ConstProto.CardType.SINGLE
                && play.getStrengthKey() <= 10) {
            score += 40;
        }
        return score;
    }

    private static double beatCost(DdzHand beat, int minOpp) {
        double cost = beat.getStrengthKey() * PdkAiConstants.FOLLOW_STRENGTH_PENALTY;
        if (beat.isBomb()) {
            boolean danger = minOpp <= PdkAiConstants.FOLLOW_BOMB_DANGER_OPP_CARDS;
            cost += danger
                    ? PdkAiConstants.FOLLOW_BOMB_BASE_COST * PdkAiConstants.FOLLOW_BOMB_DANGER_DISCOUNT
                    : PdkAiConstants.FOLLOW_BOMB_BASE_COST;
        }
        return cost;
    }

    private static double scoreLeadShape(DdzHand h, int phase) {
        double s = h.getStrengthKey();
        if (phase == 0 && h.isBomb()) {
            s += PdkAiConstants.LEAD_PENALTY_BOMB_EARLY;
        }
        if (h.getType() == ConstProto.CardType.SINGLE) {
            int v = h.getStrengthKey();
            if (v >= 7 && v <= 10) s += PdkAiConstants.LEAD_BONUS_SINGLE_MID;
            if (PdkAiConstants.isTopSingle(v)) s += PdkAiConstants.LEAD_PENALTY_SINGLE_HIGH;
        }
        if (h.getType() == ConstProto.CardType.DOUBLE) {
            int v = h.getStrengthKey();
            if (v <= 10) s += PdkAiConstants.LEAD_BONUS_SMALL_PAIR;
            if (v >= 13) s += PdkAiConstants.LEAD_PENALTY_HIGH_PAIR;
        }
        return s;
    }

    /**
     * 策略查表评估牌型保留价值。
     */
    private static double preserveHint(DdzHand h) {
        if (h.isBomb()) {
            return PdkAiConstants.SPLIT_WEIGHT_BOMB;
        }
        PreserveEvaluator evaluator = PRESERVE_EVALUATORS.get(h.getType());
        return evaluator != null ? evaluator.evaluate(h) : PdkAiConstants.SPLIT_WEIGHT_SINGLE;
    }

    private static List<DdzHand> leadCandidates(List<Card> hand, AiSearchBudget budget) {
        List<DdzHand> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (CardGroup group : PdkSplitPlanner.planBest(hand)) {
            addCandidate(group.getCards(), result, seen);
        }
        // 补充规则枚举的合法首出，避免拆牌漏掉长顺/连对
        for (DdzHand h : PdkRules.enumerateLeadHands(hand)) {
            if (seen.add(hashHand(h))) {
                result.add(h);
            }
        }
        if (hand.size() <= PdkAiConstants.MASTER_SUBSET_MAX_CARDS) {
            addSubsets(hand, result, seen, budget);
        }
        return result;
    }

    private static void addSubsets(
            List<Card> hand, List<DdzHand> result, Set<Long> seen, AiSearchBudget budget) {
        int n = hand.size();
        int combinations = 1 << n;
        for (int mask = 1; mask < combinations && budget.isExhausted(); mask++) {
            List<Card> subset = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    subset.add(hand.get(i));
                }
            }
            Optional<DdzHand> parsed = PdkRules.analyze(subset);
            if (parsed.isPresent() && seen.add(hashHand(parsed.get()))) {
                result.add(parsed.get());
            }
        }
    }

    private static void addCandidate(List<Card> cards, List<DdzHand> out, Set<Long> seen) {
        Optional<DdzHand> o = PdkRules.analyze(cards);
        if (o.isPresent() && seen.add(hashHand(o.get()))) {
            out.add(o.get());
        }
    }

    private static long hashHand(DdzHand h) {
        long hash = h.getType().ordinal() * 31L + h.getStrengthKey() * 17L + h.getStraightLen();
        for (Card c : h.getCards()) {
            hash = hash * 131 + c.getId();
        }
        return hash;
    }
}

