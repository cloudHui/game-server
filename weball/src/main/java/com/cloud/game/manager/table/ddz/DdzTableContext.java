package com.cloud.game.manager.table.ddz;

import com.cloud.game.manager.table.cards.Card;
import com.cloud.game.manager.table.ddz.ai.AiVision;
import proto.GameProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 斗地主一桌内的运行时状态（与 {@link msg.registor.enums.TableState} 区分）。
 *
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @className DdzTableContext
 * @description 斗地主一桌内的运行时状态（与 {@link msg.registor.enums.TableState} 区分）。
 * @createDate 2026-05-03
 * @since 1.0
 */
public class DdzTableContext {

    private GameProto.CardInfo lastPlayed = GameProto.CardInfo.getDefaultInstance();
    private DdzHand lastHand;//上一手出牌
    private int consecutivePasses;//连续过牌次数
    private int landlordSeat = -1;
    /**
     * 上一手出牌者座位（用于两轮「要不起」后仍由其首家）
     */
    private int lastPlaySeat = -1;//上一手出牌者座位

    /**
     * 叫分底分 1-3
     */
    private int baseScore = 1;//叫分底分
    /**
     * 抢地主倍数（每抢×2 后的积）
     */
    private int robMultiplier = 1;//抢地主倍数
    /**
     * 炸弹/火箭累计倍数。
     */
    private int bombMultiplier = 1;
    private boolean farmerEverPlayed;//农民是否出过牌
    private int landlordPlayCount;//地主出牌次数
    /**
     * 记牌器：本局所有已出牌的 cardId 集合
     */
    private final Set<Integer> playedCardIds = new HashSet<>();
    /**
     * AI 视野等级：0=正常, 1=半透视(知剩余牌池), 2=全透视(知他人手牌)。支持运行时修改
     */
    private int visionLevel = AiVision.LEVEL_NORMAL;
    /**
     * AI 智能等级：0=最笨，1=基础，2=高级，3=大师。支持运行时修改
     */
    private int aiLevel = AiVision.AI_MASTER;
    /**
     * 已亮出的底牌（定地主后桌面顶部展示，roleId=0 附在 NotCard 末尾）
     */
    private final List<Integer> revealedBottomCards = new ArrayList<>();

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

    public void setConsecutivePasses(int consecutivePasses) {
        this.consecutivePasses = consecutivePasses;
    }

    public void addPass() {
        consecutivePasses++;
    }

    public int getLandlordSeat() {
        return landlordSeat;
    }

    public void setLandlordSeat(int landlordSeat) {
        this.landlordSeat = landlordSeat;
    }

    public int getLastPlaySeat() {
        return lastPlaySeat;
    }

    public void setLastPlaySeat(int lastPlaySeat) {
        this.lastPlaySeat = lastPlaySeat;
    }

    public int getBaseScore() {
        return baseScore;
    }

    public void setBaseScore(int baseScore) {
        this.baseScore = Math.max(1, baseScore);
    }

    public int getRobMultiplier() {
        return robMultiplier;
    }

    public void setRobMultiplier(int robMultiplier) {
        this.robMultiplier = Math.max(1, robMultiplier);
    }

    public int getBombMultiplier() {
        return bombMultiplier;
    }

    public void doubleBombMultiplier() {
        bombMultiplier *= 2;
    }

    public int getCurrentMultiplier() {
        return Math.max(1, baseScore) * Math.max(1, robMultiplier) * Math.max(1, bombMultiplier);
    }

    public boolean isFarmerEverPlayed() {
        return farmerEverPlayed;
    }

    public void setFarmerEverPlayed(boolean farmerEverPlayed) {
        this.farmerEverPlayed = farmerEverPlayed;
    }

    public int getLandlordPlayCount() {
        return landlordPlayCount;
    }

    public void incrementLandlordPlayCount() {
        this.landlordPlayCount++;
    }

    public void setLandlordPlayCount(int landlordPlayCount) {
        this.landlordPlayCount = landlordPlayCount;
    }

    /**
     * 记录一批已出牌的 cardId
     */
    public void recordPlayedCards(List<Card> cards) {
        for (Card c : cards) {
            playedCardIds.add(c.getId());
        }
    }

    /**
     * 获取所有已出牌 ID 集合（只读）
     */
    public Set<Integer> getPlayedCardIds() {
        return playedCardIds;
    }

    public int getVisionLevel() {
        return visionLevel;
    }

    /**
     * 运行时修改 AI 视野等级（GM/管理台调用）
     */
    public void setVisionLevel(int visionLevel) {
        this.visionLevel = visionLevel;
    }

    public int getAiLevel() {
        return aiLevel;
    }

    /**
     * 运行时修改 AI 智能等级（GM/管理台调用）
     */
    public void setAiLevel(int aiLevel) {
        this.aiLevel = Math.max(AiVision.AI_DUMB, Math.min(AiVision.AI_MASTER, aiLevel));
    }

    public void resetCurrentTrickCards() {
        lastPlayed = GameProto.CardInfo.getDefaultInstance();
        lastHand = null;
        consecutivePasses = 0;
    }

    public void resetHand() {
        landlordSeat = -1;
        lastPlaySeat = -1;
        baseScore = 1;
        robMultiplier = 1;
        bombMultiplier = 1;
        farmerEverPlayed = false;
        landlordPlayCount = 0;
        playedCardIds.clear();
        revealedBottomCards.clear();
        resetCurrentTrickCards();
    }

    public List<Integer> getRevealedBottomCards() {
        return Collections.unmodifiableList(revealedBottomCards);
    }

    public void setRevealedBottomCards(List<Integer> cardIds) {
        revealedBottomCards.clear();
        if (cardIds != null) {
            revealedBottomCards.addAll(cardIds);
        }
    }
}
