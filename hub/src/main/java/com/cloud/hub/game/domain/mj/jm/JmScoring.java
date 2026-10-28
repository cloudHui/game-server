package com.cloud.hub.game.domain.mj.jm;

import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.mj.model.MjExposedSet;
import com.cloud.hub.game.domain.mj.model.MjTableContext;
import com.cloud.hub.game.domain.mj.model.MjWinResult;
import com.cloud.hub.game.domain.mj.rule.BaseMjScoring;
import model.tablemodel.TableModel;

import java.util.List;

/**
 * 荆门麻将计番与包赔结算。
 *
 * @author cloud
 */
public class JmScoring extends BaseMjScoring {

    @Override
    public int calcFan(MjWinResult winResult, MjTableContext ctx, TableModel model) {
        List<Card> handTiles = winResult.getHandTiles();
        List<MjExposedSet> exposedSets = winResult.getExposedSets();

        int fan = calcCommonBaseFan(handTiles, exposedSets);
        return applyExtraFanAndCap(fan, winResult, model);
    }

    @Override
    public int[] settle(MjWinResult winResult, int fan, MjTableContext ctx, TableModel model, int seatNum) {
        if (winResult.isDianPao()) {
            // 点炮: 放炮者包赔(荆门规则)
            int[] scores = new int[seatNum];
            int baseScore = model != null ? model.getBaseScore() : 1;
            int winScore = baseScore * fan;
            int dianPaoSeat = winResult.getDianPaoSeat();

            for (int i = 0; i < seatNum; i++) {
                if (i == winResult.getWinnerId()) {
                    scores[i] = winScore * (seatNum - 1);
                } else if (i == dianPaoSeat) {
                    scores[i] = -winScore * (seatNum - 1);
                }
            }
            return scores;
        }
        return super.settle(winResult, fan, ctx, model, seatNum);
    }
}
