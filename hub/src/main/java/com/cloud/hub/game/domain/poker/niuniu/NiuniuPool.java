package com.cloud.hub.game.domain.poker.niuniu;

import com.cloud.hub.game.domain.cards.Card;
import com.cloud.hub.game.domain.cards.CardSuit;
import com.cloud.hub.game.domain.table.TableUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 牛牛牌池与发牌管理器。
 *
 * @author cloud
 */
public class NiuniuPool {

    private static final Logger logger = LoggerFactory.getLogger(NiuniuPool.class);

    private final NiuniuTable table;
    private final List<Card> poolCards = new ArrayList<>();

    public NiuniuPool(NiuniuTable table) {
        this.table = table;
    }

    public void initCards(boolean hasJoker) {
        poolCards.clear();
        for (Map.Entry<Integer, CardSuit> entry : CardSuit.getEs().entrySet()) {
            CardSuit suit = entry.getValue();
            if (suit == CardSuit.J) {
                if (hasJoker) {
                    poolCards.add(new Card(suit.getStartVal()));
                    poolCards.add(new Card(suit.getEndVal()));
                }
                continue;
            }
            for (int cardId = suit.getStartVal(); cardId <= suit.getEndVal(); cardId++) {
                poolCards.add(new Card(cardId));
            }
        }
        Collections.shuffle(poolCards);
        logger.info("牛牛牌池初始化完成, 总牌数: {}, hasJoker: {}, tableId: {}",
                poolCards.size(), hasJoker, table.getTableId());
    }

    public void dealFourCards() {
        for (TableUser user : table.getSeatUsers().values()) {
            user.getCards().clear();
            for (int i = 0; i < 4; i++) {
                if (!poolCards.isEmpty()) {
                    user.addCards(poolCards.remove(0));
                }
            }
        }
        logger.info("牛牛发前4张牌完成, 人数: {}, tableId: {}",
                table.getSeatUsers().size(), table.getTableId());
    }

    public void dealLastCard() {
        for (TableUser user : table.getSeatUsers().values()) {
            if (!poolCards.isEmpty()) {
                user.addCards(poolCards.remove(0));
            }
        }
        logger.info("牛牛补发第5张牌完成, 人数: {}, tableId: {}",
                table.getSeatUsers().size(), table.getTableId());
    }

    public List<Card> getPoolCards() {
        return poolCards;
    }
}
