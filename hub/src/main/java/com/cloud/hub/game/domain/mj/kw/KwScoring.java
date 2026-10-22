package com.cloud.hub.game.domain.mj.kw;

import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.mj.BaseMjScoring;
import com.cloud.hub.game.domain.mj.MjExposedSet;
import com.cloud.hub.game.domain.mj.MjTableContext;
import com.cloud.hub.game.domain.mj.MjWinResult;
import model.tablemodel.TableModel;

import java.util.List;

/**
 * 卡五星计番。
 *
 * @author cloud
 */
public class KwScoring extends BaseMjScoring {

    public static final int FAN_KA_WU_XING = 2; // 卡五星额外番

    private final KwWinChecker winChecker;

    public KwScoring(KwWinChecker winChecker) {
        this.winChecker = winChecker;
    }

    @Override
    public int calcFan(MjWinResult winResult, MjTableContext ctx, TableModel model) {
        List<Card> handTiles = winResult.getHandTiles();
        List<MjExposedSet> exposedSets = winResult.getExposedSets();

        int fan = calcCommonBaseFan(handTiles, exposedSets);

        // 卡五星额外番检测
        if (winChecker != null && winChecker.isKaWuXing(handTiles, winResult.getWinTile())) {
            fan += FAN_KA_WU_XING;
        }

        return applyExtraFanAndCap(fan, winResult, model);
    }
}
