package com.cloud.hub.game.domain.ddz.ai;

import com.cloud.hub.game.domain.ai.AiSearchBudget;
import com.cloud.hub.game.domain.card.CardConst;
import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.ddz.DdzHand;
import com.cloud.hub.game.domain.ddz.DdzRules;
import com.cloud.hub.game.domain.ddz.DdzTable;
import com.cloud.hub.game.domain.table.TableUser;
import proto.ConstProto;
import proto.GameProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 斗地主基础托管与启发式出牌决策引擎（AI_BASIC 与通用底层评估）。
 * <p>
 * 整合拆牌规划（{@link DdzSplitPlanner}）、合法牌型压制搜索（{@link DdzLegalBeatFinder}）、
 * 阶段划分与农民阵营助攻机制，为机器人提供拟人化出牌决策。单个方法均在 40 行以内。
 *
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @since 1.0
 */
public final class DdzSimpleAi {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(DdzSimpleAi.class);

    private DdzSimpleAi() {
    }

    /**
     * 机器人主出牌决策入口。
     * <p>
     * 依次完成：信息视野感知装配 → 无脑 AI 兜底 → 首出决策（首出规划或大师搜索）
     * → 队友让牌识别 → 合法压制过滤与最小成本跟牌。
     *
     * @param table 当前斗地主牌桌实体
     * @param user  待决策的 AI 玩家实体
     * @return 最终构建的 Protobuf 出牌或过牌操作载荷
     */
    public static GameProto.OpInfo decide(DdzTable table, TableUser user) {
        List<Card> hand = new ArrayList<>(user.getCards());
        if (hand.isEmpty()) {
            return pass();
        }
        int visionLevel = table.getDdz().getVisionLevel();
        int aiLevel = table.getDdz().getAiLevel();
        visionLevel = AiVision.effectiveVisionLevel(visionLevel, aiLevel);
        DdzVision vision = new DdzVision(table, user, visionLevel, aiLevel);

        // 弱智档 AI：纯随机或出最小单张，不进行策略计算
        if (aiLevel == AiVision.AI_DUMB) {
            return decideDumb(hand, table);
        }

        int phase = phaseOf(hand.size());
        DdzHand last = table.getDdz().getLastHand();

        // 1. 首出分支：当前桌面无牌或上一轮全员 PASS
        if (last == null || last.getCards().isEmpty()) {
            if (aiLevel >= AiVision.AI_MASTER) {
                return DdzMasterAi.lead(hand, phase, new AiSearchBudget(80, 2400));
            }
            return lead(hand, phase, vision);
        }

        // 2. 农民配合分支：队友刚出过牌且控场，己方应主动让行让队友逃牌
        if (shouldPassAfterTeammate(table, user, vision)) {
            return pass();
        }

        // 3. 被动跟牌分支：枚举所有合法大过上家的牌型并择优压制
        int minOppCards = vision.getMinOpponentCards();
        List<DdzHand> beats = DdzLegalBeatFinder.findBeatingHands(hand, last);
        beats = filterHeavyBeats(beats, last, phase, minOppCards);
        if (beats.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("[DDZ-AI-Decide] 玩家: {}, 上家牌: {}, 无法压制, 选择 PASS", user.getUserId(), last.getType());
            }
            return pass();
        }

