package com.cloud.game.manager.table.tractor;

import com.cloud.game.manager.table.card.CardConst;
import com.cloud.game.manager.table.cards.Card;
import proto.GameProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 拖拉机一桌运行时状态
 */
public class TractorTableContext {

    /**
     * 座位 0/2 与 1/3 两队各自级数（打几）
     */
    private int levelEven = CardConst.ER_VAL;
    private int levelOdd = CardConst.ER_VAL;
    private int trumpSuit = 0;
    private int bankerSeat = 0;

    private int defenderScore;
    private final List<Integer> revealedBottom = new ArrayList<>();
    private final List<Integer> buriedCards = new ArrayList<>();

    /**
     * 亮主：强度 / 花色(0无主) / 座位 / 连续过牌数
     */
    private int bidStrength;
    private int bidSuit;
    private int bidSeat = -1;
    private int bidPasses;

    /**
     * 逐张发牌：是否进行中 / 下一座位 / 已发手牌张数 / 上次发牌时刻
     */
    private boolean dealing;
    private int dealNextSeat;
    private int dealPlayerCount;
    private long lastDealTime;

    /**
     * 当前实际持有未扣底牌的座位；反主后会变，-1 表示无人持底
     */
    private int bottomHolderSeat = -1;
    /** 本局本轮是否已经完成庄家第一次扣底。 */
    private boolean firstBuryDone;

    private TractorRules.Combo leadCombo;
    private final List<List<Card>> trickPlays = new ArrayList<>();
    private final List<Integer> trickSeats = new ArrayList<>();
    private int trickLeader = -1;
    private GameProto.CardInfo lastPlayed = GameProto.CardInfo.getDefaultInstance();
    private int lastPlaySeat = -1;
    private int tricksDone;
    /** 本局最后完成的一墩赢家；用于闲家获胜后的实际轮庄。 */
    private int roundWinnerSeat = -1;

    /**
     * 当前庄家方级数
     */
    public int getLevelRank() {
        return (bankerSeat % 2 == 0) ? levelEven : levelOdd;
    }

    public void upgradeBankerTeam(int levels) {
        for (int i = 0; i < levels; i++) {
            if (bankerSeat % 2 == 0) levelEven = TractorRules.nextLevelRank(levelEven);
            else levelOdd = TractorRules.nextLevelRank(levelOdd);
        }
    }

    public void upgradeSeatTeam(int seat, int levels) {
        for (int i = 0; i < levels; i++) {
            if (seat % 2 == 0) levelEven = TractorRules.nextLevelRank(levelEven);
            else levelOdd = TractorRules.nextLevelRank(levelOdd);
        }
    }

    public int getTrumpSuit() {
        return trumpSuit;
    }

    public void setTrumpSuit(int trumpSuit) {
        this.trumpSuit = trumpSuit;
    }

    public int getBankerSeat() {
        return bankerSeat;
    }

    public void setBankerSeat(int bankerSeat) {
        this.bankerSeat = bankerSeat;
    }

    public boolean isBankerTeam(int seat) {
        return seat == bankerSeat || seat == (bankerSeat + 2) % 4;
    }

    public int getDefenderScore() {
        return defenderScore;
    }

    public void addDefenderScore(int s) {
        defenderScore += s;
    }

    public List<Integer> getRevealedBottom() {
        return Collections.unmodifiableList(revealedBottom);
    }

    public void setRevealedBottom(List<Integer> ids) {
        revealedBottom.clear();
        if (ids != null) revealedBottom.addAll(ids);
    }

    public List<Integer> getBuriedCards() {
        return Collections.unmodifiableList(buriedCards);
    }

    public void setBuriedCards(List<Integer> ids) {
        buriedCards.clear();
        if (ids != null) buriedCards.addAll(ids);
    }

    public int getBidStrength() {
        return bidStrength;
    }

    public void setBidStrength(int bidStrength) {
        this.bidStrength = bidStrength;
    }

