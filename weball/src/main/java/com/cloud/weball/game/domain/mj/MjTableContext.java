package com.cloud.weball.game.domain.mj;

import com.cloud.weball.game.domain.ddz.ai.AiVision;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 麻将一桌内的运行时状态
 */
public class MjTableContext {

    /**
     * 当前庄家座位
     */
    private int dealerSeat;

    /**
     * 当前摸的牌ID(用于超时自动打牌)
     */
    private int drawnTile;

    /**
     * 当前玩家已摸牌(防止重复摸牌)
     */
    private boolean tileDrawn;

    /**
     * 碰/吃后接牌直接出牌，不需要再摸牌
     */
    private boolean discardAfterClaim;

    /**
     * 出牌提示已发送(防止重复发送)
     */
    private boolean discardPromptSent;

    /**
     * 最后打出的牌ID
     */
    private int lastDiscardTile;

    /**
     * 最后出牌的座位
     */
    private int lastDiscardSeat;

    // --- 新增字段 ---

    /**
     * 每个座位的副露区(碰/杠/吃亮出的牌)
     */
    private final Map<Integer, List<MjExposedSet>> exposedSetsMap = new HashMap<>();

    /**
     * 赖子牌ID, 0表示无赖子
     */
    private int laiZiTileId;

    /**
     * 当前赖子牌的显示值(翻牌确定)
     */
    private int laiZiFlipTile;

    /**
     * 每个座位是否有副露(开口笑规则用)
     */
    private final Map<Integer, Boolean> openedMap = new HashMap<>();

    /**
     * 弃牌堆(每座的出牌历史)
     */
    private final Map<Integer, List<Integer>> discardPileMap = new HashMap<>();
    /**
     * 全桌真实出牌顺序，快照稳定布局使用。
     */
    private final List<DiscardRecord> discardHistory = new ArrayList<>();

    /**
     * claim相关: 等待claim的座位列表
     */
    private final List<Integer> pendingClaimSeats = new ArrayList<>();

    /**
     * claim相关: 当前正在等待claim的牌ID
     */
    private int claimTileId;

    /**
     * claim相关: 出牌者的座位
     */
    private int claimFromSeat = -1;

    /**
     * 本轮检测到的全部 claim（座位→信息），用于截胡/优先级推进
     */
    private final Map<Integer, MjClaimInfo> claimInfoBySeat = new LinkedHashMap<>();

    /**
     * 是否处于胡牌询问阶段（截胡逐家 / 一炮多响并行）
     */
    private boolean claimHuPhase;

    /**
     * 本轮是否按一炮多响处理
     */
    private boolean multiHuMode;

    /**
     * 截胡：尚未询问的可胡座位队列（逆时针）
     */
    private final List<Integer> huQueue = new ArrayList<>();

    /**
     * 一炮多响：已选择胡的座位
     */
    private final LinkedHashSet<Integer> multiHuAccepted = new LinkedHashSet<>();

    /**
     * 杠上开花标记(杠后补牌胡)
     */
    private boolean gangShangKaiHua;

    /**
     * 抢杠胡标记
     */
    private boolean qiangGangHu;

    /**
     * 海底标记(牌墙最后一张)
     */
    private boolean haiDi;

    /**
     * AI 视野等级：0=正常, 1=半透视(知剩余牌池), 2=全透视(知他人手牌)
     */
    private int visionLevel;
    /**
     * AI 智能等级：0=最笨(摸什么打什么/自动过), 1=基础策略, 2=高级策略
     */
    private int aiLevel = 2;

    // --- Getters & Setters ---

    public int getDealerSeat() {
        return dealerSeat;
    }

    public void setDealerSeat(int dealerSeat) {
        this.dealerSeat = dealerSeat;
    }

    public int getDrawnTile() {
        return drawnTile;
    }

    public void setDrawnTile(int drawnTile) {
        this.drawnTile = drawnTile;
    }

