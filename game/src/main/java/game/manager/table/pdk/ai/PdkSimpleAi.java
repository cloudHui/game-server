package game.manager.table.pdk.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import game.manager.table.TableUser;
import game.manager.table.card.CardConst;
import game.manager.table.cards.Card;
import game.manager.table.ddz.DdzHand;
import game.manager.table.ddz.ai.AiVision;
import game.manager.table.pdk.PdkRules;
import game.manager.table.pdk.PdkTable;
import game.manager.table.pdk.PdkTableContext;
import proto.ConstProto;
import proto.GameProto;

/**
 * 跑得快 AI 分级决策中枢与启发式决策基类。
 * <p>
 * 架构职责：
 * <ul>
 *   <li><b>分级路由：</b>依据牌桌配置的 AI 等级 ({@link PdkTableContext#getAiLevel()}) 分发给不同策略实现：
 *       大师档流转至 {@link PdkMasterAi}，中高级走本类启发式评估，初级/低级走贪心简易规则；</li>
 *   <li><b>残局最少手数规划：</b>手牌较少时通过深搜剪枝 ({@link #endgameFirst}) 求解理论最少出完手数；</li>
 *   <li><b>组数与结构估算：</b>提供贪心顺子连对划分与手数估值函数 ({@link #estimateGroups})。</li>
 * </ul>
 */
public final class PdkSimpleAi {

    /** 触发终局精准最少手数深搜的手牌张数阈值（<=6 张） */
    private static final int ENDGAME_MAX = 6;

    /** 终局搜索最大探索节点数配额，防止指数级展开造成卡顿 */
    private static final int ENDGAME_NODE_BUDGET = 350;

    /** 对手濒危预警手牌张数（对手剩余手牌 <= 2 时启动阻截防范） */
    private static final int DANGER_OPP_CARDS = 2;

    /** 跟牌时同类型候选牌型的最大评估数量（候选已按从小到大排序，剪掉过大冗余牌） */
    private static final int MAX_SAME_TYPE_EVAL = 8;

    private PdkSimpleAi() {}

    /**
     * 跑得快 AI 出牌决策统一入口。
     *
     * @param table 当前牌桌
     * @param user  当前待行动的 AI 玩家
     * @return 最终决定的出牌或过牌协议对象
     */
    public static GameProto.OpInfo decide(PdkTable table, TableUser user) {
        List<Card> hand = new ArrayList<>(user.getCards());
        if (hand.isEmpty()) {
            return pass();
        }
        Collections.sort(hand);

        int aiLevel = table.getPdk().getAiLevel();
        // 大师档：使用高级拆牌搜索与结构评估
        if (aiLevel >= AiVision.AI_MASTER) {
            return PdkMasterAi.decide(table, user);
        }
        // 呆板初级档：只出最小合法牌
        if (aiLevel <= AiVision.AI_DUMB) {
            return decideDumb(hand, table.getPdk().getLastHand());
        }
        // 中高级档：经典启发式（手数估计 + 残局搜索）
        return decideBasic(table, user, hand);
    }

    /**
     * 中高级 AI 启发式决策：首出综合估分，跟牌择优跟进。
     *
     * @param table 当前牌桌
     * @param user  决策玩家
     * @param hand  手牌列表
     * @return 出牌操作
     */
    private static GameProto.OpInfo decideBasic(PdkTable table, TableUser user, List<Card> hand) {
        int minOpp = minOpponentCards(table, user);
        DdzHand last = table.getPdk().getLastHand();
        if (last == null) {
            return playHand(pickLead(hand, minOpp));
        }
        List<DdzHand> beats = PdkRules.findAllBeatingHands(hand, last);
        if (beats.isEmpty()) {
            return pass();
        }
        return playHand(pickBeat(hand, beats, minOpp));
    }

    /**
     * 呆板初级档 AI 决策：被动跟牌出最小合法压制，首出出最小单张，管不上则过牌。
     *
     * @param hand 当前手牌
     * @param last 上家出牌
     * @return 出牌操作
     */
    private static GameProto.OpInfo decideDumb(List<Card> hand, DdzHand last) {
        if (last != null) {
            List<DdzHand> beats = PdkRules.findAllBeatingHands(hand, last);
            if (beats.isEmpty()) {
                return pass();
            }
            return playHand(beats.get(0));
        }
        // 首出最小牌（hand 已排序，取末尾即点数最小）
        Card smallest = hand.get(hand.size() - 1);
        return playHand(PdkRules.analyze(Collections.singletonList(smallest)).orElse(null));
    }

