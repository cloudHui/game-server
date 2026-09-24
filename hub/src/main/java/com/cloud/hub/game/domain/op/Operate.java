package com.cloud.hub.game.domain.op;

import com.cloud.hub.game.domain.table.Table;
import proto.GameProto;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 牌桌操作流转管理器。
 * <p>
 * 跟踪当前出牌/操作座位号（currOpSeat）、上一操作座位号（lastOpSeat），
 * 并记录各座位当前可用的操作选项集合（吃碰杠胡、出牌提示等）。
 *
 * @author cloud
 */
public class Operate {

    /**
     * 当前操作位置
     */
    private int currOpSeat = -1;

    /**
     * 上一个操作位置
     */
    private int lastOpSeat;

    /**
     * 位置操作信息 是不是麻将才用
     */
    private final Map<Integer, Set<GameProto.OpInfo>> posOp = new HashMap<>();

    private final Table table;

    public Operate(Table table) {
        this.table = table;
    }

    public int getCurrOpSeat() {
        return currOpSeat;
    }

    public void setCurrOpSeat(int currOpSeat) {
        this.currOpSeat = currOpSeat;
    }

    public int getLastOpSeat() {
        return lastOpSeat;
    }

    public void setLastOpSeat(int lastOpSeat) {
        this.lastOpSeat = lastOpSeat;
    }

    public void addPosOpInfo(int pos, GameProto.OpInfo op) {
        posOp.computeIfAbsent(pos, k -> new HashSet<>()).add(op);
    }

    public Set<GameProto.OpInfo> getSeatOps(int seat) {
        return posOp.get(seat);
    }

    /** 清除单个座位的临时操作，不影响同时操作的其他玩家。 */
    public void clearSeatOps(int seat) {
        posOp.remove(seat);
    }

    public Set<GameProto.OpInfo> getCurrChoice() {
        return posOp.get(currOpSeat);
    }

    /**
     * 移动到下一个玩家操作
     */
    public void moveToNextOp() {
        lastOpSeat = currOpSeat;
        if (++currOpSeat >= table.getTableModel().getSeatNum()) {
            currOpSeat = 0;
        }
        posOp.clear();
    }

    /**
     * 重置
     */
    public void reset() {
        currOpSeat = -1;
        lastOpSeat = -1;
        posOp.clear();
    }

    public void clearChoiceMap() {
        posOp.clear();
    }
}
