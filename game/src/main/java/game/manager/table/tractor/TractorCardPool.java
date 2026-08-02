package game.manager.table.tractor;

import game.manager.table.Table;
import game.manager.table.TableUser;
import game.manager.table.card.CardSuit;
import game.manager.table.cards.Card;
import game.manager.table.replay.PokerReplayRecorder;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.GameProto;

import java.util.*;

/**
 * 拖拉机牌池：两副牌 108 张，每人 25，底牌 8。
 */
public class TractorCardPool {

    private static final Logger logger = LoggerFactory.getLogger(TractorCardPool.class);
    public static final int BOTTOM = 8;
    public static final int PER_PLAYER = 25;

    private final List<Card> poolCards = new ArrayList<>();
    private final List<Card> bottomCards = new ArrayList<>();
    private final Table table;

    public TractorCardPool(Table table) {
        this.table = table;
    }

    public List<Card> getBottomCards() {
        return bottomCards;
    }

    public void initCards() {
        poolCards.clear();
        bottomCards.clear();
        for (int deck = 0; deck < 2; deck++) {
            for (Map.Entry<Integer, CardSuit> entry : CardSuit.getEs().entrySet()) {
                CardSuit suit = entry.getValue();
                for (int cardId = suit.getStartVal(); cardId <= suit.getEndVal(); cardId++) {
                    poolCards.add(new Card(cardId));
                }
            }
        }
        Collections.shuffle(poolCards);
    }

    /**
     * 洗牌并准备发牌。
     */
    public void prepareDeal(int startSeat) {
        initCards();
        TractorTableContext ctx = ctx();
        int seats = Math.max(1, table.getTableModel().getSeatNum());
        ctx.setDealNextSeat(Math.floorMod(startSeat, seats));
        ctx.setDealPlayerCount(0);
        ctx.setDealing(true);
        ctx.setLastDealTime(0);
    }

    /**
     * 一次发完所有手牌并扣下底牌；dealing 保持 true 直到动画/抢主窗口结束。
     */
    public void dealAllNow() {
        TractorTableContext ctx = ctx();
        int seatNum = table.getTableModel().getSeatNum();
        int total = PER_PLAYER * seatNum;
        while (ctx.getDealPlayerCount() < total && !poolCards.isEmpty()) {
            int seat = ctx.getDealNextSeat();
            TableUser u = table.getSeatUser(seat);
            if (u != null) u.addCards(poolCards.remove(poolCards.size() - 1));
            ctx.setDealPlayerCount(ctx.getDealPlayerCount() + 1);
            ctx.setDealNextSeat((seat + 1) % seatNum);
        }
        bottomCards.clear();
        for (int i = 0; i < BOTTOM && !poolCards.isEmpty(); i++) {
            bottomCards.add(poolCards.remove(poolCards.size() - 1));
        }
    }

    private TractorTableContext ctx() {
        return ((TractorTable) table).getTractor();
    }

    /**
     * 庄家拿底 8 张，进入 30 秒扣底（再放回 8 张后开出）。
     */
    public void attachBottom(TractorTable table, int bankerSeat) {
        TableUser banker = table.getSeatUser(bankerSeat);
        if (banker == null) return;
        moveGroundBottomToPool(table);
        List<Integer> bottomIds = new ArrayList<>();
        for (Card c : bottomCards) {
            bottomIds.add(c.getId());
            banker.addCards(c);
        }
        bottomCards.clear();
        table.getTractor().setRevealedBottom(bottomIds);
        table.getTractor().setBottomHolderSeat(bankerSeat);
        table.getTractor().setBuriedCards(Collections.emptyList());
        sendInitCardNotice(table.getSeatUsers());
        logger.info("拖拉机拿底 table:{} banker:{} bottom:{}", table.getTableId(), bankerSeat, bottomIds);
    }

    /**
     * 反主换庄：把已扣的 8 张（或未扣时手里的底）放回地下池，再给新庄拿起。
     */
    public void moveGroundBottomToPool(TractorTable table) {
        TractorTableContext ctx = table.getTractor();
        if (!ctx.getBuriedCards().isEmpty()) {
            bottomCards.clear();
            for (int id : ctx.getBuriedCards()) bottomCards.add(new Card(id));
            ctx.setBuriedCards(Collections.emptyList());
            ctx.setRevealedBottom(Collections.emptyList());
            ctx.setBottomHolderSeat(-1);
            sendInitCardNotice(table.getSeatUsers());
            return;
        }
        List<Integer> ids = new ArrayList<>(ctx.getRevealedBottom());
        if (ids.isEmpty()) return;
        int holder = ctx.getBottomHolderSeat();
        if (holder < 0) holder = ctx.getBankerSeat();
        TableUser user = table.getSeatUser(holder);
        if (user != null) {
            Map<Integer, Integer> need = new TreeMap<>();
            for (int id : ids) need.merge(id, 1, Integer::sum);
            Map<Integer, Integer> left = new TreeMap<>(need);
            for (Card c : new ArrayList<>(user.getCards())) {
                Integer n = left.get(c.getId());
                if (n != null && n > 0) {
                    user.getCards().remove(c);
                    bottomCards.add(c);
                    left.put(c.getId(), n - 1);
                }
            }
        }
        ctx.setRevealedBottom(Collections.emptyList());
        ctx.setBottomHolderSeat(-1);
        sendInitCardNotice(table.getSeatUsers());
    }