    /**
     * 根据剩余手牌数划分对局阶段。
     *
     * @param handSize 当前手牌总数
     * @return 阶段标识（0:前期手牌>=12，1:中期手牌>=7，2:残局手牌<7）
     */
    static int phaseOf(int handSize) {
        if (handSize >= PdkAiConstants.PHASE_EARLY_MIN_CARDS) {
            return 0;
        }
        if (handSize >= PdkAiConstants.PHASE_MID_MIN_CARDS) {
            return 1;
        }
        return 2;
    }

    /**
     * 启发式首出选择：权衡能直接出完、残局最少手数、以及综合手数惩罚。
     *
     * @param hand   当前手牌
     * @param minOpp 对手最小手牌张数
     * @return 最佳首出牌型
     */
    static DdzHand pickLead(List<Card> hand, int minOpp) {
        // 斩杀判定：整手牌本身构成合法牌型，直接一把打完获胜
        DdzHand whole = PdkRules.analyze(hand).orElse(null);
        if (whole != null) {
            return whole;
        }

        // 残局冲刺：手牌 <= 6 张时，搜索能以最少手数出完的起手牌
        if (hand.size() <= ENDGAME_MAX) {
            DdzHand end = endgameFirst(hand);
            if (end != null) {
                return end;
            }
        }

        // 枚举规则层所有合法首出
        List<DdzHand> candidates = PdkRules.enumerateLeadHands(hand);
        if (candidates.isEmpty()) {
            Card smallest = hand.get(hand.size() - 1);
            return PdkRules.analyze(Collections.singletonList(smallest)).orElse(null);
        }

        int[] counts = countByValue(hand);
        DdzHand best = null;
        double bestScore = Double.MAX_VALUE;
        for (DdzHand h : candidates) {
            double sc = scoreLead(hand, h, minOpp, counts);
            if (sc < bestScore) {
                bestScore = sc;
                best = h;
            }
        }
        return best;
    }

    /**
     * 评估主动首发某牌型的代价得分（分数越低越优）。
     *
     * @param hand   当前手牌
     * @param h      拟首发的候选牌型
     * @param minOpp 对手最小手牌张数
     * @param counts 手牌点数直方图
     * @return 评估得分
     */
    private static double scoreLead(List<Card> hand, DdzHand h, int minOpp, int[] counts) {
        List<Card> left = removeCards(hand, h.getCards());
        int residual = estimateGroups(left);

        // 剩余手数是核心考量：每多一手惩罚 40 分
        double score = residual * 40.0;
        // 一次性出掉的牌张数越多越奖励
        score -= h.getCards().size() * 6.0;
        // 基础牌力消耗
        score += h.getStrengthKey() * 0.35;

        // 炸弹使用策略：对手危险（<=2张）时鼓励开炸抢权，非危险期严惩首发出炸
        if (h.isBomb()) {
            score += minOpp <= DANGER_OPP_CARDS ? -30 : 80;
        } else if (breaksBomb(counts, h)) {
            // 严厉惩罚拆掉自己天然炸弹的行为
            score += 25;
        }

        // 直接清空手牌大幅奖励
        if (left.isEmpty()) {
            score -= 1000;
        }
        // 下手即可直接清空大幅奖励
        if (residual == 1 && !left.isEmpty()) {
            score -= 50;
        }

        // 首发大单张（>=K）容易浪费控场顶张，给予惩罚
        if (h.getType() == ConstProto.CardType.SINGLE && h.getStrengthKey() >= 13) {
            score += 12;
        }
        // 顺子、连对、三带等多张结构牌优先首发走牌
        if (h.getType() == ConstProto.CardType.STRAIGHT
                || h.getType() == ConstProto.CardType.STRAIGHT_DOUBLE
                || h.getType() == ConstProto.CardType.TRIPLE_ONE
                || h.getType() == ConstProto.CardType.TRIPLE_DOUBLE) {
            score -= 8;
        }
        return score;
    }

