package game.manager.table.mj.ai;

import game.manager.table.TableUser;
import game.manager.table.cards.Card;
import game.manager.table.ddz.ai.AiVision;
import game.manager.table.mj.MjExposedSet;
import game.manager.table.mj.MjTable;
import game.manager.table.mj.card.MjConst;

import java.util.*;

/**
 * 麻将 AI 视野实现。
 * <p>
 * 根据 visionLevel 控制 AI 能看到的信息范围：
 * <ul>
 *   <li>0(NORMAL) — 自己手牌 + 各家弃牌</li>
 *   <li>1(SEMI)   — + 牌山剩余牌构成</li>
 *   <li>2(FULL)   — + 其他玩家手牌</li>
 * </ul>
 *
 * @author cloud
 * @version 1.0
 * @date 2026-06-11
 * @since 1.0
 */
public class MjVision implements AiVision {
    public static final int AI_DUMB = AiVision.AI_DUMB;
    public static final int AI_BASIC = AiVision.AI_BASIC;
    public static final int AI_ADVANCED = AiVision.AI_ADVANCED;
    public static final int AI_MASTER = AiVision.AI_MASTER;

    private final MjTable table;
    private final TableUser self;
    private final int visionLevel;
    private final int aiLevel;

    // 缓存
    private Map<Integer, List<Card>> remainingPoolCache;
    private Set<Integer> playedCardIdsCache;

    public MjVision(MjTable table, TableUser self, int visionLevel, int aiLevel) {
        this.table = table;
        this.self = self;
        this.visionLevel = visionLevel;
        this.aiLevel = aiLevel;
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

    /**
     * 获取所有已打出的牌 ID 集合（各家弃牌汇总）
     */
    @Override
    public Set<Integer> getPlayedCardIds() {
        if (playedCardIdsCache != null) {
            return playedCardIdsCache;
        }
        playedCardIdsCache = new HashSet<>();
        int seatNum = table.getTableModel().getSeatNum();
        for (int seat = 0; seat < seatNum; seat++) {
            List<Integer> pile = table.getMjContext().getDiscardPile(seat);
            if (pile != null) {
                playedCardIdsCache.addAll(pile);
            }
        }
        return playedCardIdsCache;
    }

    /**
     * 剩余未出牌按 value 分组（level ≥ 1 时可用）。
     * key = tileId, value = 该牌剩余的 Card 列表
     */
    @Override
    public Map<Integer, List<Card>> getRemainingPool() {
        if (visionLevel < LEVEL_SEMI) {
            return null;
        }
        if (remainingPoolCache != null) {
            return remainingPoolCache;
        }
        remainingPoolCache = new HashMap<>();
        for (int suit = 1; suit <= 5; suit++) {
            int maxVal = suit <= 3 ? 9 : (suit == 4 ? 4 : 3);
            for (int val = 1; val <= maxVal; val++) {
                int tileId = MjConst.encode(suit, val);
                int remaining = getPublicRemainingCount(tileId);
                for (int copy = 0; copy < remaining; copy++) {
                    remainingPoolCache.computeIfAbsent(tileId, k -> new ArrayList<>()).add(new Card(tileId));
                }
            }
        }
        return remainingPoolCache;
    }

    @Override
    public List<Card> getOpponentHand(int seat) {
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
            int min = Integer.MAX_VALUE;
            for (TableUser u : table.getUsers().values()) {
                if (u.getUserId() != self.getUserId()) {
                    min = Math.min(min, u.getCards().size());
                }
            }
            return min == Integer.MAX_VALUE ? 13 : min;
        }
        // 非透视：返回平均值估算
        int totalRemaining = 136 - countDiscardedTiles() - self.getCards().size()
                - countExposedTiles();
        int oppCount = table.getUsers().size() - 1;
        return oppCount > 0 ? totalRemaining / oppCount : 13;
    }

    @Override
    public int remainingCountOfRank(int tileId) {
        if (visionLevel < LEVEL_SEMI) {
            return -1;
        }
        Map<Integer, List<Card>> pool = getRemainingPool();
        List<Card> cards = pool.get(tileId);
        return cards != null ? cards.size() : 0;
    }

    /**
     * 仅根据自己手牌、弃牌区和副露区计算某张牌的可见剩余数。
     * 该信息在普通公平视野下也可用，不读取牌山或其他玩家手牌。
     */
    public int getPublicRemainingCount(int tileId) {
        int visible = countTiles(self.getCards(), tileId);
        int seatNum = table.getTableModel().getSeatNum();
        for (int seat = 0; seat < seatNum; seat++) {
            visible += countValues(getDiscardPile(seat), tileId);
            for (MjExposedSet set : getExposedSets(seat)) {
                visible += countValues(set.getTileIds(), tileId);
            }
        }
        return Math.max(0, MjConst.COPY_COUNT - visible);
    }

    // ==================== 麻将专用方法 ====================

    /**
     * 获取指定座位的副露列表
     */
    public List<MjExposedSet> getExposedSets(int seat) {
        List<MjExposedSet> sets = table.getMjContext().getExposedSets(seat);
        return sets != null ? sets : java.util.Collections.emptyList();
    }

    /**
     * 获取赖子牌 ID（0 表示无赖子）
     */
    public int getLaiZiTileId() {
        return table.getMjContext().getLaiZiTileId();
    }

    /**
     * 获取指定座位的弃牌列表
     */
    public List<Integer> getDiscardPile(int seat) {
        List<Integer> pile = table.getMjContext().getDiscardPile(seat);
        return pile != null ? pile : java.util.Collections.emptyList();
    }

    // ==================== 内部方法 ====================

    private int countExposedTiles() {
        int count = 0;
        int seatNum = table.getTableModel().getSeatNum();
        for (int seat = 0; seat < seatNum; seat++) {
            List<MjExposedSet> sets = table.getMjContext().getExposedSets(seat);
            if (sets != null) {
                for (MjExposedSet es : sets) {
                    count += es.getTileIds().size();
                }
            }
        }
        return count;
    }

    private int countDiscardedTiles() {
        int count = 0;
        int seatNum = table.getTableModel().getSeatNum();
        for (int seat = 0; seat < seatNum; seat++) {
            count += getDiscardPile(seat).size();
        }
        return count;
    }

    private static int countTiles(List<Card> cards, int tileId) {
        int count = 0;
        for (Card card : cards) {
            if (card.getId() == tileId) count++;
        }
        return count;
    }

    private static int countValues(List<Integer> tiles, int tileId) {
        int count = 0;
        for (Integer tile : tiles) {
            if (tile != null && tile == tileId) count++;
        }
        return count;
    }
}
