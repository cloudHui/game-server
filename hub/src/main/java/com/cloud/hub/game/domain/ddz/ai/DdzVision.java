package com.cloud.hub.game.domain.ddz.ai;

import com.cloud.hub.game.domain.card.CardSuit;
import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.ddz.DdzTable;
import com.cloud.hub.game.domain.table.TableUser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 斗地主 AI 视野环境具体实现。
 * <p>
 * 严格按照 visionLevel 权限向决策层提供对局信息隔离：
 * <ul>
 *   <li>LEVEL_NORMAL(0)：公平视野，仅向 AI 暴露自己手牌和公共记牌器（已出牌）</li>
 *   <li>LEVEL_SEMI(1)：半透视视野，支持查询外部剩余牌库分布</li>
 *   <li>LEVEL_FULL(2)：全透视视野，可直接获知对手座位上的实时手牌与精确张数</li>
 * </ul>
 *
 * @author cloud
 * @version 1.0
 * @date 2026-06-11
 * @since 1.0
 */
public class DdzVision implements AiVision {

    /** 完整一副 54 张扑克牌模板（静态不可变列表） */
    private static final List<Card> FULL_DECK = buildFullDeck();

    /** 斗地主牌桌实例 */
    private final DdzTable table;
    /** 当前 AI 自身玩家实体 */
    private final TableUser self;
    /** 视野权限级别 (0=正常, 1=半透视, 2=全透视) */
    private final int visionLevel;
    /** AI 智能策略等级 (0=弱智, 1=基础, 2=高级, 3=大师) */
    private final int aiLevel;
    /** 当前牌局地主座位号（未定地主时为 -1） */
    private final int landlordSeat;

    /** 剩余牌池分组缓存（避免在同一操作周期内重复全量计算） */
    private Map<Integer, List<Card>> remainingPoolCache;

    /**
     * 构造斗地主 AI 视野对象。
     *
     * @param table       牌桌实体
     * @param self        当前 AI 自身用户
     * @param visionLevel 视野等级
     * @param aiLevel     AI 智能等级
     */
    public DdzVision(DdzTable table, TableUser self, int visionLevel, int aiLevel) {
        this.table = table;
        this.self = self;
        this.visionLevel = visionLevel;
        this.aiLevel = aiLevel;
        this.landlordSeat = table.getDdz().getLandlordSeat();
    }

    @Override
    public int getVisionLevel() {
        return visionLevel;
    }

    @Override
    public int getAiLevel() {
        return aiLevel;
    }

    @Override
    public List<Card> getMyHand() {
        return self.getCards();
    }

    @Override
    public Set<Integer> getPlayedCardIds() {
        return table.getDdz().getPlayedCardIds();
    }

    @Override
    public Map<Integer, List<Card>> getRemainingPool() {
        // 正常视野下禁止越权感知剩余未知牌池
        if (visionLevel < LEVEL_SEMI) {
            return null;
        }
        if (remainingPoolCache != null) {
            return remainingPoolCache;
        }
        Set<Integer> played = getPlayedCardIds();
        Set<Integer> myIds = new HashSet<>();
        for (Card c : self.getCards()) {
            myIds.add(c.getId());
        }
        remainingPoolCache = new HashMap<>();
        for (Card c : FULL_DECK) {
            if (!played.contains(c.getId()) && !myIds.contains(c.getId())) {
                remainingPoolCache.computeIfAbsent(c.getCardVal(), k -> new ArrayList<>()).add(c);
            }
        }
        return remainingPoolCache;
    }

    @Override
    public List<Card> getOpponentHand(int seat) {
        // 仅在全透视模式下允许调取对手手牌数据
        if (visionLevel < LEVEL_FULL) {
            return null;
        }
        TableUser user = table.getSeatUser(seat);
        if (user == null || user.getUserId() == self.getUserId()) {
            return null;
        }
        return user.getCards();
    }

    @Override
    public int getMinOpponentCards() {
        if (visionLevel >= LEVEL_FULL) {
            // 全透视精确值：直接遍历对手座位取最小剩余张数
            int min = Integer.MAX_VALUE;
            for (TableUser u : table.getUsers().values()) {
                if (u.getUserId() == self.getUserId()) {
                    continue;
                }
                if (isOpponent(u)) {
                    min = Math.min(min, u.getCards().size());
                }
            }
            return min == Integer.MAX_VALUE ? 20 : min;
        }
        // 正常视野估算：依据 54 张总牌数 - 已打出张数 - 己方手牌张数，推导对手平均持牌数
        int totalCards = 54;
        int played = getPlayedCardIds().size();
        int mine = self.getCards().size();
        int remaining = totalCards - played - mine;
        int oppCount = table.getUsers().size() - 1;
        return oppCount > 0 ? remaining / oppCount : remaining;
    }

    @Override
    public int remainingCountOfRank(int rank) {
        if (visionLevel < LEVEL_SEMI) {
            return -1;
        }
        Map<Integer, List<Card>> pool = getRemainingPool();
        List<Card> cards = pool.get(rank);
        return cards != null ? cards.size() : 0;
    }

    // ==================== 内部方法 ====================

    /**
     * 判断目标用户在当前阵营划分下是否属于己方的对手。
     *
     * @param u 待判断的桌内玩家
     * @return true 为对手，false 为队友或自己
     */
    private boolean isOpponent(TableUser u) {
        if (landlordSeat < 0) {
            return true; // 未定地主阶段各自为战，均视为竞争对手
        }
        boolean selfIsLandlord = self.getSeated() == landlordSeat;
        boolean otherIsLandlord = u.getSeated() == landlordSeat;
        return selfIsLandlord != otherIsLandlord; // 阵营不同即为对手
    }

    /**
     * 初始化构建单副 54 张标准扑克牌库集合。
     *
     * @return 不可变的扑克牌列表
     */
    private static List<Card> buildFullDeck() {
        List<Card> deck = new ArrayList<>(54);
        for (CardSuit suit : CardSuit.values()) {
            for (int id = suit.getStartVal(); id <= suit.getEndVal(); id++) {
                deck.add(new Card(id));
            }
        }
        return Collections.unmodifiableList(deck);
    }
}