    /**
     * 启发式跟牌挑选：在合法管牌中寻找最不伤牌力且最不拆散结构的打法。
     *
     * @param hand   当前手牌
     * @param beats  合法能管的牌型列表
     * @param minOpp 对手最小手牌数
     * @return 选中的管牌
     */
    private static DdzHand pickBeat(List<Card> hand, List<DdzHand> beats, int minOpp) {
        // 斩杀判定：若能直接把手牌一把出完，毫不犹豫立即打出
        for (DdzHand h : beats) {
            if (h.getCards().size() == hand.size()) {
                return h;
            }
        }

        boolean danger = minOpp <= DANGER_OPP_CARDS;
        int[] counts = countByValue(hand);
        DdzHand best = null;
        double bestScore = Double.MAX_VALUE;
        int sameTypeSeen = 0;

        for (DdzHand h : beats) {
            // 剪枝优化：同类型管牌已按点数递增排序，评估超过阈值后跳过更大牌，保留大牌实力
            if (!h.isBomb()) {
                sameTypeSeen++;
                if (sameTypeSeen > MAX_SAME_TYPE_EVAL) {
                    continue;
                }
            } else if (!danger && best != null && !best.isBomb()) {
                // 非危急状态且已有非炸牌型能管上时，不滥用炸弹
                continue;
            }

            double sc = h.getStrengthKey();
            List<Card> left = removeCards(hand, h.getCards());
            sc += estimateGroups(left) * 18.0;
            sc -= h.getCards().size() * 3.0;

            if (h.isBomb()) {
                sc += danger ? -40 : 120;
            } else if (breaksBomb(counts, h)) {
                // 拆炸惩罚
                sc += 35;
            } else if (h.getType() == ConstProto.CardType.SINGLE && counts[h.getStrengthKey()] >= 2) {
                // 拆对出单轻微惩罚
                sc += 8;
            }

            if (left.isEmpty()) {
                sc -= 1000;
            }
            if (sc < bestScore) {
                bestScore = sc;
                best = h;
            }
        }
        return best != null ? best : beats.get(0);
    }

    /**
     * 检查拟打出的牌型是否拆散了手牌中已有的 4 张同点数天然炸弹。
     *
     * @param counts 手牌点数直方图
     * @param play   拟出牌型
     * @return 若拆散了炸弹返回 true
     */
    private static boolean breaksBomb(int[] counts, DdzHand play) {
        if (play.isBomb()) {
            return false;
        }
        for (Card c : play.getCards()) {
            int v = c.getCardVal();
            if (v >= 0 && v < counts.length && counts[v] == 4) {
                return true;
            }
        }
        return false;
    }

    /**
     * 统计手牌各点数的张数直方图。
     *
     * @param hand 当前卡牌列表
     * @return 点数直方图数组（下标为点数值）
     */
    private static int[] countByValue(List<Card> hand) {
        int[] counts = new int[CardConst.ER_VAL + 1];
        for (Card c : hand) {
            int v = c.getCardVal();
            if (v >= 0 && v < counts.length) {
                counts[v]++;
            }
        }
        return counts;
    }

    /**
     * 快速估算一组手牌所需的出牌手数（拆牌组数）。
     * <p>
     * 贪心顺序：四张炸弹(1组) → 三张(1组) → 顺子(>=5张) → 对子与连对 → 散牌。
     *
     * @param cards 待评估手牌
     * @return 估算的总组数（>=1）
     */
    private static int estimateGroups(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return 0;
        }
        int[] counts = countByValue(cards);
        int groups = 0;
        List<Integer> singles = new ArrayList<>(12);
        List<Integer> pairs = new ArrayList<>(8);

        // 提取炸弹与三张，并收集剩余单牌与对子点数
        for (int v = 3; v <= CardConst.ER_VAL; v++) {
            int n = counts[v];
            if (n <= 0) {
                continue;
            }
            if (n >= 4) {
                groups++;
                n -= 4;
            }
            if (n >= 3) {
                groups++;
                n -= 3;
            }
            if (n >= 2) {
                pairs.add(v);
                n -= 2;
            }
            if (n >= 1) {
                singles.add(v);
            }
        }

