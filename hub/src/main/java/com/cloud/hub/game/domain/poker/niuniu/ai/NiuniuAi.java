package com.cloud.hub.game.domain.poker.niuniu.ai;

import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.poker.niuniu.NiuniuFlow;
import com.cloud.hub.game.domain.poker.niuniu.NiuniuRules;
import com.cloud.hub.game.domain.poker.niuniu.NiuniuTable;
import com.cloud.hub.game.domain.table.TableUser;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 牛牛机器人陪练决策引擎。
 *
 * @author cloud
 */
public final class NiuniuAi {

    private NiuniuAi() {
    }

    public static void rob(NiuniuTable table, TableUser robot) {
        if (robot == null || !robot.isRobot()) {
            return;
        }

        int quality = evalCards(robot.getCards());
        int mult = (quality >= 3) ? ThreadLocalRandom.current().nextInt(2, 5)
                 : (quality == 2) ? ThreadLocalRandom.current().nextInt(1, 3)
                 : ThreadLocalRandom.current().nextInt(0, 2);

        NiuniuFlow.onRob(table, robot.getUserId(), mult);
    }

    public static void bet(NiuniuTable table, TableUser robot) {
        if (robot == null || !robot.isRobot()) {
            return;
        }
        if (robot.getSeated() == table.getContext().getBankerSeat()) {
            return;
        }

        int quality = evalCards(robot.getCards());
        int mult = Math.min(5, Math.max(1, quality));

        NiuniuFlow.onBet(table, robot.getUserId(), mult);
    }

    public static void show(NiuniuTable table, TableUser robot) {
        if (robot == null || !robot.isRobot()) {
            return;
        }
        NiuniuFlow.onShow(table, robot.getUserId());
    }

    private static int evalCards(List<Card> cards) {
        if (cards == null || cards.isEmpty()) {
            return 1;
        }
        int tenCount = 0;
        for (Card c : cards) {
            if (NiuniuRules.getNiuPoint(c) == 10) {
                tenCount++;
            }
        }
        return Math.min(4, Math.max(1, tenCount + 1));
    }
}
