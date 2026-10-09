package game.manager.table.pdk.ai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import game.manager.table.ai.AiSearchBudget;
import game.manager.table.cards.Card;
import game.manager.table.ddz.DdzHand;
import game.manager.table.ddz.ai.CardGroup;
import game.manager.table.pdk.PdkRules;
import game.manager.table.pdk.PdkTable;
import game.manager.table.TableUser;
import proto.ConstProto;
import proto.GameProto;

/**
 * 跑得快大师档 AI 决策器：参照斗地主 {@code DdzMasterAi} 的评估架构微调。
 * <p>
 * 核心设计策略：
 * <ul>
 *   <li><b>首出决策：</b>拆牌规划生成候选牌型 → 评估出牌后剩余手牌的结构质量（剩余组数、保留价值）→ 限时节点预算搜索选取代价最低的一手；</li>
 *   <li><b>跟牌决策：</b>严格遵循跑得快“有牌必管”强制规则，在所有合法能管的牌中，权衡破坏自身结构的代价与控场消耗，非危急关头克制用炸，对手濒危报单时果断轰炸拦截；</li>
 *   <li><b>残局冲刺：</b>手牌较少时启用终局斩杀检测与全子集搜索，争取以最少手数一把清或两手清。</li>
 * </ul>
 */
public final class PdkMasterAi {

    /** 私有构造器，工具类禁止实例化 */
    private PdkMasterAi() {}

    /**
     * 大师档 AI 出牌/跟牌顶层决策入口。
     * <p>
     * 依据当前桌面牌权状态分别流转至首出决策 ({@link #lead}) 或跟牌决策 ({@link #pickBeat})。
     *
     * @param table 当前牌桌上下文（包含底牌、上家出牌、各玩家手牌数等）
     * @param user  当前待决策的 AI 玩家
     * @return 封装好的出牌或过牌操作协议对象
     */
    static GameProto.OpInfo decide(PdkTable table, TableUser user) {
        List<Card> hand = new ArrayList<>(user.getCards());
        // 异常防御：若手牌已空则直接过牌，避免后续空指针
        if (hand.isEmpty()) return PdkSimpleAi.pass();
        java.util.Collections.sort(hand);

        // 收集对手中最少的手牌数，用于感知整桌危急程度（如对手报单/报双预警）
        int minOpp = PdkSimpleAi.minOpponentCards(table, user);
        DdzHand last = table.getPdk().getLastHand();

        // 分配大师档搜索的计算预算，防止多轮搜索或复杂残局导致单步超时卡顿
        AiSearchBudget budget = new AiSearchBudget(
                PdkAiConstants.MASTER_TIME_MS, PdkAiConstants.MASTER_MAX_NODES);

        // 桌面无上家压牌，当前拥有主动出牌权，执行首出规划
        if (last == null) {
            return lead(hand, minOpp, budget);
        }

        // 跑得快规则强制“有牌必管”：先提取所有能压过上家的合法牌型
        List<DdzHand> beats = PdkRules.findAllBeatingHands(hand, last);
        if (beats.isEmpty()) return PdkSimpleAi.pass();

        // 从所有合法管牌中筛选对自身手牌破坏最小、最具控场效益的最优解
        return PdkSimpleAi.playHand(pickBeat(hand, beats, minOpp, budget));
    }

