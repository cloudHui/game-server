package com.cloud.hub.game.domain.poker.niuniu;

import model.tablemodel.TableModel;

import java.util.EnumMap;
import java.util.Map;

/**
 * 牛牛规则与数值配置实体。
 *
 * @author cloud
 */
public class NiuniuConfig {

    public static final int DEFAULT_SEATS = 4;
    public static final int DEFAULT_MIN_PLAYERS = 2;

    private int seatNum = DEFAULT_SEATS;
    private int minStartPlayers = DEFAULT_MIN_PLAYERS;
    private boolean hasJoker = false;

    private boolean enableFiveSmall = true;
    private boolean enableBomb = true;
    private boolean enableFiveFlower = true;
    private boolean enableFourFlower = true;

    private int robTimeout = 5;
    private int betTimeout = 5;
    private int showTimeout = 8;

    private final Map<NiuniuCardType, Integer> multiplierOverrides = new EnumMap<>(NiuniuCardType.class);

    public static NiuniuConfig fromTableModel(TableModel model) {
        NiuniuConfig config = new NiuniuConfig();
        if (model != null && model.getSeatNum() >= 2 && model.getSeatNum() <= 6) {
            config.setSeatNum(model.getSeatNum());
        }
        return config;
    }

    public int getMultiplier(NiuniuCardType type) {
        if (type == null) {
            return 1;
        }
        Integer override = multiplierOverrides.get(type);
        return override != null ? override : type.getDefaultMultiplier();
    }

    public void setMultiplier(NiuniuCardType type, int multiplier) {
        multiplierOverrides.put(type, multiplier);
    }

    public int getSeatNum() {
        return seatNum;
    }

    public void setSeatNum(int seatNum) {
        this.seatNum = seatNum;
    }

    public int getMinStartPlayers() {
        return minStartPlayers;
    }

    public void setMinStartPlayers(int minStartPlayers) {
        this.minStartPlayers = minStartPlayers;
    }

    public boolean isHasJoker() {
        return hasJoker;
    }

    public void setHasJoker(boolean hasJoker) {
        this.hasJoker = hasJoker;
    }

    public boolean isEnableFiveSmall() {
        return enableFiveSmall;
    }

    public void setEnableFiveSmall(boolean enableFiveSmall) {
        this.enableFiveSmall = enableFiveSmall;
    }

    public boolean isEnableBomb() {
        return enableBomb;
    }

    public void setEnableBomb(boolean enableBomb) {
        this.enableBomb = enableBomb;
    }

    public boolean isEnableFiveFlower() {
        return enableFiveFlower;
    }

    public void setEnableFiveFlower(boolean enableFiveFlower) {
        this.enableFiveFlower = enableFiveFlower;
    }

    public boolean isEnableFourFlower() {
        return enableFourFlower;
    }

    public void setEnableFourFlower(boolean enableFourFlower) {
        this.enableFourFlower = enableFourFlower;
    }

    public int getRobTimeout() {
        return robTimeout;
    }

    public void setRobTimeout(int robTimeout) {
        this.robTimeout = robTimeout;
    }

    public int getBetTimeout() {
        return betTimeout;
    }

    public void setBetTimeout(int betTimeout) {
        this.betTimeout = betTimeout;
    }

    public int getShowTimeout() {
        return showTimeout;
    }

    public void setShowTimeout(int showTimeout) {
        this.showTimeout = showTimeout;
    }
}
