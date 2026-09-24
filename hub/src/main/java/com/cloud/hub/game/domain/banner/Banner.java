package com.cloud.hub.game.domain.banner;

import java.util.HashSet;
import java.util.Set;

/**
 * 斗地主叫地主、抢地主与叫分阶段的状态上下文。
 * <p>
 * 负责记录本局首叫者锚点、当前候选地主、加倍倍率以及放弃抢庄的玩家集合。
 *
 * @author cloud
 * @version 1.0
 * @since 1.0
 */
public class Banner {

    /** 首个被分配去叫地主的座位（连局为上局赢家，非连局为随机产生） */
    private int firstRandomRobSeat = -1;
    /** 叫抢操作广播是否已完成（避免一轮中重复向客户端发送 NOT_OP） */
    private boolean robBroadcastDone;
    /** 是否处于抢地主阶段（true 表示已产生首叫者进入抢地主，false 表示仍在首轮叫地主/叫分阶段） */
    private boolean robPhase;
    /** 当前全场最大叫分（叫分模式 1/2/3 使用） */
    private int maxCallScore;
    /** 当前地主候选人座位（最终胜出当选地主的人选） */
    private int candidateSeat = -1;
    /** 叫分阶段已响应人数统计 */
    private int bidResponses;
    /** 本轮已被叫过的分值集合（叫分模式不可叫相同分数） */
    private final Set<Integer> calledScores = new HashSet<>();
    /** 抢地主累积倍数（初始为 1，每次玩家选择「抢地主」则翻倍 ×2） */
    private int robMultiplierAccum = 1;
    /** 首个选择「叫地主」的玩家座位（终局裁决锚点，-1 表示尚未有人叫地主） */
    private int firstCallerSeat = -1;
    /** 在首叫之后是否已有其他玩家执行过「抢地主」 */
    private boolean hasRobbed;
    /** 已选择「不叫」或「不抢」而永久放弃资格的座位集合（后续轮转直接跳过） */
    private final Set<Integer> givenUpSeats = new HashSet<>();

    public Banner() {
    }

    /**
     * 获取首个主动叫地主的玩家座位。
     *
     * @return 首叫者座位，-1 表示尚无玩家叫地主
     */
    public int getFirstCallerSeat() {
        return firstCallerSeat;
    }

    /**
     * 设置首个主动叫地主的玩家座位。
     *
     * @param firstCallerSeat 首叫者座位
     */
    public void setFirstCallerSeat(int firstCallerSeat) {
        this.firstCallerSeat = firstCallerSeat;
    }

    /**
     * 是否已经产生了首叫地主的玩家。
     *
     * @return true 表示已有玩家叫地主，进入抢地主阶段；false 表示尚未有人叫地主
     */
    public boolean hasCaller() {
        return firstCallerSeat >= 0;
    }

    /**
     * 检查在首叫后是否已有玩家执行过抢地主。
     *
     * @return true 表示有人抢过地主；false 表示除首叫外无人抢
     */
    public boolean isHasRobbed() {
        return hasRobbed;
    }

    /**
     * 设置是否有人执行过抢地主标记。
     *
     * @param hasRobbed 是否有人抢过地主
     */
    public void setHasRobbed(boolean hasRobbed) {
        this.hasRobbed = hasRobbed;
    }

    /**
     * 判断指定座位的玩家是否已经放弃本局当庄资格（选择过不叫或不抢）。
     *
     * @param seat 座位编号
     * @return true 表示已放弃；false 表示仍具抢庄资格
     */
    public boolean isGivenUp(int seat) {
        return givenUpSeats.contains(seat);
    }

    /**
     * 标记指定座位的玩家放弃抢庄资格。
     *
     * @param seat 座位编号
     */
    public void addGivenUpSeat(int seat) {
        if (seat >= 0) {
            givenUpSeats.add(seat);
        }
    }

    /**
     * 获取已放弃抢庄资格的玩家总人数。
     *
     * @return 弃权人数
     */
    public int getGivenUpCount() {
        return givenUpSeats.size();
    }

    /**
     * 获取已放弃抢庄资格的座位集合。
     *
     * @return 弃权座位只读或内部集合
     */
    public Set<Integer> getGivenUpSeats() {
        return givenUpSeats;
    }

