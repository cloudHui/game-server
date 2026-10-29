package com.cloud.hub.game.domain.poker.niuniu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.cards.CardSuit;
import com.cloud.hub.game.domain.replay.ReplayRecorder;
import com.cloud.hub.game.domain.table.TableUser;

/**
 * 牛牛牌池与明牌抢庄两段发牌管理器（先发 4 张明牌，下注结束后补发第 5 张决胜牌）。
 */
public class NiuniuPool {

    /** 所属牛牛游戏桌实例 */
    private final NiuniuTable table;
    /** 牌堆剩余待发扑克牌列表 */
    private final List<Card> poolCards = new ArrayList<>();

    /**
     * 构造牌池管理器。
     *
     * @param table 关联的牛牛游戏桌
     */
    public NiuniuPool(NiuniuTable table) {
        this.table = table;
    }

    /**
     * 初始化并洗混标准 52 张无王扑克牌。
     */
    public void initCards() {
        poolCards.clear();
        for (CardSuit suit : CardSuit.getEs().values()) {
            if (suit == CardSuit.J) {
                continue;
            }
            for (int cardId = suit.getStartVal(); cardId <= suit.getEndVal(); cardId++) {
                poolCards.add(new Card(cardId));
            }
        }
        Collections.shuffle(poolCards);
    }

    /**
     * 开局首轮向每位在座玩家发放前 4 张明牌。
     */
    public void dealFourCards() {
        for (TableUser user : table.getSeatUsers().values()) {
            user.getCards().clear();
            for (int i = 0; i < 4 && !poolCards.isEmpty(); i++) {
                user.addCards(poolCards.remove(0));
            }
        }
    }

    /**
     * 闲家下注结束后向每位在座玩家补发第 5 张牌，并同步写入回放流水。
     */
    public void dealLastCard() {
        ReplayRecorder replay = table.getReplayRecorder();
        for (TableUser user : table.getSeatUsers().values()) {
            // 仅对不足 5 张的在座玩家补发，防止超时与主动下注临界点重复补牌
            if (user.getCards().size() < 5 && !poolCards.isEmpty()) {
                Card fifth = poolCards.remove(0);
                user.addCards(fifth);
                if (replay != null) {
                    replay.writeAuditEvent("座" + user.getSeated() + " 补发第5张 " + fifth.getId());
                }
            }
        }
    }

    /**
     * 获取当前牌堆剩余未发出的牌列表（供后台牌桌透视使用）。
     *
     * @return 剩余牌列表
     */
    public List<Card> getPoolCards() {
        return poolCards;
    }
}
