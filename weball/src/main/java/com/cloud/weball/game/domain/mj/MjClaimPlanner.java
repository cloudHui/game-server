package com.cloud.weball.game.domain.mj;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 吃碰杠胡优先级与座位顺序（纯逻辑，便于单测）。
 * 优先级：胡 &gt; 杠 &gt; 碰 &gt; 吃；同级按逆时针离出牌者最近优先。
 */
public final class MjClaimPlanner {

    private MjClaimPlanner() {
    }

    /**
     * 完整响应优先级：胡4 &gt; 杠3 &gt; 碰2 &gt; 吃1。
     */
    public static int priority(MjClaimInfo info) {
        if (info == null) return 0;
        if (info.isCanHu()) return 4;
        return sidePriority(info);
    }

    /**
     * 副露类优先级：杠3 &gt; 碰2 &gt; 吃1
     */
    public static int sidePriority(MjClaimInfo info) {
        if (info == null) return 0;
        if (info.isCanGang()) return 3;
        if (info.isCanPeng()) return 2;
        if (info.isCanChi()) return 1;
        return 0;
    }

    /**
     * 出牌者到目标座位的逆时针距离（1=下家）
     */
    public static int ccwDistance(int fromSeat, int seat, int seatNum) {
        if (seatNum <= 0) return 0;
        return (seat - fromSeat + seatNum) % seatNum;
    }

    /**
     * 按逆时针顺序收集可胡座位
     */
    public static List<Integer> huSeatsInOrder(List<MjClaimInfo> claims) {
        List<Integer> seats = new ArrayList<>();
        for (MjClaimInfo c : claims) {
            if (c != null && c.isCanHu()) seats.add(c.getSeat());
        }
        return seats;
    }

    /**
     * 副露候选：有杠/碰/吃者，按优先级降序，同级按逆时针距离升序。
     */
    public static List<MjClaimInfo> sideCandidates(Map<Integer, MjClaimInfo> bySeat, int fromSeat, int seatNum) {
        List<MjClaimInfo> list = new ArrayList<>();
        for (MjClaimInfo c : bySeat.values()) {
            if (c != null && sidePriority(c) > 0) list.add(c);
        }
        list.sort(Comparator
                .comparingInt(MjClaimPlanner::sidePriority).reversed()
                .thenComparingInt(c -> ccwDistance(fromSeat, c.getSeat(), seatNum)));
        return list;
    }

    /**
     * 胡阶段仅保留胡能力（过由下发选项提供）
     */
    public static MjClaimInfo huOnly(MjClaimInfo src) {
        if (src == null || !src.isCanHu()) return null;
        return new MjClaimInfo(src.getSeat(), true, false, false, false,
                src.getClaimTileId(), 0, null);
    }

    /**
     * 副露阶段去掉胡，保留杠碰吃
     */
    public static MjClaimInfo sideOnly(MjClaimInfo src) {
        if (src == null || sidePriority(src) <= 0) return null;
        return new MjClaimInfo(src.getSeat(), false, src.isCanGang(), src.isCanPeng(), src.isCanChi(),
                src.getClaimTileId(), src.getGangTileId(), src.getChiCombos());
    }
}
