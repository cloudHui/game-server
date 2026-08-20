package com.cloud.game.manager.table.mj.ai;

import com.cloud.game.manager.table.ai.AiSearchBudget;
import com.cloud.game.manager.table.cards.Card;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 麻将大师档的限时下一摸期望评估。
 */
final class MjMasterAi {
    private MjMasterAi() {
    }

    static int decideDiscard(List<Card> hand, MjVision vision, AiSearchBudget budget) {
        int fallback = MjSimpleAi.decideByEfficiency(hand, vision);
        int[] counts = MjSimpleAi.handToCounts(hand);
        Map<Integer, Integer> seen = new HashMap<>();
        int bestTile = fallback;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Card discard : hand) {
            int tileId = discard.getId();
            if (seen.put(tileId, tileId) != null || tileId == vision.getLaiZiTileId()) continue;
            if (budget.tryVisit()) break;
            counts[tileId]--;
            double score = expectedDrawScore(counts, vision, budget);
            counts[tileId]++;
            if (score < bestScore) {
                bestScore = score;
                bestTile = tileId;
            }
        }
        return bestTile;
    }

    private static double expectedDrawScore(int[] counts, MjVision vision, AiSearchBudget budget) {
        double weighted = 0;
        int total = 0;
        for (int tileId : MjSimpleAi.allTileTypes()) {
            int remaining = vision.getPublicRemainingCount(tileId);
            if (remaining <= 0) continue;
            if (budget.tryVisit()) break;
            counts[tileId]++;
            int shanten = MjSimpleAi.calcShanten(counts);
            int efficiency = MjSimpleAi.countEffectiveDraws(counts, vision.getLaiZiTileId());
            counts[tileId]--;
            weighted += remaining * (shanten * 100.0 - efficiency);
            total += remaining;
        }
        return total == 0 ? MjSimpleAi.calcShanten(counts) * 100.0 : weighted / total;
    }
}