    public boolean isTileDrawn() {
        return tileDrawn;
    }

    public void setTileDrawn(boolean tileDrawn) {
        this.tileDrawn = tileDrawn;
    }

    public boolean isDiscardAfterClaim() {
        return discardAfterClaim;
    }

    public void setDiscardAfterClaim(boolean discardAfterClaim) {
        this.discardAfterClaim = discardAfterClaim;
    }

    public boolean isDiscardPromptSent() {
        return discardPromptSent;
    }

    public void setDiscardPromptSent(boolean discardPromptSent) {
        this.discardPromptSent = discardPromptSent;
    }

    public int getLastDiscardTile() {
        return lastDiscardTile;
    }

    public void setLastDiscardTile(int lastDiscardTile) {
        this.lastDiscardTile = lastDiscardTile;
    }

    public int getLastDiscardSeat() {
        return lastDiscardSeat;
    }

    public void setLastDiscardSeat(int lastDiscardSeat) {
        this.lastDiscardSeat = lastDiscardSeat;
    }

    public void resetTurn() {
        tileDrawn = false;
        discardAfterClaim = false;
        discardPromptSent = false;
        drawnTile = 0;
    }

    public void resetRound() {
        lastDiscardTile = 0;
        lastDiscardSeat = -1;
        exposedSetsMap.clear();
        openedMap.clear();
        discardPileMap.clear();
        discardHistory.clear();
        laiZiTileId = 0;
        laiZiFlipTile = 0;
        gangShangKaiHua = false;
        qiangGangHu = false;
        haiDi = false;
        clearClaimRuntime();
        resetTurn();
    }

    // --- 副露区管理 ---

    /**
     * 获取某个座位的副露区（只读，不存在返回空list，不创建新对象）
     */
    public List<MjExposedSet> getExposedSets(int seat) {
        return exposedSetsMap.getOrDefault(seat, Collections.emptyList());
    }

    /**
     * 获取或创建某个座位的副露区（写操作用）
     */
    private List<MjExposedSet> getOrCreateExposedSets(int seat) {
        return exposedSetsMap.computeIfAbsent(seat, k -> new ArrayList<>());
    }

    /**
     * 添加副露
     */
    public void addExposedSet(int seat, MjExposedSet set) {
        getOrCreateExposedSets(seat).add(set);
        openedMap.put(seat, true);
    }

    /**
     * 某座位是否有副露
     */
    public boolean hasOpened(int seat) {
        return openedMap.getOrDefault(seat, false);
    }

    // --- 弃牌堆管理 ---

    /**
     * 记录出牌
     */
    public void addDiscard(int seat, int tileId) {
        discardPileMap.computeIfAbsent(seat, k -> new ArrayList<>()).add(tileId);
        discardHistory.add(new DiscardRecord(seat, tileId));
    }

    /**
     * 获取弃牌堆
     */
    public List<Integer> getDiscardPile(int seat) {
        return discardPileMap.getOrDefault(seat, Collections.emptyList());
    }

    public List<DiscardRecord> getDiscardHistory() {
        return Collections.unmodifiableList(discardHistory);
    }

    /**
     * 吃、碰、明杠、点炮胡认领最后弃牌后，将其移出可见弃牌区。
     */
    public void claimLastDiscard(int fromSeat, int tileId) {
        List<Integer> pile = discardPileMap.get(fromSeat);
        if (pile != null && !pile.isEmpty() && pile.get(pile.size() - 1) == tileId) {
            pile.remove(pile.size() - 1);
        }
        if (!discardHistory.isEmpty()) {
            DiscardRecord last = discardHistory.get(discardHistory.size() - 1);
            if (last.seat == fromSeat && last.tileId == tileId) {
                discardHistory.remove(discardHistory.size() - 1);
            }
        }
    }

    public static final class DiscardRecord {
        private final int seat;
        private final int tileId;

        DiscardRecord(int seat, int tileId) {
            this.seat = seat;
            this.tileId = tileId;
        }