    /**
     * 放回 8 张到地下；本人仍可见、不可再改。
     */
    public boolean buryCards(TractorTable table, int bankerSeat, List<Integer> buryIds) {
        TableUser banker = table.getSeatUser(bankerSeat);
        if (banker == null || buryIds == null || buryIds.size() != BOTTOM) return false;
        if (!table.getTractor().getBuriedCards().isEmpty()) return false;
        List<Card> bury = new ArrayList<>();
        Map<Integer, Integer> need = new TreeMap<>();
        for (int id : buryIds) need.merge(id, 1, Integer::sum);
        Map<Integer, Integer> have = new TreeMap<>();
        for (Card c : banker.getCards()) have.merge(c.getId(), 1, Integer::sum);
        for (Map.Entry<Integer, Integer> e : need.entrySet()) {
            if (have.getOrDefault(e.getKey(), 0) < e.getValue()) return false;
        }
        Map<Integer, Integer> left = new TreeMap<>(need);
        for (Card c : new ArrayList<>(banker.getCards())) {
            Integer n = left.get(c.getId());
            if (n != null && n > 0) {
                bury.add(c);
                left.put(c.getId(), n - 1);
                banker.getCards().remove(c);
            }
        }
        if (bury.size() != BOTTOM) {
            for (Card c : bury) banker.addCards(c);
            return false;
        }
        bottomCards.clear();
        bottomCards.addAll(bury);
        List<Integer> buriedIds = new ArrayList<>(buryIds);
        table.getTractor().setBuriedCards(buriedIds);
        table.getTractor().setRevealedBottom(buriedIds);
        table.getTractor().setBottomHolderSeat(bankerSeat);
        if (table.getReplayRecorder() instanceof PokerReplayRecorder) {
            ((PokerReplayRecorder) table.getReplayRecorder()).recordBury(bankerSeat, buriedIds);
        }
        sendInitCardNotice(table.getSeatUsers());
        logger.info("拖拉机扣底 table:{} banker:{} bury:{}", table.getTableId(), bankerSeat, buryIds);
        return true;
    }

    public void autoBury(TractorTable table, int bankerSeat) {
        if (!table.getTractor().getBuriedCards().isEmpty()) return;
        TableUser banker = table.getSeatUser(bankerSeat);
        if (banker == null) return;
        List<Card> hand = new ArrayList<>(banker.getCards());
        hand.sort((a, b) -> Integer.compare(buryPriority(table, a), buryPriority(table, b)));
        List<Integer> buryIds = new ArrayList<>();
        for (int i = 0; i < BOTTOM && i < hand.size(); i++) buryIds.add(hand.get(i).getId());
        buryCards(table, bankerSeat, buryIds);
    }

    /**
     * 越小越先扣：优先扣废副牌；主牌/分牌尽量留在手里控场与消化。
     */
    private int buryPriority(TractorTable table, Card c) {
        TractorTableContext ctx = table.getTractor();
        int level = ctx.getLevelRank();
        int trump = ctx.getTrumpSuit();
        int score = TractorRules.scoreOf(c);
        boolean isTrump = TractorRules.isTrump(c, level, trump);
        int base = TractorRules.power(c, level, trump);
        if (isTrump) base += 5000;
        if (score > 0) base += 2000;
        return base;
    }

    public void sendInitCardNotice(Map<Integer, TableUser> seatUsers) {
        TractorTable tractorTable = table instanceof TractorTable ? (TractorTable) table : null;
        List<Integer> bottomIds = tractorTable != null
                ? tractorTable.getTractor().getRevealedBottom() : Collections.emptyList();
        int holderSeat = tractorTable != null ? tractorTable.getTractor().getBottomHolderSeat() : -1;
        if (holderSeat < 0 && tractorTable != null) {
            holderSeat = tractorTable.getTractor().getBankerSeat();
        }
        for (Map.Entry<Integer, TableUser> entry : seatUsers.entrySet()) {
            TableUser sendUser = entry.getValue();
            GameProto.NotCard.Builder builder = GameProto.NotCard.newBuilder();
            for (Map.Entry<Integer, TableUser> userEntry : seatUsers.entrySet()) {
                TableUser other = userEntry.getValue();
                GameProto.NCardsInfo.Builder nCards = GameProto.NCardsInfo.newBuilder().setRoleId(other.getUserId());
                boolean owner = other.equals(sendUser);
                for (Card card : other.getCards()) {
                    nCards.addCards(GameProto.Card.newBuilder().setValue(owner ? card.getId() : 0));
                }
                builder.addNCards(nCards.build());
            }
            if (!bottomIds.isEmpty()) {
                GameProto.NCardsInfo.Builder bottom = GameProto.NCardsInfo.newBuilder().setRoleId(0);
                boolean holderViewer = sendUser.getSeated() == holderSeat;
                for (int id : bottomIds) {
                    bottom.addCards(GameProto.Card.newBuilder().setValue(holderViewer ? id : 0));
                }
                builder.addNCards(bottom.build());
            }
            sendUser.sendRoleMessage(builder.build(), GMsg.NOT_CARD, table.getTableId());
        }
    }
}
