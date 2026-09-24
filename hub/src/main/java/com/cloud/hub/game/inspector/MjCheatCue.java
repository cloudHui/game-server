package com.cloud.hub.game.inspector;

import com.cloud.hub.game.domain.cards.CardFormatter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 麻将待执行换牌/控牌指令模型。
 * <p>
 * 支持将换牌指令精确绑定到指定玩家或全局下一轮摸牌，
 * 严格基于牌墙现有余牌生效，不修改手牌且保证牌数绝对守恒。
 * </p>
 *
 * @author cloud
 */
public class MjCheatCue {

    /**
     * 目标座位：-1 表示全局下一个摸牌玩家；0~3 表示指定座位玩家
     */
    private final int targetSeat;

    /**
     * 目标牌 ID（必须来自当前未摸牌墙）
     */
    private final int targetTileId;

    /**
     * 是否为杠牌后的补摸（true=杠牌摸牌，false=普通摸牌）
     */
    private final boolean gang;

    /**
     * 创建时间戳
     */
    private final long createTime;

    /**
     * 状态：PENDING（等待摸牌生效），COMPLETED（已成功摸走），EXHAUSTED（牌墙已无此牌作废）
     */
    private volatile String status = "PENDING";

    public MjCheatCue(int targetSeat, int targetTileId, boolean gang) {
        this.targetSeat = targetSeat;
        this.targetTileId = targetTileId;
        this.gang = gang;
        this.createTime = System.currentTimeMillis();
    }

    public int getTargetSeat() {
        return targetSeat;
    }

    public int getTargetTileId() {
        return targetTileId;
    }

    public boolean isGang() {
        return gang;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public Map<String, Object> toMap(int remainingCountInWall) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("targetSeat", targetSeat);
        map.put("targetSeatDesc", targetSeat < 0 ? "全局下一个人" : ("座位 " + targetSeat));
        map.put("targetTileId", targetTileId);
        map.put("targetTileName", CardFormatter.formatMahjong(targetTileId));
        map.put("isGang", gang);
        map.put("actionDesc", gang ? "杠牌补摸" : "普通摸牌");
        map.put("status", status);
        map.put("remainingInWall", remainingCountInWall);
        map.put("createTime", createTime);
        return map;
    }
}