    /**
     * 首出（主动领出）决策：评估各种候选出牌，选择剩余手牌结构最健康的打法。
     *
     * @param hand   当前手牌（已按点数从小到大排序）
     * @param minOpp 场上对手中剩余手牌的最少张数
     * @param budget 搜索步数与耗时预算控制器
     * @return 最终决定的出牌操作
     */
    private static GameProto.OpInfo lead(List<Card> hand, int minOpp, AiSearchBudget budget) {
        // 进入终局（手牌<=7张）：优先检索能否以最少手数直接斩杀清手，避免常规权衡导致错失连招杀机
        if (hand.size() <= PdkAiConstants.PHASE_ENDGAME_MAX_CARDS) {
            DdzHand end = PdkSimpleAi.endgameFirst(hand);
            if (end != null) return PdkSimpleAi.playHand(end);
        }

        // 综合拆牌规划、规则枚举合法牌型以及残局子集，生成多样化首出候选集
        List<DdzHand> candidates = leadCandidates(hand, budget);
        DdzHand best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int phase = PdkSimpleAi.phaseOf(hand.size());

        // 遍历所有候选牌型，采用极小化代价打分（评估得分越低代表剩余牌型越优）
        for (DdzHand candidate : candidates) {
            // 超出时间或搜索节点配额时熔断早停，保证服务端实时响应
            if (budget.tryNotVisit()) break;

            double score = scorePlay(hand, candidate, phase, minOpp, budget);
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        // 兜底防御：若搜索因耗尽预算或未命中有效解，降级使用简易规则启发式选出首发牌
        if (best == null) {
            return PdkSimpleAi.playHand(PdkSimpleAi.pickLead(hand, minOpp));
        }
        return PdkSimpleAi.playHand(best);
    }

    /**
     * 跟牌（被动管牌）挑选：在满足“有牌必管”的前提下，权衡自身结构破坏与关键压制收益。
     *
     * @param hand   当前手牌
     * @param beats  所有能管住上家牌的合法牌型集合（非空）
     * @param minOpp 场上对手中最少手牌数
     * @param budget 搜索预算控制器
     * @return 综合代价最小的跟牌方案
     */
    private static DdzHand pickBeat(
            List<Card> hand, List<DdzHand> beats, int minOpp, AiSearchBudget budget) {
        int phase = PdkSimpleAi.phaseOf(hand.size());
        DdzHand best = beats.get(0);
        double bestScore = Double.POSITIVE_INFINITY;

        for (DdzHand beat : beats) {
            // 避免在大牌型分支展开时单步耗时过长，触发预算熔断
            if (budget.tryNotVisit()) break;

            // 基础评估：模拟打出该牌后剩余手牌的结构完整度
            double score = scorePlay(hand, beat, phase, minOpp, budget);
            // 叠加管牌成本：大牌点数消耗惩罚，以及非危险期保留炸弹的成本
            score += beatCost(beat, minOpp);

            if (score < bestScore) {
                bestScore = score;
                best = beat;
            }
        }
        return best;
    }

    /**
     * 评估打出指定牌型后的综合负面代价。
     * <p>
     * 评估准则采用“惩罚值越小越优”的设计：
     * <ul>
     *   <li>立即出完获胜：给予极巨负分奖励（{@link PdkAiConstants#FINISH_NOW_BONUS}）；</li>
     *   <li>下轮即可斩杀：剩余手牌为单一合法牌型时给予高额奖励；</li>
     *   <li>剩余手牌组数：组数越少说明手数越短，手数越多累加组数惩罚；</li>
     *   <li>报单防守：当对手仅剩极少手牌时，严惩出小单牌给对手喂牌的行为。</li>
     * </ul>
     *
     * @param hand   当前完整手牌
     * @param play   拟打出的候选牌型
     * @param phase  当前对局阶段（前期/中期/终局）
     * @param minOpp 对手最小手牌张数
     * @param budget 搜索预算
     * @return 打出该牌后的评估代价（分值越小越优秀）
     */
    private static double scorePlay(
            List<Card> hand, DdzHand play, int phase, int minOpp, AiSearchBudget budget) {
        List<Card> left = PdkSimpleAi.removeCards(hand, play.getCards());

        // 斩杀判定：打出后手牌直接归零，立即赢得本局，赋予最高优先级的奖励权值
        if (left.isEmpty()) return PdkAiConstants.FINISH_NOW_BONUS;

        // 评估当前出手牌型的初始形态偏好与固有保留权重（大牌/炸弹保留分越高，出手的代价越大）
        double score = scoreLeadShape(play, phase) + preserveHint(play) * PdkAiConstants.LEAD_PRESERVE_SCALE;

        // 对打出后的剩余手牌重新做拆牌规划：剩余组数直接反映了后续所需的回合手数
        List<CardGroup> plan = PdkSplitPlanner.planBest(left);
        score += plan.size() * PdkAiConstants.RESIDUAL_GROUP_PENALTY;

        // 次轮斩杀判定：若剩余所有手牌恰好能合成一手合法的完整牌型，下次回合即可打完，给予极高奖励
        if (PdkRules.analyze(left).isPresent()) {
            score += PdkAiConstants.FINISH_NEXT_BONUS;
        }

        // 细化剩余手牌质量：手牌中剩余有价值的牌型（如炸弹、长顺）越多，手牌结构越健康，微调保留分
        for (CardGroup group : plan) {
            if (budget.tryNotVisit()) break;
            Optional<DdzHand> analyzed = PdkRules.analyze(group.getCards());
            if (analyzed.isPresent()) {
                score += preserveHint(analyzed.get()) * 0.15;
            }
        }

        // 跑得快经典防守逻辑（防放走下家）：当对手仅剩 <= 2 张牌且处于极度危险时，严禁主动出 <=10 的小单牌喂牌
        if (minOpp <= PdkAiConstants.FOLLOW_BOMB_DANGER_OPP_CARDS
                && play.getType() == ConstProto.CardType.SINGLE
                && play.getStrengthKey() <= 10) {
            score += 40;
        }
        return score;
    }

    /**
     * 计算跟牌行为的额外资源损耗代价。
     * <p>
     * 压牌时消耗的点数越大，后续争夺牌权的筹码越少；炸弹在常规期极其昂贵，但在对手即将走完时允许低成本动用以实施阻截。
     *
     * @param beat   当前准备使用的管牌
     * @param minOpp 对手最小手牌张数
     * @return 跟牌本身的附加代价
     */
    private static double beatCost(DdzHand beat, int minOpp) {
        // 牌力消耗：点数越大成本越高，避免用过大的顶张（如 A、2）压制非关键小牌
        double cost = beat.getStrengthKey() * PdkAiConstants.FOLLOW_STRENGTH_PENALTY;

        if (beat.isBomb()) {
            // 对手剩余手牌 <= 2 张时进入斩杀警戒状态，炸弹成本打折鼓励使用；否则保留炸弹作为关键压制筹码
            boolean danger = minOpp <= PdkAiConstants.FOLLOW_BOMB_DANGER_OPP_CARDS;
            cost += danger
                    ? PdkAiConstants.FOLLOW_BOMB_BASE_COST * PdkAiConstants.FOLLOW_BOMB_DANGER_DISCOUNT
                    : PdkAiConstants.FOLLOW_BOMB_BASE_COST;
        }
        return cost;
    }

    /**
     * 评估首发牌型的形态偏好与阶段惩罚。
     *
     * @param h     拟首发的牌型
     * @param phase 当前阶段（0:前期手牌>=12张，1:中期手牌>=7张，2:残局）
     * @return 牌型形态调整得分
     */
    private static double scoreLeadShape(DdzHand h, int phase) {
        double s = h.getStrengthKey();

        // 开局前期手牌充裕时，严厉惩罚主动首发出炸弹，炸弹应当蓄势控场而非盲目抢跑
        if (phase == 0 && h.isBomb()) s += PdkAiConstants.LEAD_PENALTY_BOMB_EARLY;

        // 单牌出牌策略：优先清理 7~10 这一类难成顺子又无法充当大顶张的中段废单；严禁开局直接首发出大顶单牌（K/A/2）
        if (h.getType() == ConstProto.CardType.SINGLE) {
            int v = h.getStrengthKey();
            if (v >= 7 && v <= 10) s += PdkAiConstants.LEAD_BONUS_SINGLE_MID;
            if (PdkAiConstants.isTopSingle(v)) s += PdkAiConstants.LEAD_PENALTY_SINGLE_HIGH;
        }

        // 对子出牌策略：小对子（<=10）容易被压死，鼓励开局早顺走；大对子（>=K）具备过牌和控场价值，首出施加保留惩罚
        if (h.getType() == ConstProto.CardType.DOUBLE) {
            int v = h.getStrengthKey();
            if (v <= 10) s += PdkAiConstants.LEAD_BONUS_SMALL_PAIR;
            if (v >= 13) s += PdkAiConstants.LEAD_PENALTY_HIGH_PAIR;
        }
        return s;
    }

    /**
     * 计算牌型的固有保留价值估分。
     * <p>
     * 牌型越难形成、一次性出牌效率越高，其保留价值越大（如炸弹、长顺、连对）。
     *
     * @param h 待评估牌型
     * @return 牌型保留基准分
     */
    private static double preserveHint(DdzHand h) {
        // 炸弹具有绝对统治力与额外番数价值，保留权重最高
        if (h.isBomb()) return PdkAiConstants.SPLIT_WEIGHT_BOMB;

        switch (h.getType()) {
        case STRAIGHT:
            // 单顺子：基础门槛奖励 + 长度线性加权（顺子越长一次性消耗废牌越多，价值越高）
            return PdkAiConstants.SPLIT_WEIGHT_STRAIGHT_MIN_BONUS
                    + h.getCards().size() * PdkAiConstants.SPLIT_WEIGHT_STRAIGHT_PER_CARD;
        case STRAIGHT_DOUBLE:
            // 双顺（连对）：基础加成 + 连对对数加权
            return PdkAiConstants.SPLIT_WEIGHT_STRAIGHT_DOUBLE_MIN_BONUS
                    + h.getStraightLen() * PdkAiConstants.SPLIT_WEIGHT_STRAIGHT_DOUBLE_PER_PAIR;
        case TRIPLE_DOUBLE:
            return PdkAiConstants.SPLIT_WEIGHT_TRIPLE_DOUBLE;
        case TRIPLE_ONE:
            return PdkAiConstants.SPLIT_WEIGHT_TRIPLE_ONE;
        case TRIPLE:
            return PdkAiConstants.SPLIT_WEIGHT_TRIPLE;
        case DOUBLE:
            return PdkAiConstants.SPLIT_WEIGHT_PAIR;
        default:
            return PdkAiConstants.SPLIT_WEIGHT_SINGLE;
        }
    }

    /**
     * 生成当前手牌的所有合法首出候选牌型。
     * <p>
     * 集合拆牌规划器、规则层合法牌型枚举以及残局子集穷举三种渠道，确保不会漏掉潜在的最优出牌路线。
     *
     * @param hand   当前手牌列表
     * @param budget 搜索预算控制器
     * @return 去重后的所有合法首出候选集合
     */
    private static List<DdzHand> leadCandidates(List<Card> hand, AiSearchBudget budget) {
        List<DdzHand> result = new ArrayList<>();
        // 采用去重集合避免相同牌型与牌值的重复计算
        Set<Long> seen = new HashSet<>();

        // 渠道 1：基于动态规划最优拆解出的牌组结构
        for (CardGroup group : PdkSplitPlanner.planBest(hand)) {
            addCandidate(group.getCards(), result, seen);
        }

        // 渠道 2：跑得快规则层全量枚举首出合法牌型，避免拆牌规划过于贪心而漏掉长连对/长单顺
        for (DdzHand h : PdkRules.enumerateLeadHands(hand)) {
            if (seen.add(hashHand(h))) result.add(h);
        }

        // 渠道 3：当手牌张数较少（<=10张）时，暴力枚举所有子集组合以挖掘非典型带牌（如三带一、残局杂牌连招）
        if (hand.size() <= PdkAiConstants.MASTER_SUBSET_MAX_CARDS) {
            addSubsets(hand, result, seen, budget);
        }
        return result;
    }

    /**
     * 二进制位掩码穷举手牌的所有非空子集并识别合法牌型。
     * <p>
     * 仅在手牌 <= 10 张的残局触发（组合数最多 2^10 = 1024 种），受搜索预算限制早停。
     *
     * @param hand   当前手牌
     * @param result 输出候选列表
     * @param seen   已处理牌型哈希去重集合
     * @param budget 搜索预算控制器
     */
    private static void addSubsets(
            List<Card> hand, List<DdzHand> result, Set<Long> seen, AiSearchBudget budget) {
        int n = hand.size();
        int combinations = 1 << n;

        // 掩码从 1 到 2^n - 1，按位提取手牌子集
        for (int mask = 1; mask < combinations && budget.isNotExhausted(); mask++) {
            List<Card> subset = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) subset.add(hand.get(i));
            }

            // 验证子集是否能构成有效跑得快牌型（顺子、三带、炸弹等）
            Optional<DdzHand> parsed = PdkRules.analyze(subset);
            if (parsed.isPresent() && seen.add(hashHand(parsed.get()))) {
                result.add(parsed.get());
            }
        }
    }

    /**
     * 辅助工具：尝试解析一组卡牌，若构成合法牌型且未被收录过，则加入候选列表。
     *
     * @param cards 待验证的卡牌列表
     * @param out   候选牌型收集容器
     * @param seen  哈希去重集合
     */
    private static void addCandidate(List<Card> cards, List<DdzHand> out, Set<Long> seen) {
        Optional<DdzHand> o = PdkRules.analyze(cards);
        if (o.isPresent() && seen.add(hashHand(o.get()))) out.add(o.get());
    }

    /**
     * 计算牌型对象的指纹哈希值，用于快速判重。
     * <p>
     * 复合特征包含：牌型种类、主牌点数、顺子长度以及所含具体卡牌 ID 序列。
     *
     * @param h 牌型对象
     * @return 64 位指纹哈希
     */
    private static long hashHand(DdzHand h) {
        long hash = h.getType().ordinal() * 31L + h.getStrengthKey() * 17L + h.getStraightLen();
        for (Card c : h.getCards()) hash = hash * 131 + c.getId();
        return hash;
    }
}
