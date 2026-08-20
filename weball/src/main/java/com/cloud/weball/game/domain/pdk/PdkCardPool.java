package com.cloud.weball.game.domain.pdk;

import com.cloud.weball.game.domain.table.Table;
import com.cloud.weball.game.domain.table.TableUser;
import com.cloud.weball.game.domain.card.CardSuit;
import com.cloud.weball.game.domain.cards.Card;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.GameProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 跑得快牌池：48 张（去大小王、去掉一张黑桃2、一张黑桃A），每人 16 张。
 */
public class PdkCardPool {

    private static final Logger logger = LoggerFactory.getLogger(PdkCardPool.class);
    /**
     * 去掉黑桃 A(414) 与黑桃 2(415)
     */
    private static final int SKIP_SPADE_A = 414;
    private static final int SKIP_SPADE_2 = 415;
    /**
     * 方块 3，首局先出
     */
    public static final int DIAMOND_3 = 103;

    private final List<Card> poolCards = new ArrayList<>();
    private final Table table;

    public PdkCardPool(Table table) {
        this.table = table;
    }

    public void initCards() {
        poolCards.clear();
        for (Map.Entry<Integer, CardSuit> entry : CardSuit.getEs().entrySet()) {
            CardSuit suit = entry.getValue();
            if (suit == CardSuit.J) continue;
            for (int cardId = suit.getStartVal(); cardId <= suit.getEndVal(); cardId++) {
                if (cardId == SKIP_SPADE_A || cardId == SKIP_SPADE_2) continue;
                poolCards.add(new Card(cardId));
            }
        }
        Collections.shuffle(poolCards);
    }

    public void dealInitCard() {
        initCards();
        Map<Integer, TableUser> seatUsers = table.getSeatUsers();
        int seatNum = table.getTableModel().getSeatNum();
        int perPlayer = poolCards.size() / seatNum;
        TreeMap<Integer, TableUser> ordered = new TreeMap<>(seatUsers);
        for (int round = 0; round < perPlayer; round++) {
            for (int s = 0; s < seatNum; s++) {
                TableUser u = ordered.get(s);
                if (u != null && !poolCards.isEmpty()) {
                    u.addCards(poolCards.remove(poolCards.size() - 1));
                }
            }
        }
        sendInitCardNotice(seatUsers);
    }

    public int findSeatWithCard(int cardId) {
        for (Map.Entry<Integer, TableUser> e : table.getSeatUsers().entrySet()) {
            for (Card c : e.getValue().getCards()) {
                if (c.getId() == cardId) return e.getKey();
            }
        }
        return 0;
    }

    public void sendInitCardNotice(Map<Integer, TableUser> seatUsers) {
        for (Map.Entry<Integer, TableUser> entry : seatUsers.entrySet()) {
            TableUser sendUser = entry.getValue();
            GameProto.NotCard.Builder builder = GameProto.NotCard.newBuilder();
            for (Map.Entry<Integer, TableUser> userEntry : seatUsers.entrySet()) {
                TableUser otherUser = userEntry.getValue();
                GameProto.NCardsInfo.Builder nCards = GameProto.NCardsInfo.newBuilder()
                        .setRoleId(otherUser.getUserId());
                boolean owner = otherUser.equals(sendUser);
                for (Card card : otherUser.getCards()) {
                    nCards.addCards(GameProto.Card.newBuilder().setValue(owner ? card.getId() : 0).build());
                }
                builder.addNCards(nCards.build());
            }
            sendUser.sendRoleMessage(builder.build(), GMsg.NOT_CARD, table.getTableId());
            logger.info("pdk table:{} role:{} sendCardNotify", table.getTableId(), sendUser.getUserId());
        }
    }
}