    /**
     * 获取初始被指定为首个询问叫地主的座位编号。
     *
     * @return 首个操作座位
     */
    public int getFirstRandomRobSeat() {
        return firstRandomRobSeat;
    }

    /**
     * 设置初始被指定为首个询问叫地主的座位编号。
     *
     * @param firstRandomRobSeat 首个操作座位
     */
    public void setFirstRandomRobSeat(int firstRandomRobSeat) {
        this.firstRandomRobSeat = firstRandomRobSeat;
    }

    /**
     * 检查当前轮次的操作广播是否已发送。
     *
     * @return true 表示已广播
     */
    public boolean isRobBroadcastDone() {
        return robBroadcastDone;
    }

    /**
     * 设置当前轮次操作广播状态。
     *
     * @param robBroadcastDone 广播完成状态
     */
    public void setRobBroadcastDone(boolean robBroadcastDone) {
        this.robBroadcastDone = robBroadcastDone;
    }

    /**
     * 是否处于抢地主阶段。
     *
     * @return true 为抢地主阶段
     */
    public boolean isRobPhase() {
        return robPhase;
    }

    /**
     * 设置抢地主阶段状态。
     *
     * @param robPhase 阶段状态
     */
    public void setRobPhase(boolean robPhase) {
        this.robPhase = robPhase;
    }

    /**
     * 获取当前叫分模式下的最高叫分。
     *
     * @return 最高叫分 (1/2/3)
     */
    public int getMaxCallScore() {
        return maxCallScore;
    }

    /**
     * 设置当前叫分模式下的最高叫分。
     *
     * @param maxCallScore 最高叫分
     */
    public void setMaxCallScore(int maxCallScore) {
        this.maxCallScore = maxCallScore;
    }

    /**
     * 获取当前地主候选人座位。
     *
     * @return 当前地主候选人座位
     */
    public int getCandidateSeat() {
        return candidateSeat;
    }

    /**
     * 更新当前地主候选人座位。
     *
     * @param candidateSeat 新候选人座位
     */
    public void setCandidateSeat(int candidateSeat) {
        this.candidateSeat = candidateSeat;
    }

    /**
     * 获取叫分阶段已响应人数。
     *
     * @return 已响应玩家数
     */
    public int getBidResponses() {
        return bidResponses;
    }

    /**
     * 设置叫分阶段已响应人数。
     *
     * @param bidResponses 已响应玩家数
     */
    public void setBidResponses(int bidResponses) {
        this.bidResponses = bidResponses;
    }

    /**
     * 叫分阶段响应人数累加 1。
     */
    public void addBidResponse() {
        this.bidResponses++;
    }

    /**
     * 检查指定分值是否已被其他玩家叫过。
     *
     * @param score 分值
     * @return true 表示已被叫过
     */
    public boolean hasCalledScore(int score) {
        return calledScores.contains(score);
    }

    /**
     * 记录已被玩家叫出的分值。
     *
     * @param score 分值
     */
    public void addCalledScore(int score) {
        if (score > 0) {
            calledScores.add(score);
        }
    }

    /**
     * 判定指定分值在当前叫分规则下是否可选。
     *
     * @param score 待验证分值
     * @return true 表示高于当前最大分且未被叫过
     */
    public boolean isScoreAvailable(int score) {
        return score > maxCallScore && !calledScores.contains(score);
    }

    /**
     * 获取抢地主累积倍数。
     *
     * @return 抢地主当前倍率（1, 2, 4, 8...）
     */
    public int getRobMultiplierAccum() {
        return robMultiplierAccum;
    }

    /**
     * 设置抢地主累积倍数。
     *
     * @param robMultiplierAccum 新倍率
     */
    public void setRobMultiplierAccum(int robMultiplierAccum) {
        this.robMultiplierAccum = robMultiplierAccum;
    }

    /**
     * 重置叫地主、抢地主与叫分阶段的所有状态数据，供新一局开局使用。
     */
    public void reset() {
        firstRandomRobSeat = -1;
        firstCallerSeat = -1;
        hasRobbed = false;
        givenUpSeats.clear();
        robBroadcastDone = false;
        robPhase = false;
        maxCallScore = 0;
        candidateSeat = -1;
        bidResponses = 0;
        calledScores.clear();
        robMultiplierAccum = 1;
    }
}