    public int getBidSuit() {
        return bidSuit;
    }

    public void setBidSuit(int bidSuit) {
        this.bidSuit = bidSuit;
    }

    public int getBidSeat() {
        return bidSeat;
    }

    public void setBidSeat(int bidSeat) {
        this.bidSeat = bidSeat;
    }

    public int getBidPasses() {
        return bidPasses;
    }

    public void incBidPasses() {
        bidPasses++;
    }

    public void resetBidPasses() {
        bidPasses = 0;
    }

    public boolean isDealing() {
        return dealing;
    }

    public void setDealing(boolean dealing) {
        this.dealing = dealing;
    }

    public int getDealNextSeat() {
        return dealNextSeat;
    }

    public void setDealNextSeat(int dealNextSeat) {
        this.dealNextSeat = dealNextSeat;
    }

    public int getDealPlayerCount() {
        return dealPlayerCount;
    }

    public void setDealPlayerCount(int dealPlayerCount) {
        this.dealPlayerCount = dealPlayerCount;
    }

    public long getLastDealTime() {
        return lastDealTime;
    }

    public void setLastDealTime(long lastDealTime) {
        this.lastDealTime = lastDealTime;
    }

    public int getBottomHolderSeat() {
        return bottomHolderSeat;
    }

    public void setBottomHolderSeat(int bottomHolderSeat) {
        this.bottomHolderSeat = bottomHolderSeat;
    }

    public boolean isFirstBuryDone() {
        return firstBuryDone;
    }

    public void setFirstBuryDone(boolean firstBuryDone) {
        this.firstBuryDone = firstBuryDone;
    }

    public TractorRules.Combo getLeadCombo() {
        return leadCombo;
    }

    public void setLeadCombo(TractorRules.Combo leadCombo) {
        this.leadCombo = leadCombo;
    }

    public List<List<Card>> getTrickPlays() {
        return trickPlays;
    }

    public List<Integer> getTrickSeats() {
        return trickSeats;
    }

    public int getTrickLeader() {
        return trickLeader;
    }

    public void setTrickLeader(int trickLeader) {
        this.trickLeader = trickLeader;
    }

    public GameProto.CardInfo getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(GameProto.CardInfo lastPlayed) {
        this.lastPlayed = lastPlayed != null ? lastPlayed : GameProto.CardInfo.getDefaultInstance();
    }

    public int getLastPlaySeat() {
        return lastPlaySeat;
    }

    public void setLastPlaySeat(int lastPlaySeat) {
        this.lastPlaySeat = lastPlaySeat;
    }

    public int getTricksDone() {
        return tricksDone;
    }

    public void incTricksDone() {
        tricksDone++;
    }

    public int getRoundWinnerSeat() {
        return roundWinnerSeat;
    }

    public void setRoundWinnerSeat(int roundWinnerSeat) {
        this.roundWinnerSeat = roundWinnerSeat;
    }

    public void resetTrick() {
        leadCombo = null;
        trickPlays.clear();
        trickSeats.clear();
    }

    public void resetRoundKeepLevel() {
        defenderScore = 0;
        revealedBottom.clear();
        buriedCards.clear();
        lastPlaySeat = -1;
        lastPlayed = GameProto.CardInfo.getDefaultInstance();
        tricksDone = 0;
        roundWinnerSeat = -1;
        bidStrength = 0;
        bidSuit = 0;
        bidSeat = -1;
        bidPasses = 0;
        trumpSuit = 0;
        bottomHolderSeat = -1;
        firstBuryDone = false;
        dealing = false;
        dealNextSeat = 0;
        dealPlayerCount = 0;
        lastDealTime = 0;
        resetTrick();
    }

    public void fullReset() {
        levelEven = CardConst.ER_VAL;
        levelOdd = CardConst.ER_VAL;
        bankerSeat = 0;
        resetRoundKeepLevel();
    }
}