        DdzHand pick = aiLevel >= AiVision.AI_MASTER
                ? DdzMasterAi.pickBeat(hand, beats, minOppCards, new AiSearchBudget(80, 2400))
                : pickCheapestBeat(beats, minOppCards);
        if (logger.isDebugEnabled()) {
            logger.debug("[DDZ-AI-Decide] 玩家: {}, 上家牌: {}, 候选数: {}, 选择出牌: {}",
                    user.getUserId(), last.getType(), beats.size(), pick.getType());
        }
        return playHand(pick);
    }

    /**
     * 弱智档 AI（AI_DUMB）保底决策：首出取最小拆数组或单牌，跟牌直接不出。
     *
     * @param hand  当前手牌
     * @param table 牌桌实体
     * @return 操作载荷
     */
    private static GameProto.OpInfo decideDumb(List<Card> hand, DdzTable table) {
        DdzHand last = table.getDdz().getLastHand();
        if (last != null && !last.getCards().isEmpty()) {
            return pass();
        }
        List<CardGroup> plan = DdzSplitPlanner.plan(hand);
        if (!plan.isEmpty()) {
            Optional<DdzHand> o = DdzRules.analyze(plan.get(0).getCards());
            if (o.isPresent()) {
                return playHand(o.get());
            }
        }
        Card c = Collections.min(hand);
        Optional<DdzHand> one = DdzRules.analyze(Collections.singletonList(c));
        return one.map(DdzSimpleAi::playHand).orElseGet(DdzSimpleAi::pass);
    }

    /**
     * 根据剩余手牌张数推导所处的对局阶段。
     *
     * @param handSize 当前手牌总张数
     * @return 0 为前期（≥14张），1 为中期（8~13张），2 为残局（≤7张）
     */
    static int phaseOf(int handSize) {
        if (handSize >= DdzAiConstants.PHASE_EARLY_MIN_CARDS) {
            return 0;
        }
        if (handSize >= DdzAiConstants.PHASE_MID_MIN_CARDS) {
            return 1;
        }
        return 2;
    }

    /**
     * 判断当前农民玩家在队友出牌后是否应当主动让行（PASS）。
     * <p>
     * 例外情况（不让行）：
     * <ol>
     *   <li>自己能一手直接走完出光获胜；</li>
     *   <li>地主手牌危险（≤3张），必须封锁；</li>
     *   <li>队友打出的牌力过弱（低于阈值），己方主动接过来控场。</li>
     * </ol>
     *
     * @param table  牌桌实体
     * @param user   当前农民玩家实体
     * @param vision 视野控制器
     * @return true 表示应当助攻让行
     */
    private static boolean shouldPassAfterTeammate(DdzTable table, TableUser user, DdzVision vision) {
        if (!DdzAiConstants.AI_PASS_AFTER_TEAMMATE_PLAY) {
            return false;
        }
        int landlordSeat = table.getDdz().getLandlordSeat();
        int mySeat = user.getSeated();
        // 自己不是农民，或尚未定地主，不触发农民配合
        if (landlordSeat < 0 || mySeat == landlordSeat) {
            return false;
        }
        int lastSeat = table.getDdz().getLastPlaySeat();
        // 上一手并非同阵营队友出的牌，不触发让牌
        if (lastSeat < 0 || lastSeat == mySeat || lastSeat == landlordSeat) {
            return false;
        }

        // 己方手牌能一手打光直接斩杀获胜时，绝对不让
        if (user.getCards().size() <= 5) {
            DdzHand whole = DdzRules.analyze(new ArrayList<>(user.getCards())).orElse(null);
            if (whole != null) {
                return false;
            }
        }

        // 地主手牌告急进入斩杀线（≤3张）时，必须积极压制不可放行
        if (vision.getMinOpponentCards() <= DdzAiConstants.FARMER_DANGER_LANDLORD_CARDS) {
            return false;
        }

        // 队友出牌过弱容易被地主接走时，己方应主动接牌控场
        DdzHand lastHand = table.getDdz().getLastHand();
        if (lastHand != null && lastHand.getStrengthKey() < DdzAiConstants.FARMER_TEAMMATE_WEAK_THRESHOLD) {
            return false;
        }

        return true;
    }

    /**
     * 过滤过重牌型（在前期试探阶段避免用炸弹压制软牌）。
     *
     * @param beats       所有合法大过的牌型列表
     * @param last        桌面上一手牌型
     * @param phase       对局阶段
     * @param minOppCards 对手最小余牌数
     * @return 过滤后的牌型列表
     */
    private static List<DdzHand> filterHeavyBeats(List<DdzHand> beats, DdzHand last, int phase, int minOppCards) {
        // 对手快赢时取消所有过滤，允许全力动用炸弹拦截
        if (minOppCards <= DdzAiConstants.FOLLOW_BOMB_DANGER_OPP_CARDS) {
            return beats;
        }
        // 中后期或上一手本来就是炸弹，不作过滤
        if (phase > 0 || last.isBomb() || last.isRocket()) {
            return beats;
        }
        // 上一手牌面点数较大，允许动用强力牌
        if (last.getStrengthKey() > DdzAiConstants.FOLLOW_SOFT_LAST_STRENGTH_MAX) {
            return beats;
        }
        // 前期且上一手为软牌时，过滤掉炸弹与火箭
        List<DdzHand> light = new ArrayList<>();
        for (DdzHand h : beats) {
            if (!h.isBomb() && !h.isRocket()) {
                light.add(h);
            }
        }
        return light.isEmpty() ? beats : light;
    }

    /**
     * 在备选压制牌型中，按照「最小压制原则」选取综合代价最低的一手牌。
     *
     * @param beats       合法压制牌型列表
     * @param minOppCards 对手最小余牌数
     * @return 代价最低的最佳跟牌
     */
    static DdzHand pickCheapestBeat(List<DdzHand> beats, int minOppCards) {
        DdzHand best = null;
        double bestCost = Double.MAX_VALUE;
        boolean danger = minOppCards <= DdzAiConstants.FOLLOW_BOMB_DANGER_OPP_CARDS;
        for (DdzHand h : beats) {
            double cost = h.getStrengthKey() * DdzAiConstants.FOLLOW_STRENGTH_MARGIN_PENALTY;
            if (h.isBomb()) {
                cost += danger ? DdzAiConstants.FOLLOW_BOMB_BASE_COST * DdzAiConstants.FOLLOW_BOMB_DANGER_DISCOUNT
                        : DdzAiConstants.FOLLOW_BOMB_BASE_COST;
            }
            if (h.isRocket()) {
                cost += danger ? DdzAiConstants.FOLLOW_ROCKET_COST * DdzAiConstants.FOLLOW_BOMB_DANGER_DISCOUNT
                        : DdzAiConstants.FOLLOW_ROCKET_COST;
            }
            if (cost < bestCost) {
                bestCost = cost;
                best = h;
            }
        }
        return best;
    }

    /**
     * 基础启发式首出决策：综合成套拆牌、残局搜索与保留权重挑选最优起手牌。
     *
     * @param hand   当前手牌
     * @param phase  阶段
     * @param vision 视野
     * @return 出牌操作载荷
     */
    static GameProto.OpInfo lead(List<Card> hand, int phase, DdzVision vision) {
        // 残局搜索（≤5张时）：直接穷举最少步数必胜解
        if (hand.size() <= DdzAiConstants.PHASE_ENDGAME_MAX_CARDS) {
            DdzHand endgame = endgameSolve(hand);
            if (endgame != null) {
                return playHand(endgame);
            }
        }

        List<CardGroup> plan = DdzSplitPlanner.planBest(hand);
        Set<Long> seen = new HashSet<>();
        List<DdzHand> candidates = new ArrayList<>();
        for (CardGroup g : plan) {
            addLeadCandidate(g.getCards(), candidates, seen);
        }
        for (Card c : hand) {
            addLeadCandidate(Collections.singletonList(c), candidates, seen);
        }
        DdzHand best = null;
        double bestScore = Double.MAX_VALUE;
        for (DdzHand h : candidates) {
            double sc = scoreLead(h, phase);
            sc += DdzAiConstants.LEAD_PRESERVE_WEIGHT_SCALE * preserveHint(h);
            sc += DdzAiConstants.LEAD_RESIDUAL_GROUP_PENALTY * residualGroupCount(hand, h);
            if (sc < bestScore) {
                bestScore = sc;
                best = h;
            }
        }
        if (best == null) {
            Card c = Collections.min(hand);
            Optional<DdzHand> one = DdzRules.analyze(Collections.singletonList(c));
            return one.map(DdzSimpleAi::playHand).orElseGet(DdzSimpleAi::pass);
        }
        return playHand(best);
    }

    /**
     * 计算打出指定牌型后，剩余牌拆解出的散组数量（组数越少越好）。
     *
     * @param hand 当前完整手牌
     * @param play 假定打出的牌型
     * @return 剩余散组总数
     */
    private static int residualGroupCount(List<Card> hand, DdzHand play) {
        List<Card> remaining = new ArrayList<>(hand);
        for (Card c : play.getCards()) {
            remaining.remove(c);
        }
        if (remaining.isEmpty()) {
            return 0;
        }
        return DdzSplitPlanner.plan(remaining).size();
    }

    /**
     * 将一组扑克牌解析后去重装入首出候选集。
     *
     * @param cards      牌列表
     * @param candidates 候选集
     * @param seen       哈希去重集
     */
    static void addLeadCandidate(List<Card> cards, List<DdzHand> candidates, Set<Long> seen) {
        Optional<DdzHand> o = DdzRules.analyze(cards);
        if (!o.isPresent()) {
            return;
        }
        DdzHand h = o.get();
        if (seen.add(DdzLegalBeatFinder.hashHand(h))) {
            candidates.add(h);
        }
    }

    @FunctionalInterface
    private interface PreserveEvaluator {
        double evaluate(DdzHand h);
    }

    private static final java.util.Map<ConstProto.CardType, PreserveEvaluator> PRESERVE_EVALUATORS =
            new java.util.EnumMap<>(ConstProto.CardType.class);

    static {
        PRESERVE_EVALUATORS.put(ConstProto.CardType.STRAIGHT, h ->
                DdzAiConstants.SPLIT_WEIGHT_STRAIGHT_MIN_BONUS
                        + h.getCards().size() * DdzAiConstants.SPLIT_WEIGHT_STRAIGHT_PER_CARD);
        PRESERVE_EVALUATORS.put(ConstProto.CardType.STRAIGHT_DOUBLE, h ->
                DdzAiConstants.SPLIT_WEIGHT_STRAIGHT_DOUBLE_MIN_BONUS
                        + h.getStraightLen() * DdzAiConstants.SPLIT_WEIGHT_STRAIGHT_DOUBLE_PER_PAIR);
        PRESERVE_EVALUATORS.put(ConstProto.CardType.TRIPLE, h -> DdzAiConstants.SPLIT_WEIGHT_TRIPLE);
        PRESERVE_EVALUATORS.put(ConstProto.CardType.TRIPLE_ONE, h -> DdzAiConstants.SPLIT_WEIGHT_TRIPLE);
        PRESERVE_EVALUATORS.put(ConstProto.CardType.TRIPLE_DOUBLE, h -> DdzAiConstants.SPLIT_WEIGHT_TRIPLE);
        PRESERVE_EVALUATORS.put(ConstProto.CardType.PLANE_ONE, h -> DdzAiConstants.splitPlaneGroupScore(h.getStraightLen()));
        PRESERVE_EVALUATORS.put(ConstProto.CardType.PLANE_DOUBLE, h -> DdzAiConstants.splitPlaneGroupScore(h.getStraightLen()));
        PRESERVE_EVALUATORS.put(ConstProto.CardType.DOUBLE, h -> DdzAiConstants.SPLIT_WEIGHT_PAIR);
    }

    /**
     * 计算特定牌型的结构保留代价分（分值越高代表结构越强，尽量不拆）。
     *
     * @param h 牌型对象
     * @return 保留分分值
     */
    static double preserveHint(DdzHand h) {
        if (h.isRocket()) {
            return DdzAiConstants.SPLIT_WEIGHT_ROCKET;
        }
        if (h.isBomb()) {
            return DdzAiConstants.SPLIT_WEIGHT_BOMB;
        }
        PreserveEvaluator evaluator = PRESERVE_EVALUATORS.get(h.getType());
        return evaluator != null ? evaluator.evaluate(h) : DdzAiConstants.SPLIT_WEIGHT_SINGLE;
    }

    /**
     * 计算牌型在当前阶段下的首出倾向评分（得分越低代表越推荐先出）。
     *
     * @param h     待评估牌型
     * @param phase 对局阶段
     * @return 首出评分
     */
    static double scoreLead(DdzHand h, int phase) {
        double s = h.getStrengthKey();
        if (phase == 0) {
            if (h.isRocket()) {
                s += DdzAiConstants.LEAD_PENALTY_ROCKET_EARLY;
            }
            if (h.isBomb()) {
                s += DdzAiConstants.LEAD_PENALTY_BOMB_EARLY;
            }
        }
        if (h.getType() == ConstProto.CardType.SINGLE) {
            int v = h.getCards().get(0).getCardVal();
            if (v >= 7 && v <= 10) {
                s += DdzAiConstants.LEAD_BONUS_SINGLE_RANK_7_TO_10;
            }
            if (v >= 13 || v >= CardConst.SMALL_JOKER_VAL) {
                s += DdzAiConstants.LEAD_PENALTY_SINGLE_HIGH;
            }
        }
        if (h.getType() == ConstProto.CardType.DOUBLE) {
            int v = h.getCards().get(0).getCardVal();
            if (v <= 10) {
                s += DdzAiConstants.LEAD_BONUS_SMALL_PAIR_LOW_RANK;
            }
            if (v >= 13) {
                s += DdzAiConstants.LEAD_PENALTY_PAIR_HIGH_RANK;
            }
        }
        return s;
    }

    /**
     * 将确定打出的牌型封装为 Protobuf 操作协议。
     *
     * @param h 待打出牌型
     * @return Protobuf 出牌操作
     */
    static GameProto.OpInfo playHand(DdzHand h) {
        return GameProto.OpInfo.newBuilder()
                .setChoice(ConstProto.Operation.PLAY)
                .addOpCards(h.toCardInfo())
                .build();
    }

    /**
     * 残局搜索：在手牌 ≤5 张时，通过 BFS 深度搜索寻找最快出完手牌的路径。
     *
     * @param hand 当前残局手牌
     * @return 第一手应该打出的最优牌型；若无法推算则返回 null
     */
    private static DdzHand endgameSolve(List<Card> hand) {
        if (hand.isEmpty()) {
            return null;
        }
        // 能直接一手打完，直接出
        Optional<DdzHand> whole = DdzRules.analyze(hand);
        if (whole.isPresent()) {
            return whole.get();
        }
        List<DdzHand> allPlays = enumerateAllPlays(hand);
        DdzHand bestFirst = null;
        int bestPlays = Integer.MAX_VALUE;
        for (DdzHand play : allPlays) {
            List<Card> remaining = removeCards(hand, play.getCards());
            int plays = endgameMinPlays(remaining, 0, bestPlays - 1);
            if (plays < bestPlays) {
                bestPlays = plays;
                bestFirst = play;
                if (bestPlays == 1) {
                    break; // 剩余仅需再打一手即出完，达到最优理论极值
                }
            }
        }
        return bestFirst;
    }

    /**
     * 残局递归剪枝：返回打光 remaining 手牌所需的最少手数。
     *
     * @param remaining 剩余手牌
     * @param depth     当前已消耗步数
     * @param cutoff    最优步数截断阈值
     * @return 最少打出手数
     */
    private static int endgameMinPlays(List<Card> remaining, int depth, int cutoff) {
        if (remaining.isEmpty()) {
            return depth;
        }
        if (depth >= cutoff) {
            return cutoff + 1; // 深度剪枝
        }
        if (DdzRules.analyze(remaining).isPresent()) {
            return depth + 1;
        }
        List<DdzHand> plays = enumerateAllPlays(remaining);
        int best = cutoff + 1;
        for (DdzHand play : plays) {
            List<Card> next = removeCards(remaining, play.getCards());
            int r = endgameMinPlays(next, depth + 1, best - 1);
            if (r < best) {
                best = r;
                if (best <= depth + 1) {
                    break;
                }
            }
        }
        return best;
    }

    /**
     * 枚举手牌中所有非空子集所构成的全部合法牌型。
     *
     * @param hand 当前手牌
     * @return 所有合法牌型列表
     */
    private static List<DdzHand> enumerateAllPlays(List<Card> hand) {
        List<DdzHand> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        int n = hand.size();
        for (int mask = 1; mask < (1 << n); mask++) {
            List<Card> subset = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    subset.add(hand.get(i));
                }
            }
            Optional<DdzHand> o = DdzRules.analyze(subset);
            if (o.isPresent() && seen.add(DdzLegalBeatFinder.hashHand(o.get()))) {
                result.add(o.get());
            }
        }
        return result;
    }

    /**
     * 从手牌列表中扣除指定牌集合，生成新的余牌列表副本。
     *
     * @param hand     原手牌
     * @param toRemove 待移除牌集合
     * @return 扣除后的新牌列表
     */
    static List<Card> removeCards(List<Card> hand, List<Card> toRemove) {
        List<Card> remaining = new ArrayList<>(hand);
        for (Card c : toRemove) {
            remaining.remove(c);
        }
        return remaining;
    }

    /**
     * 生成过牌（PASS）操作协议载荷。
     *
     * @return PASS 操作对象
     */
    private static GameProto.OpInfo pass() {
        return GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PASS).build();
    }
}
