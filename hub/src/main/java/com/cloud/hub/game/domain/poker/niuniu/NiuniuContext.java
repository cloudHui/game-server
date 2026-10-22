package com.cloud.hub.game.domain.poker.niuniu;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 牛牛单局运行时上下文。
 *
 * @author cloud
 */
public class NiuniuContext {

    public static final int STAGE_WAITING = 0;
    public static final int STAGE_ROB = 1;
    public static final int STAGE_BET = 2;
    public static final int STAGE_SHOW = 3;
    public static final int STAGE_SETTLE = 4;

    private volatile int stage = STAGE_WAITING;
    private volatile int bankerSeat = -1;
    private volatile int bankerMultiplier = 1;

    private final Map<Integer, Integer> robMultipliers = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> betMultipliers = new ConcurrentHashMap<>();
    private final Map<Integer, NiuniuHand> playerHands = new ConcurrentHashMap<>();
    private final Set<Integer> showedSeats = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Integer> roundScoreDeltas = new ConcurrentHashMap<>();

    public void reset() {
        this.stage = STAGE_WAITING;
        this.bankerSeat = -1;
        this.bankerMultiplier = 1;
        this.robMultipliers.clear();
        this.betMultipliers.clear();
        this.playerHands.clear();
        this.showedSeats.clear();
        this.roundScoreDeltas.clear();
    }

    public int getStage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = stage;
    }

    public int getBankerSeat() {
        return bankerSeat;
    }

    public void setBankerSeat(int bankerSeat) {
        this.bankerSeat = bankerSeat;
    }

    public int getBankerMultiplier() {
        return bankerMultiplier;
    }

    public void setBankerMultiplier(int bankerMultiplier) {
        this.bankerMultiplier = bankerMultiplier;
    }

    public Map<Integer, Integer> getRobMultipliers() {
        return robMultipliers;
    }

    public Map<Integer, Integer> getBetMultipliers() {
        return betMultipliers;
    }

    public Map<Integer, NiuniuHand> getPlayerHands() {
        return playerHands;
    }

    public Set<Integer> getShowedSeats() {
        return showedSeats;
    }

    public Map<Integer, Integer> getRoundScoreDeltas() {
        return roundScoreDeltas;
    }
}
