package game.domain.poker.niuniu.ai;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import game.domain.cards.Card;
import game.domain.poker.niuniu.NiuniuFlow;
import game.domain.poker.niuniu.NiuniuRules;
import game.domain.poker.niuniu.NiuniuTable;
import game.domain.table.TableUser;

/**
 * 牛牛陪练机器人决策引擎：依据前 4 张明牌成牛潜力决策抢庄与下注倍数。
 */
public final class NiuniuAi {

    /** 4 张明牌中 4 选 3 凑十检测组合（前 3 项为牛身下标，第 4 项为余牌下标） */
    private static final int[][] IDX_4_CHOOSE_3 = { { 0, 1, 2, 3 }, { 0, 1, 3, 2 }, { 0, 2, 3, 1 }, { 1, 2, 3, 0 } };

    private NiuniuAi() {
    }

    /**
     * 机器人在明牌抢庄阶段根据前 4 张手牌潜力决策抢庄倍数（0~4 倍）。
     *
     * @param table 牛牛牌桌实例
     * @param robot 机器人玩家实体
     */
    public static void rob(NiuniuTable table, TableUser robot) {
        if (!isValidRobot(robot) || table.getContext().getRobMultipliers().containsKey(robot.getSeated())) {
            return;
        }
        int q = evalPotential(robot.getCards());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int mult = q >= 4 ? rnd.nextInt(3, 5)
                : (q == 3 ? rnd.nextInt(1, 4) : (q == 2 ? rnd.nextInt(0, 3) : rnd.nextInt(0, 2)));
        NiuniuFlow.onRob(table, robot.getUserId(), mult);
    }

    /**
     * 机器人在闲家加倍阶段根据手牌潜力决策下注倍数（1~5 倍）。
     *
     * @param table 牛牛牌桌实例
     * @param robot 机器人玩家实体
     */
    public static void bet(NiuniuTable table, TableUser robot) {
        int seat = robot != null ? robot.getSeated() : -1;
        if (!isValidRobot(robot) || seat == table.getContext().getBankerSeat()
                || table.getContext().getBetMultipliers().containsKey(seat)) {
            return;
        }
        int q = evalPotential(robot.getCards());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int mult = q >= 4 ? rnd.nextInt(3, 6) : (q == 3 ? rnd.nextInt(2, 5) : rnd.nextInt(1, 3));
        NiuniuFlow.onBet(table, robot.getUserId(), mult);
    }

    /**
     * 机器人在拼牛摊牌阶段自动完成亮牌。
     *
     * @param table 牛牛牌桌实例
     * @param robot 机器人玩家实体
     */
    public static void show(NiuniuTable table, TableUser robot) {
        if (isValidRobot(robot) && !table.getContext().getShowedSeats().contains(robot.getSeated())) {
            NiuniuFlow.onShow(table, robot.getUserId());
        }
    }

    private static boolean isValidRobot(TableUser robot) {
        return robot != null && robot.isRobot() && robot.getSeated() >= 0;
    }

    private static int evalPotential(List<Card> cards) {
        if (cards == null || cards.size() < 4) {
            return 1;
        }
        // 三条胚子具备冲击炸弹牛潜力，按最高档评估
        for (int i = 0; i < 4; i++) {
            int same = 1;
            for (int j = i + 1; j < 4; j++) {
                if (NiuniuRules.getCardRank(cards.get(i)) == NiuniuRules.getCardRank(cards.get(j))) {
                    same++;
                }
            }
            if (same >= 3) {
                return 4;
            }
        }
        int bestRest = -1;
        for (int[] c : IDX_4_CHOOSE_3) {
            int sum3 = NiuniuRules.getNiuPoint(cards.get(c[0]))
                    + NiuniuRules.getNiuPoint(cards.get(c[1]))
                    + NiuniuRules.getNiuPoint(cards.get(c[2]));
            if (sum3 % 10 == 0) {
                bestRest = Math.max(bestRest, NiuniuRules.getNiuPoint(cards.get(c[3])));
            }
        }
        if (bestRest >= 8) {
            return 4;
        }
        if (bestRest >= 0) {
            return 3;
        }
        int tens = 0;
        for (int i = 0; i < 4; i++) {
            if (NiuniuRules.getNiuPoint(cards.get(i)) == 10) {
                tens++;
            }
            for (int j = i + 1; j < 4; j++) {
                if ((NiuniuRules.getNiuPoint(cards.get(i)) + NiuniuRules.getNiuPoint(cards.get(j))) % 10 == 0) {
                    return 3;
                }
            }
        }
        return tens >= 2 ? 2 : 1;
    }
}
