package com.cloud.weball.game.domain.ddz;

import com.cloud.weball.game.domain.table.TableUser;
import com.cloud.weball.game.domain.cards.Card;
import com.cloud.weball.game.domain.cards.CardOps;
import com.cloud.weball.game.domain.ddz.ai.DdzSimpleAi;
import com.cloud.weball.game.domain.replay.DdzReplayRecorder;
import com.cloud.weball.game.domain.replay.ReplayRecorder;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 斗地主出牌服务
 * 处理出牌、过牌、自动出牌、AI出牌
 */
public final class DdzPlayService {

    private static final Logger logger = LoggerFactory.getLogger(DdzPlayService.class);

    private DdzPlayService() {
    }

    /**
     * 超时/托管自动出最小牌
     */
    public static void autoPlaySmallest(DdzTable table, int userId) {
        logger.info("斗地主超时自动出最小牌, tableId: {}, userId: {}", table.getTableId(), userId);
        TableUser user = table.getUsers().get(userId);
        if (user == null || user.getCards().isEmpty()) return;

        List<Card> hand = user.getCards();
        Card smallest = Collections.min(hand);

        ReplayRecorder replay = table.getReplayRecorder();
        if (replay instanceof DdzReplayRecorder) {
            ((DdzReplayRecorder) replay).recordAutoPlay(user.getSeated(), Collections.singletonList(smallest.getId()));
        }

        GameProto.OpInfo op = GameProto.OpInfo.newBuilder()
                .setChoice(ConstProto.Operation.PLAY)
                .addOpCards(GameProto.CardInfo.newBuilder()
                        .addCards(GameProto.Card.newBuilder().setValue(smallest.getId()).build())
                        .build())
                .build();
        apply(table, userId, op);
    }

    /**
     * 托管/超时：走简易AI，失败返回false
     */
    public static boolean autoPlayAi(DdzTable table, int userId) {
        TableUser user = table.getUsers().get(userId);
        if (user == null) return false;
        GameProto.OpInfo op = DdzSimpleAi.decide(table, user);
        return apply(table, userId, op) == ConstProto.Result.SUCCESS_VALUE;
    }

    /**
     * 若当前玩家的全部余牌本身就是一手合法牌并且能接上桌面牌，则整手自动打出。
     * 规则判断集中在服务端，真人、机器人和不同前端保持一致。
     */
    public static boolean autoPlayWholeHand(DdzTable table, int seat) {
        TableUser user = table.getSeatUser(seat);
        if (user == null || !canPlayWholeHand(user.getCards(), table.getDdz().getLastHand())) return false;
        List<Integer> ids = new ArrayList<>();
        GameProto.CardInfo.Builder cards = GameProto.CardInfo.newBuilder();
        for (Card card : user.getCards()) {
            ids.add(card.getId());
            cards.addCards(GameProto.Card.newBuilder().setValue(card.getId()));
        }
        ReplayRecorder replay = table.getReplayRecorder();
        if (replay instanceof DdzReplayRecorder) {
            ((DdzReplayRecorder) replay).recordAutoPlay(seat, ids);
        }
        logger.info("斗地主最后一手自动打出, tableId:{}, seat:{}, cards:{}", table.getTableId(), seat, ids);
        GameProto.OpInfo op = GameProto.OpInfo.newBuilder()
                .setChoice(ConstProto.Operation.PLAY).addOpCards(cards).build();
        return apply(table, user.getUserId(), op) == ConstProto.Result.SUCCESS_VALUE;
    }

    static boolean canPlayWholeHand(List<Card> cards, DdzHand lastHand) {
        Optional<DdzHand> whole = DdzRules.analyze(cards);
        return whole.isPresent() && (lastHand == null || DdzRules.beats(whole.get(), lastHand));
    }

    /**
     * 应用操作（出牌/过牌）
     */
    public static int apply(DdzTable table, int userId, GameProto.OpInfo opInfo) {
        if (table.getTableState() != TableState.IDLE_CARD) return ConstProto.Result.OP_CURR_ERROR_VALUE;
        TableUser user = table.getUsers().get(userId);
        if (user == null || user.getSeated() != table.getOp().getCurrOpSeat())
            return ConstProto.Result.OP_CURR_ERROR_VALUE;

        ConstProto.Operation choice = opInfo.getChoice();
        if (choice == ConstProto.Operation.PASS) return applyPass(table, userId);
        if (choice == ConstProto.Operation.PLAY) return applyPlay(table, user, opInfo);
        return ConstProto.Result.OP_CURR_ERROR_VALUE;
    }

    // ======================== 内部方法 ========================