        // 顺子贪心拼接与计数
        groups += takeStraightRuns(singles, 5);
        groups += singles.size();
        // 连对贪心拼接与计数
        groups += takeStraightRuns(pairs, 2);
        groups += pairs.size();
        return Math.max(1, groups);
    }

    /**
     * 从有序点数序列中贪心提取满足最小连续长度的顺子，并扣除消耗的点数。
     *
     * @param vals   升序点数列表
     * @param minLen 最小连续长度（单顺>=5，双顺>=2）
     * @return 成功提取的顺子组数
     */
    private static int takeStraightRuns(List<Integer> vals, int minLen) {
        int runs = 0;
        int i = 0;
        while (i < vals.size()) {
            int j = i + 1;
            // 2 不能参与顺子，连续条件为 vals[j] == vals[j-1] + 1
            while (j < vals.size() && vals.get(j) == vals.get(j - 1) + 1 && vals.get(j) < CardConst.ER_VAL) {
                j++;
            }
            if (j - i >= minLen) {
                runs++;
                vals.subList(i, j).clear();
            } else {
                i = j;
            }
        }
        return runs;
    }

    /**
     * 终局搜索：寻找能以最少手数全部出完手牌的最优起手牌。
     *
     * @param hand 当前手牌（<=6张）
     * @return 最少手数方案的第一手出牌
     */
    static DdzHand endgameFirst(List<Card> hand) {
        int[] nodes = { 0 };
        List<DdzHand> plays = PdkRules.enumerateLeadHands(hand);
        DdzHand best = null;
        int bestPlays = Integer.MAX_VALUE;

        for (DdzHand play : plays) {
            if (nodes[0] > ENDGAME_NODE_BUDGET) {
                break;
            }
            List<Card> left = removeCards(hand, play.getCards());
            int need = 1 + minPlays(left, bestPlays - 1, nodes);
            if (need < bestPlays) {
                bestPlays = need;
                best = play;
                // 1 手已是最优下限，提前结束
                if (bestPlays <= 1) {
                    break;
                }
            }
        }
        return best;
    }

    /**
     * 递归搜索将剩余手牌全部出完所需的最小手数（Alpha-Beta 风格上界剪枝）。
     *
     * @param left   剩余手牌
     * @param cutoff 当前已发现的最优解截断阈值
     * @param nodes  探索节点计数器
     * @return 所需手数
     */
    private static int minPlays(List<Card> left, int cutoff, int[] nodes) {
        if (left.isEmpty()) {
            return 0;
        }
        if (cutoff <= 0) {
            return cutoff + 1;
        }
        if (++nodes[0] > ENDGAME_NODE_BUDGET) {
            return Math.max(cutoff, estimateGroups(left));
        }
        if (PdkRules.analyze(left).isPresent()) {
            return 1;
        }

        int best = cutoff + 1;
        for (DdzHand play : PdkRules.enumerateLeadHands(left)) {
            int r = 1 + minPlays(removeCards(left, play.getCards()), best - 1, nodes);
            if (r < best) {
                best = r;
                if (best <= 1) {
                    break;
                }
            }
            if (nodes[0] > ENDGAME_NODE_BUDGET) {
                break;
            }
        }
        return best;
    }

    /**
     * 获取除自身以外的所有对手中的最小剩余手牌张数。
     *
     * @param table 当前牌桌
     * @param me    当前玩家
     * @return 对手中最小手牌张数
     */
    static int minOpponentCards(PdkTable table, TableUser me) {
        int min = Integer.MAX_VALUE;
        for (TableUser u : table.getSeatUsers().values()) {
            if (u == null || u.getUserId() == me.getUserId()) {
                continue;
            }
            min = Math.min(min, u.getCards().size());
        }
        return min == Integer.MAX_VALUE ? 99 : min;
    }

    /**
     * 从完整手牌列表中安全移除指定打出的卡牌。
     *
     * @param hand 当前完整手牌
     * @param play 准备打出的卡牌列表
     * @return 移除后的新卡牌列表
     */
    static List<Card> removeCards(List<Card> hand, List<Card> play) {
        List<Card> left = new ArrayList<>(hand.size());
        left.addAll(hand);
        for (Card c : play) {
            left.remove(c);
        }
        return left;
    }

    /**
     * 将牌型对象封装为出牌协议操作。
     *
     * @param h 拟出的牌型（若为 null 则封装为过牌）
     * @return 操作协议对象
     */
    static GameProto.OpInfo playHand(DdzHand h) {
        if (h == null) {
            return pass();
        }
        return GameProto.OpInfo.newBuilder()
                .setChoice(ConstProto.Operation.PLAY)
                .addOpCards(h.toCardInfo())
                .build();
    }

    /**
     * 封装过牌（不出）协议操作。
     *
     * @return 过牌操作对象
     */
    static GameProto.OpInfo pass() {
        return GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PASS).build();
    }
}

