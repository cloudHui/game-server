package com.cloud.game.manager.table.mj.card;

import model.tablemodel.TableModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 按座位数/玩法编排牌墙：二人去整门万，三人万只留一九；卡五星沿用万条。
 */
public final class MjWallComposer {

    private MjWallComposer() {
    }

    /**
     * 生成一副未洗牌的牌墙 ID 列表。
     * <ul>
     *   <li>卡五星(subType=2)：仅万、条（既有规则，暂不扩展）</li>
     *   <li>二人：去掉整门万，留条/筒/风/箭</li>
     *   <li>三人：万只保留一万、九万，其余花色齐全</li>
     *   <li>四人：全花色 136 张</li>
     * </ul>
     */
    public static List<Integer> compose(TableModel model) {
        int subType = model != null ? model.getGameSubType() : 0;
        int seatNum = model != null ? model.getSeatNum() : 4;
        List<Integer> wall = new ArrayList<>();
        for (int suit = MjConst.SUIT_WAN; suit <= MjConst.SUIT_JIAN; suit++) {
            if (!includeSuit(subType, seatNum, suit)) continue;
            int maxVal = maxValueOf(suit);
            for (int val = 1; val <= maxVal; val++) {
                if (!includeValue(subType, seatNum, suit, val)) continue;
                int tileId = MjConst.encode(suit, val);
                for (int c = 0; c < MjConst.COPY_COUNT; c++) {
                    wall.add(tileId);
                }
            }
        }
        return wall;
    }

    /**
     * 期望牌墙张数（便于测试断言）
     */
    public static int expectedSize(TableModel model) {
        return compose(model).size();
    }

    static boolean includeSuit(int subType, int seatNum, int suit) {
        if (subType == 2) {
            // 卡五星：仅万、条
            return suit == MjConst.SUIT_WAN || suit == MjConst.SUIT_TIAO;
        }
        if (seatNum == 2) {
            // 二人：去掉整门万
            return suit != MjConst.SUIT_WAN;
        }
        return true;
    }

    static boolean includeValue(int subType, int seatNum, int suit, int val) {
        if (subType == 2) return true;
        // 三人：万只留一九
        if (seatNum == 3 && suit == MjConst.SUIT_WAN) {
            return val == 1 || val == 9;
        }
        return true;
    }

    private static int maxValueOf(int suit) {
        if (suit <= MjConst.SUIT_TONG) return MjConst.NUM_COUNT;
        if (suit == MjConst.SUIT_FENG) return MjConst.FENG_COUNT;
        return MjConst.JIAN_COUNT;
    }
}
