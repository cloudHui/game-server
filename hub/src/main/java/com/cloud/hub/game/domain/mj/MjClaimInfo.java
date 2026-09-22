package com.cloud.hub.game.domain.mj;

import proto.ConstProto;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个座位的claim信息
 * 记录某个座位可以执行的操作(碰/杠/胡/吃/过)
 */
public class MjClaimInfo {

    private final int seat;
    private final boolean canHu;
    private final boolean canGang;
    private final boolean canPeng;
    private final boolean canChi;
    private final int claimTileId;  // 废弃牌ID(碰/吃/胡的目标牌)
    private final int gangTileId;   // 可杠的牌ID(0=不可杠)
    private final List<int[]> chiCombos; // 可吃的组合

    public MjClaimInfo(int seat, boolean canHu, boolean canGang, boolean canPeng, boolean canChi,
                       int claimTileId, int gangTileId, List<int[]> chiCombos) {
        this.seat = seat;
        this.canHu = canHu;
        this.canGang = canGang;
        this.canPeng = canPeng;
        this.canChi = canChi;
        this.claimTileId = claimTileId;
        this.gangTileId = gangTileId;
        this.chiCombos = chiCombos != null ? chiCombos : new ArrayList<>();
    }

    public int getSeat() {
        return seat;
    }

    public boolean isCanHu() {
        return canHu;
    }

    public boolean isCanGang() {
        return canGang;
    }

    public boolean isCanPeng() {
        return canPeng;
    }

    public boolean isCanChi() {
        return canChi;
    }

    public int getGangTileId() {
        return gangTileId;
    }

    public int getClaimTileId() {
        return claimTileId;
    }

    public List<int[]> getChiCombos() {
        return chiCombos;
    }

    /**
     * 返回本座位可执行的非吃操作列表（胡 &gt; 杠 &gt; 碰），顺序即下发优先级。
     *
     * <p>吃牌因需携带组合数据，由调用方单独处理；过牌由调用方统一追加。
     * 新增操作类型时只需在此处追加即可，sendClaimOptions 无需修改。
     */
    List<ConstProto.Operation> simpleOps() {
        List<ConstProto.Operation> ops = new ArrayList<>();
        if (canHu)   ops.add(ConstProto.Operation.MJ_HU);
        if (canGang) ops.add(ConstProto.Operation.MJ_GANG);
        if (canPeng) ops.add(ConstProto.Operation.MJ_PENG);
        return ops;
    }
}
