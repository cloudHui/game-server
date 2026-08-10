package game.manager.table.pdk;

import game.manager.table.TableUser;
import game.manager.table.cards.Card;
import game.manager.table.cards.CardOps;
import game.manager.table.ddz.DdzHand;
import game.manager.table.pdk.ai.PdkSimpleAi;
import game.manager.table.replay.PokerReplayRecorder;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 跑得快出牌 / 过牌 / 自动出牌
 */
public final class PdkPlayService {

    private static final Logger logger = LoggerFactory.getLogger(PdkPlayService.class);

    private PdkPlayService() {
    }

    public static boolean autoPlayAi(PdkTable table, int userId) {
        TableUser user = table.getUsers().get(userId);
        if (user == null) return false;
        GameProto.OpInfo op = PdkSimpleAi.decide(table, user);
        return apply(table, userId, op) == ConstProto.Result.SUCCESS_VALUE;
    }

    public static void autoPlaySmallest(PdkTable table, int userId) {
        TableUser user = table.getUsers().get(userId);
        if (user == null || user.getCards().isEmpty()) return;
        Card smallest = Collections.min(user.getCards());
        GameProto.OpInfo op = GameProto.OpInfo.newBuilder()
                .setChoice(ConstProto.Operation.PLAY)
                .addOpCards(GameProto.CardInfo.newBuilder()
                        .addCards(GameProto.Card.newBuilder().setValue(smallest.getId()).build())
                        .build())
                .build();
        apply(table, userId, op);
    }

    public static int apply(PdkTable table, int userId, GameProto.OpInfo opInfo) {
        if (table.getTableState() != TableState.IDLE_CARD) return ConstProto.Result.OP_CURR_ERROR_VALUE;
        TableUser user = table.getUsers().get(userId);
        if (user == null || user.getSeated() != table.getOp().getCurrOpSeat()) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        ConstProto.Operation choice = opInfo.getChoice();
        if (choice == ConstProto.Operation.PASS) return applyPass(table, userId);
        if (choice == ConstProto.Operation.PLAY) {
            // 管不上时不下发出牌选项，客户端误发也直接拒绝
            if (table.canCurrentPlayerPass()) return ConstProto.Result.OP_CURR_ERROR_VALUE;
            return applyPlay(table, user, opInfo);
        }
        return ConstProto.Result.OP_CURR_ERROR_VALUE;
    }

    private static int applyPass(PdkTable table, int userId) {
        PdkTableContext ctx = table.getPdk();
        if (ctx.getLastHand() == null) return ConstProto.Result.OP_CURR_ERROR_VALUE;
        TableUser user = table.getUsers().get(userId);
        if (user == null) return ConstProto.Result.OP_CURR_ERROR_VALUE;
        if (PdkRules.canBeat(user.getCards(), ctx.getLastHand())) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        broadcastAck(table, userId, GameProto.OpInfo.newBuilder().setChoice(ConstProto.Operation.PASS).build());
        if (table.getReplayRecorder() instanceof PokerReplayRecorder) {
            PokerReplayRecorder replay = (PokerReplayRecorder) table.getReplayRecorder();
            replay.writeAuditEvent("座" + user.getSeated() + " 收到选项 过 → 客户端展示 过");
            replay.writeAuditEvent("座" + user.getSeated() + " " + source(user) + "选择 过");
            replay.recordPass(user.getSeated());
        }
        ctx.addPass();
        ctx.addPassSeat(user.getSeated());
        int needPass = table.getTableModel().getSeatNum() - 1;
        if (ctx.getConsecutivePasses() >= needPass) {
            int leader = ctx.getLastPlaySeat();
            ctx.resetCurrentTrick();
            table.getOp().setCurrOpSeat(leader);
        } else {
            table.getOp().moveToNextOp();
        }
        if (table.getReplayRecorder() != null) table.getReplayRecorder().writeAuditEvent("下一操作位 座" + table.getOp().getCurrOpSeat());
        table.upNextStateWithTime(TableState.CARD, System.currentTimeMillis());
        return ConstProto.Result.SUCCESS_VALUE;
    }

    private static int applyPlay(PdkTable table, TableUser user, GameProto.OpInfo opInfo) {
        PdkTableContext ctx = table.getPdk();
        List<Integer> ids = CardOps.collectIds(opInfo);
        if (ids.isEmpty()) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
        List<Card> selected = CardOps.pullFromHand(user, ids);
        if (selected == null) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
        Optional<DdzHand> parsed = PdkRules.analyze(selected);
        if (!parsed.isPresent()) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
        DdzHand hand = parsed.get();
        if (ctx.getLastHand() != null && !PdkRules.beats(hand, ctx.getLastHand())) {
            return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;
        }
        if (!user.removeCardsByProtoIds(ids)) return ConstProto.Result.OP_CARD_NOT_MATCH_VALUE;

        ctx.markPlayed(user.getSeated());
        if (table.getReplayRecorder() instanceof PokerReplayRecorder) {
            PokerReplayRecorder replay = (PokerReplayRecorder) table.getReplayRecorder();
            replay.writeAuditEvent("座" + user.getSeated() + " 收到选项 出牌 → 客户端展示 出牌");
            replay.writeAuditEvent("座" + user.getSeated() + " " + source(user) + "选择 出牌 " + ids);
            replay.writeAuditEvent("座" + user.getSeated() + " 牌型 " + hand.getType().name()
                    + " 强度 " + hand.getStrengthKey() + " 长度 " + hand.getCards().size());
            replay.recordPlay(user.getSeated(), ids);
        }
        broadcastAck(table, user.getUserId(), GameProto.OpInfo.newBuilder()
                .setChoice(ConstProto.Operation.PLAY)
                .addOpCards(hand.toCardInfo()).build());
        ctx.setLastHand(hand);
        ctx.setLastPlayed(hand.toCardInfo());
        ctx.setConsecutivePasses(0);
        ctx.setLastPlaySeat(user.getSeated());
        if (table.getReplayRecorder() != null) {
            table.getReplayRecorder().writeAuditEvent("当前最大方 座" + user.getSeated());
        }

        if (user.getCards().isEmpty()) {
            ctx.recordFinish(user.getSeated());
            PdkSettleService.finishGame(table, user);
            return ConstProto.Result.SUCCESS_VALUE;
        }
        table.getOp().moveToNextOp();
        if (table.getReplayRecorder() != null) table.getReplayRecorder().writeAuditEvent("下一操作位 座" + table.getOp().getCurrOpSeat());
        table.upNextStateWithTime(TableState.CARD, System.currentTimeMillis());
        return ConstProto.Result.SUCCESS_VALUE;
    }

    private static String source(TableUser user) {
        return user.isRobot() ? "机器人" : "玩家";
    }


    private static void broadcastAck(PdkTable table, int actorUserId, GameProto.OpInfo op) {
        GameProto.AckOp msg = GameProto.AckOp.newBuilder()
                .setOp(op).setOpId(actorUserId).setOpFrom(actorUserId)
                .setBaseScore(1).setRobMultiplier(1).setBombMultiplier(1).setCurrentMultiplier(1)
                .build();
        table.sendTableMessage(msg, GMsg.ACK_OP);
        logger.debug("pdk ackOp table:{} from:{}", table.getTableId(), actorUserId);
    }
}