        public int getSeat() {
            return seat;
        }

        public int getTileId() {
            return tileId;
        }
    }

    // --- 赖子 ---

    public int getLaiZiTileId() {
        return laiZiTileId;
    }

    public void setLaiZiTileId(int laiZiTileId) {
        this.laiZiTileId = laiZiTileId;
    }

    public int getLaiZiFlipTile() {
        return laiZiFlipTile;
    }

    public void setLaiZiFlipTile(int laiZiFlipTile) {
        this.laiZiFlipTile = laiZiFlipTile;
    }

    // --- Claim管理 ---

    /**
     * 设置claim信息
     */
    public void setClaimInfo(int tileId, int fromSeat, List<Integer> waitingSeats) {
        this.claimTileId = tileId;
        this.claimFromSeat = fromSeat;
        this.pendingClaimSeats.clear();
        this.pendingClaimSeats.addAll(waitingSeats);
    }

    /**
     * 写入本轮全部 claim 检测结果并重置胡阶段状态
     */
    public void beginClaimRound(Map<Integer, MjClaimInfo> bySeat, boolean multiHu) {
        claimInfoBySeat.clear();
        claimInfoBySeat.putAll(bySeat);
        claimHuPhase = false;
        multiHuMode = multiHu;
        huQueue.clear();
        multiHuAccepted.clear();
    }

    public Map<Integer, MjClaimInfo> getClaimInfoBySeat() {
        return claimInfoBySeat;
    }

    public boolean isClaimHuPhase() {
        return claimHuPhase;
    }

    public void setClaimHuPhase(boolean claimHuPhase) {
        this.claimHuPhase = claimHuPhase;
    }

    public boolean isMultiHuMode() {
        return multiHuMode;
    }

    public List<Integer> getHuQueue() {
        return huQueue;
    }

    public LinkedHashSet<Integer> getMultiHuAccepted() {
        return multiHuAccepted;
    }

    /**
     * 某座位完成claim(响应或pass)
     */
    public void removeClaimSeat(int seat) {
        pendingClaimSeats.remove(Integer.valueOf(seat));
    }

    /**
     * 是否还有待响应的claim
     */
    public boolean hasPendingClaims() {
        return !pendingClaimSeats.isEmpty();
    }

    public List<Integer> getPendingClaimSeats() {
        return pendingClaimSeats;
    }

    public int getClaimTileId() {
        return claimTileId;
    }

    public int getClaimFromSeat() {
        return claimFromSeat;
    }

    /**
     * 清空 claim 运行时（含胡队列）
     */
    public void clearClaimRuntime() {
        pendingClaimSeats.clear();
        claimInfoBySeat.clear();
        huQueue.clear();
        multiHuAccepted.clear();
        claimHuPhase = false;
        multiHuMode = false;
        claimTileId = 0;
        claimFromSeat = -1;
    }

    // --- 特殊胡牌标记 ---

    public boolean isGangShangKaiHua() {
        return gangShangKaiHua;
    }

    public void setGangShangKaiHua(boolean gangShangKaiHua) {
        this.gangShangKaiHua = gangShangKaiHua;
    }

    public boolean isQiangGangHu() {
        return qiangGangHu;
    }

    public void setQiangGangHu(boolean qiangGangHu) {
        this.qiangGangHu = qiangGangHu;
    }

    public boolean isHaiDi() {
        return haiDi;
    }

    public void setHaiDi(boolean haiDi) {
        this.haiDi = haiDi;
    }

    public int getVisionLevel() {
        return visionLevel;
    }

    public void setVisionLevel(int visionLevel) {
        this.visionLevel = visionLevel;
    }

    public int getAiLevel() {
        return aiLevel;
    }

    public void setAiLevel(int aiLevel) {
        this.aiLevel = Math.max(AiVision.AI_DUMB,
                Math.min(AiVision.AI_MASTER, aiLevel));
    }
}