    /**
     * 应用过牌
     */
    private static int applyPass(DdzTable table, int userId) {
        DdzTableContext ctx = table.getDdz();
        if (ctx.getLastHand() == null) return ConstProto.Result.OP_CURR_ERROR_VALUE;

        TableUser user = table.getUsers().get(userId);
        ReplayRecorder replay = table.getReplayRecorder();
        if (replay instanceof DdzReplayRecorder && user != null) {
            replay.writeAuditEvent("座" + user.getSeated() + " 收到选项 出牌/过 → 客户端展示 出牌/过");
            replay.writeAuditEvent("座" + user.getSeated() + " " + source(user) + "选择 过");
            ((DdzReplayRecorder) replay).recordPass(user.getSeated());
        }

        broadcastAck(table, userId, GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PASS).build());
        ctx.addPass();
        if (ctx.getConsecutivePasses() >= 2) {
            int leader = ctx.getLastPlaySeat();
            ctx.resetCurrentTrickCards();
            table.getOp().setCurrOpSeat(leader);
        } else {
            table.getOp().moveToNextOp();
        }
        if (replay != null) replay.writeAuditEvent("下一操作位 座" + table.getOp().getCurrOpSeat());
        table.getBanner().setRobBroadcastDone(false);
        table.upNextStateWithTime(TableState.CARD, System.currentTimeMillis());
        return ConstProto.Result.SUCCESS_VALUE;
    }

    /**
     * 应用出牌
     */
    private static int applyPlay(DdzTable table, TableUser user, GameProto.OpInfo opInfo) {
        DdzTableContext ctx = table.getDdz();
        List<Integer> ids = CardOps.collectIds(opInfo);
        if (ids.isEmpty()) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;

        List<Card> selected = CardOps.pullFromHand(user, ids);
        if (selected == null) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;

        Optional<DdzHand> parsed = DdzRules.analyze(selected);
        if (!parsed.isPresent()) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;

        DdzHand hand = parsed.get();
        if (ctx.getLastHand() == null) {
            if (!user.removeCardsByProtoIds(ids)) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
            afterSuccessfulPlay(table, user, hand);
            return ConstProto.Result.SUCCESS_VALUE;
        }
        if (!DdzRules.beats(hand, ctx.getLastHand())) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
        if (!user.removeCardsByProtoIds(ids)) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
        afterSuccessfulPlay(table, user, hand);
        return ConstProto.Result.SUCCESS_VALUE;
    }

    /**
     * 成功出牌后处理
     */
    private static void afterSuccessfulPlay(DdzTable table, TableUser user, DdzHand hand) {
        DdzTableContext ctx = table.getDdz();
        if (hand.isBomb() || hand.isRocket()) {
            ctx.doubleBombMultiplier();
        }
        int landlordSeat = ctx.getLandlordSeat();
        if (user.getSeated() == landlordSeat) {
            ctx.incrementLandlordPlayCount();
        } else {
            ctx.setFarmerEverPlayed(true);
        }
        ctx.recordPlayedCards(hand.getCards());

        ReplayRecorder replay = table.getReplayRecorder();
        if (replay instanceof DdzReplayRecorder) {
            List<Integer> ids = new ArrayList<>();
            for (Card c : hand.getCards()) ids.add(c.getId());
            replay.writeAuditEvent("座" + user.getSeated() + " 收到选项 "
                    + (ctx.getLastHand() == null ? "出牌" : "出牌/过") + " → 客户端展示");
            replay.writeAuditEvent("座" + user.getSeated() + " " + source(user) + "选择 出牌 " + ids);
            replay.writeAuditEvent("座" + user.getSeated() + " 牌型 " + hand.getType().name()
                    + " 强度 " + hand.getStrengthKey() + " 长度 " + hand.getCards().size()
                    + (hand.isRocket() ? " 王炸" : (hand.isBomb() ? " 炸弹" : "")));
            ((DdzReplayRecorder) replay).recordPlay(user.getSeated(), ids);
        }

        broadcastAck(table, user.getUserId(), GameProto.OpInfo.newBuilder()
                .setChoice(ConstProto.Operation.PLAY)
                .addOpCards(hand.toCardInfo()).build());
        ctx.setLastHand(hand);
        ctx.setLastPlayed(hand.toCardInfo());
        ctx.setConsecutivePasses(0);
        ctx.setLastPlaySeat(user.getSeated());
        if (replay != null) replay.writeAuditEvent("当前最大方 座" + user.getSeated());

        if (user.getCards().isEmpty()) {
            DdzSettleService.finishGame(table, user);
            return;
        }
        table.getOp().moveToNextOp();
        if (replay != null) replay.writeAuditEvent("下一操作位 座" + table.getOp().getCurrOpSeat());
        table.getBanner().setRobBroadcastDone(false);
        table.upNextStateWithTime(TableState.CARD, System.currentTimeMillis());
    }

    private static String source(TableUser user) {
        return user.isRobot() ? "机器人" : "玩家";
    }


    /**
     * 广播确认
     */
    private static void broadcastAck(DdzTable table, int actorUserId, GameProto.OpInfo op) {
        DdzTableContext ctx = table.getDdz();
        GameProto.AckOp msg = GameProto.AckOp.newBuilder()
                .setOp(op).setOpId(actorUserId).setOpFrom(actorUserId)
                .setBaseScore(ctx.getBaseScore()).setRobMultiplier(ctx.getRobMultiplier())
                .setBombMultiplier(ctx.getBombMultiplier())
                .setCurrentMultiplier(ctx.getCurrentMultiplier()).build();
        table.sendTableMessage(msg, GMsg.ACK_OP);
    }
}
