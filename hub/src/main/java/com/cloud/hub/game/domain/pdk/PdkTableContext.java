package com.cloud.hub.game.domain.pdk;

import com.cloud.hub.game.domain.ddz.DdzHand;
import com.cloud.hub.game.domain.ddz.ai.AiVision;
import proto.GameProto;

import java.util.HashSet;
import java.util.Set;

/**
 * 跑得快单桌运行时状态上下文。
 * <p>
 * 维护跑得快牌桌的轮次状态，包括上一手牌、连续不出次数、首出座位、已过牌座位以及头游/二游排名。
 *
 * @author cloud
 */
public class PdkTableContext {

    /** 桌面上一手出牌的 Protobuf 信息 */
    private GameProto.CardInfo lastPlayed = GameProto.CardInfo.getDefaultInstance();
    /** 桌面上一手出牌的规则分析结果 */
    private DdzHand lastHand;
    /** 当前圈内连续不出/过牌的次数 */
    private int consecutivePasses;
    /** 最后一手最大出牌者的座位编号 */
    private int lastPlaySeat = -1;
    /** 本轮已过牌座位集合（用于断线重连向客户端展示「不要」） */
    private final Set<Integer> passSeats = new HashSet<>();
    /** 首出座位编号（持方块3或上局头游玩家） */
    private int firstSeat = -1;
    /** 各座位是否出过牌记录（用于全关/关牌判定） */
    private final Set<Integer> playedSeats = new HashSet<>();
    /** 出完牌的名次计数器（0 为头游，1 为二游） */
    private int finishOrder = 0;
    /** 各座位的出完手牌名次排名 */
    private final int[] finishRanks = new int[] { -1, -1, -1 };
    /** AI 智能等级，默认大师档（{@link AiVision#AI_MASTER}） */
    private int aiLevel = AiVision.AI_MASTER;

    public GameProto.CardInfo getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(GameProto.CardInfo lastPlayed) {
        this.lastPlayed = lastPlayed != null ? lastPlayed : GameProto.CardInfo.getDefaultInstance();
    }

    public DdzHand getLastHand() {
        return lastHand;
    }

    public void setLastHand(DdzHand lastHand) {
        this.lastHand = lastHand;
    }

    public int getConsecutivePasses() {
        return consecutivePasses;
    }

    public void addPass() {
        consecutivePasses++;
    }

    public void setConsecutivePasses(int n) {
        this.consecutivePasses = n;
    }

    public void addPassSeat(int seat) {
        passSeats.add(seat);
    }

    public Set<Integer> getPassSeats() {
        return passSeats;
    }

    public int getLastPlaySeat() {
        return lastPlaySeat;
    }

    public void setLastPlaySeat(int lastPlaySeat) {
        this.lastPlaySeat = lastPlaySeat;
    }

    public int getFirstSeat() {
        return firstSeat;
    }

    public void setFirstSeat(int firstSeat) {
        this.firstSeat = firstSeat;
    }

    public void markPlayed(int seat) {
        playedSeats.add(seat);
    }

    public boolean hasPlayed(int seat) {
        return playedSeats.contains(seat);
    }

    /**
     * 记录指定座位玩家完成手牌出完的名次。
     *
     * @param seat 获胜出完手牌的座位
     */
    public void recordFinish(int seat) {
        if (finishRanks[seat] >= 0) {
            return;
        }
        finishRanks[seat] = finishOrder++;
    }

    public int getFinishRank(int seat) {
        return finishRanks[seat];
    }

    /**
     * 重置当前一圈对决的出牌状态（全员过牌后由最大方领出新一手）。
     */
    public void resetCurrentTrick() {
        lastPlayed = GameProto.CardInfo.getDefaultInstance();
        lastHand = null;
        consecutivePasses = 0;
        passSeats.clear();
    }

    /**
     * 重置整局对局上下文。
     */
    public void resetRound() {
        lastPlaySeat = -1;
        playedSeats.clear();
        finishOrder = 0;
        for (int i = 0; i < finishRanks.length; i++) {
            finishRanks[i] = -1;
        }
        resetCurrentTrick();
    }

    public int getAiLevel() {
        return aiLevel;
    }

    public void setAiLevel(int aiLevel) {
        this.aiLevel = Math.max(AiVision.AI_DUMB, Math.min(AiVision.AI_MASTER, aiLevel));
    }
}

