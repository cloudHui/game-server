package com.cloud.hub.game.domain.mj.rule;

import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.mj.card.MjConst;
import com.cloud.hub.game.domain.mj.model.MjExposedSet;
import com.cloud.hub.game.domain.mj.model.MjTableContext;
import com.cloud.hub.game.domain.mj.model.MjWinResult;
import model.tablemodel.TableModel;

import java.util.List;

/**
 * 通用麻将计番与分摊结算基类。
 * <p>
 * 封装通用番型常量、核心牌型判定（碰碰胡、清一色、七对、龙七对）、附加番统计（杠开/抢杠/海底）及自摸得分分摊。
 * 各地方麻将玩法（荆门麻将、卡五星等）只需继承并覆写差异化规则。
 *
 * @author cloud
 */
public abstract class BaseMjScoring implements MjScoring {

    public static final int FAN_PING_HU = 1;          // 平胡
    public static final int FAN_PENG_PENG_HU = 2;      // 碰碰胡
    public static final int FAN_QING_YI_SE = 3;        // 清一色
    public static final int FAN_QI_DUI = 2;            // 七对
    public static final int FAN_LONG_QI_DUI = 4;       // 龙七对
    public static final int FAN_GANG_SHANG_HUA = 2;    // 杠上开花(额外)
    public static final int FAN_QIANG_GANG_HU = 2;     // 抢杠胡(额外)
    public static final int FAN_HAI_DI = 2;            // 海底捞月(额外)

    /**
     * 计算通用牌型基础番数（平胡、碰碰胡、清一色、七对/龙七对）
     */
    protected int calcCommonBaseFan(List<Card> handTiles, List<MjExposedSet> exposedSets) {
        int fan = FAN_PING_HU;

        if (isPengPengHu(exposedSets, handTiles)) {
            fan = Math.max(fan, FAN_PENG_PENG_HU);
        }

        if (isQingYiSe(handTiles, exposedSets)) {
            fan = Math.max(fan, FAN_QING_YI_SE);
        }

        if (exposedSets.isEmpty() && handTiles.size() == 14) {
            fan = Math.max(fan, isLongQiDui(handTiles) ? FAN_LONG_QI_DUI : FAN_QI_DUI);
        }

        return fan;
    }

    /**
     * 累加通用附加番（杠开、抢杠、海底）并执行封顶
     */
    protected int applyExtraFanAndCap(int fan, MjWinResult winResult, TableModel model) {
        if (winResult.isGangShangKaiHua()) {
            fan += FAN_GANG_SHANG_HUA;
        }
        if (winResult.isQiangGangHu()) {
            fan += FAN_QIANG_GANG_HU;
        }
        if (winResult.isHaiDi()) {
            fan += FAN_HAI_DI;
        }

        int maxFan = model != null ? model.getMaxFan() : 0;
        if (maxFan > 0 && fan > maxFan) {
            fan = maxFan;
        }
        return fan;
    }

    @Override
    public int[] settle(MjWinResult winResult, int fan, MjTableContext ctx, TableModel model, int seatNum) {
        int[] scores = new int[seatNum];
        int baseScore = model != null ? model.getBaseScore() : 1;
        int winScore = baseScore * fan;

        if (winResult.isZiMo()) {
            for (int i = 0; i < seatNum; i++) {
                if (i == winResult.getWinnerId()) {
                    scores[i] = winScore * (seatNum - 1);
                } else {
                    scores[i] = -winScore;
                }
            }
        }
        return scores;
    }

    public static boolean isPengPengHu(List<MjExposedSet> exposedSets, List<Card> handTiles) {
        for (MjExposedSet set : exposedSets) {
            if (set.getType() == MjExposedSet.Type.CHI) {
                return false;
            }
        }
        return handTiles.size() == 2 && handTiles.get(0).getId() == handTiles.get(1).getId();
    }

    public static boolean isQingYiSe(List<Card> handTiles, List<MjExposedSet> exposedSets) {
        int suit = -1;
        for (Card c : handTiles) {
            int s = MjConst.suitOf(c.getId());
            if (suit == -1) {
                suit = s;
            } else if (s != suit) {
                return false;
            }
        }
        for (MjExposedSet set : exposedSets) {
            for (int tileId : set.getTileIds()) {
                if (MjConst.suitOf(tileId) != suit) {
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isLongQiDui(List<Card> handTiles) {
        if (handTiles.size() != 14) {
            return false;
        }
        int[] sorted = handTiles.stream().mapToInt(Card::getId).sorted().toArray();
        for (int i = 0; i < 14; i += 2) {
            if (sorted[i] != sorted[i + 1]) {
                return false;
            }
        }
        for (int i = 0; i < 14; i += 2) {
            if (i + 3 < 14 && sorted[i] == sorted[i + 3]) {
                return true;
            }
        }
        return false;
    }
}
