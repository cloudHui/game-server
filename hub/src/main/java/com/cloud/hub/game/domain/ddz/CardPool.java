package com.cloud.hub.game.domain.ddz;

import com.cloud.hub.game.domain.table.TableUser;
import com.cloud.hub.game.domain.card.CardSuit;
import com.cloud.hub.game.domain.cards.Card;
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
 * 斗地主专属牌池组件。
 * <p>
 * <b>职责与使用场景：</b>
 * <ul>
 *   <li>强类型持有 {@link DdzTable} 引用，消灭历史代码中的父类强转与向下造型；</li>
 *   <li>负责一局斗地主 54 张牌（含大小王）的洗牌、切牌、发牌（每人 17 张）以及保留 3 张底牌；</li>
 *   <li>在确定地主后由 {@link DdzBidService} 调用 {@link #sendBottomCards()} 将底牌发放给地主并全桌广播。</li>
 * </ul>
 *
 * @author cloud
 * @version 1.0
 */
public class CardPool {

    private static final Logger logger = LoggerFactory.getLogger(CardPool.class);

    private final List<Card> poolCards = new ArrayList<>();
    private final List<Card> bottomCards = new ArrayList<>();
    private final DdzTable table;

    public CardPool(DdzTable table) {
        this.table = table;
    }

    public List<Card> getBottomCards() {
        return bottomCards;
    }

    /**
     * 初始化并洗牌
     */
    public void initCards() {
        poolCards.clear();
        bottomCards.clear();
        for (Map.Entry<Integer, CardSuit> entry : CardSuit.getEs().entrySet()) {
            CardSuit suit = entry.getValue();
            for (int cardId = suit.getStartVal(); cardId <= suit.getEndVal(); cardId++) {
                poolCards.add(new Card(cardId));
            }
        }
        Collections.shuffle(poolCards);
    }

    public Card dealCard() {
        return poolCards.remove(poolCards.size() - 1);
    }

    public int leftSize() {
        return poolCards.size();
    }

    /**
     * 发初始手牌与底牌，并推送手牌通知。
     * 根据座位数动态计算每人手牌数，保证牌池合理分配。
     */
    public void dealInitCard() {
        initCards();
        Map<Integer, TableUser> seatUsers = table.getSeatUsers();
        int seatNum = table.getTableModel().getSeatNum();
        int totalCards = poolCards.size() + bottomCards.size();
        int bottom = 3;
        int perPlayer = (totalCards - bottom) / seatNum;
        TreeMap<Integer, TableUser> ordered = new TreeMap<>(seatUsers);
        for (int round = 0; round < perPlayer; round++) {
            for (int s = 0; s < seatNum; s++) {
                TableUser u = ordered.get(s);
                if (u != null) {
                    u.addCards(dealCard());
                }
            }
        }
        bottomCards.clear();
        for (int i = 0; i < bottom && poolCards.size() > 0; i++) {
            bottomCards.add(dealCard());
        }
        sendInitCardNotice(seatUsers);
    }

    /**
     * 将底牌并入地主手牌并再次通知手牌。
     * 底牌 ID 写入上下文，NotCard 末尾以 roleId=0 附带正面牌值，供桌面顶部展示。
     */
    public void attachBottomToLandlord(DdzTable table, int landlordSeat) {
        TableUser landlord = table.getSeatUser(landlordSeat);
        if (landlord == null) {
            logger.error("attachBottomToLandlord landlord null seat:{} table:{}", landlordSeat, table.getTableId());
            return;
        }
        List<Integer> bottomIds = new ArrayList<>(bottomCards.size());
        for (Card c : bottomCards) {
            bottomIds.add(c.getId());
            landlord.addCards(c);
        }
        bottomCards.clear();
        table.getDdz().setRevealedBottomCards(bottomIds);
        sendInitCardNotice(table.getSeatUsers());
    }

    public void sendInitCardNotice(Map<Integer, TableUser> seatUsers) {
        List<Integer> bottomIds = table.getDdz().getRevealedBottomCards();
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
            // 末尾附加底牌（roleId=0），机器人取首个正面牌组为自己，不受影响。
            if (!bottomIds.isEmpty()) {
                GameProto.NCardsInfo.Builder bottom = GameProto.NCardsInfo.newBuilder().setRoleId(0);
                for (int id : bottomIds) {
                    bottom.addCards(GameProto.Card.newBuilder().setValue(id).build());
                }
                builder.addNCards(bottom.build());
            }
            sendUser.sendRoleMessage(builder.build(), GMsg.NOT_CARD, table.getTableId());
            logger.info("table:{} role:{} sendCardNotify", table.getTableId(), sendUser.getUserId());
        }
    }
}
