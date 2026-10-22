package com.cloud.hub.game.domain.poker.niuniu;

import com.cloud.hub.game.domain.table.TableUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 牛牛核心流程流转与结算（抢庄、下注、拼牛摊牌、比牌结算）。
 *
 * @author cloud
 */
public final class NiuniuFlow {

    private static final Logger logger = LoggerFactory.getLogger(NiuniuFlow.class);

    private NiuniuFlow() {
    }

    // ======================== 抢庄阶段 ========================

    public static boolean onRob(NiuniuTable table, int userId, int multiplier) {
        TableUser user = table.getUserByUserId(userId);
        if (user == null || user.getSeated() < 0) {
            return false;
        }
        int seat = user.getSeated();
        NiuniuContext ctx = table.getContext();
        ctx.getRobMultipliers().put(seat, Math.max(0, Math.min(multiplier, 4)));

        table.broadcastRob(userId, seat, ctx.getRobMultipliers().get(seat));

        if (ctx.getRobMultipliers().keySet().containsAll(table.getSeatUsers().keySet())) {
            electBanker(table);
        }
        return true;
    }

    public static void onRobTimeout(NiuniuTable table) {
        NiuniuContext ctx = table.getContext();
        for (int seat : table.getSeatUsers().keySet()) {
            ctx.getRobMultipliers().putIfAbsent(seat, 0);
        }
        electBanker(table);
    }

    public static void electBanker(NiuniuTable table) {
        NiuniuContext ctx = table.getContext();
        if (ctx.getBankerSeat() >= 0) {
            return;
        }

        Map<Integer, Integer> robs = ctx.getRobMultipliers();
        int maxRob = 0;
        for (int m : robs.values()) {
            if (m > maxRob) {
                maxRob = m;
            }
        }

        List<Integer> candidates = new ArrayList<>();
        if (maxRob > 0) {
            for (Map.Entry<Integer, Integer> e : robs.entrySet()) {
                if (e.getValue() == maxRob) {
                    candidates.add(e.getKey());
                }
            }
        } else {
            candidates.addAll(table.getSeatUsers().keySet());
            maxRob = 1;
        }

        int luckySeat = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        ctx.setBankerSeat(luckySeat);
        ctx.setBankerMultiplier(maxRob);

        logger.info("牛牛定庄完成! bankerSeat: {}, mult: {}, tableId: {}",
                luckySeat, maxRob, table.getTableId());
        table.onBankerDetermined();
    }

    // ======================== 闲家下注阶段 ========================

    public static boolean onBet(NiuniuTable table, int userId, int multiplier) {
        TableUser user = table.getUserByUserId(userId);
        if (user == null || user.getSeated() < 0) {
            return false;
        }
        int seat = user.getSeated();
        NiuniuContext ctx = table.getContext();
        if (seat == ctx.getBankerSeat()) {
            return false;
        }

        ctx.getBetMultipliers().put(seat, Math.max(1, Math.min(multiplier, 5)));
        table.broadcastBet(userId, seat, ctx.getBetMultipliers().get(seat));

        if (isAllBet(table)) {
            table.onBetCompleted();
        }
        return true;
    }

    public static void onBetTimeout(NiuniuTable table) {
        NiuniuContext ctx = table.getContext();
        int bankerSeat = ctx.getBankerSeat();
        for (int seat : table.getSeatUsers().keySet()) {
            if (seat != bankerSeat) {
                ctx.getBetMultipliers().putIfAbsent(seat, 1);
            }
        }
        table.onBetCompleted();
    }

    private static boolean isAllBet(NiuniuTable table) {
        NiuniuContext ctx = table.getContext();
        int bankerSeat = ctx.getBankerSeat();
        for (int seat : table.getSeatUsers().keySet()) {
            if (seat != bankerSeat && !ctx.getBetMultipliers().containsKey(seat)) {
                return false;
            }
        }
        return true;
    }

    // ======================== 拼牛摊牌阶段 ========================

    public static boolean onShow(NiuniuTable table, int userId) {
        TableUser user = table.getUserByUserId(userId);
        if (user == null || user.getSeated() < 0) {
            return false;
        }
        int seat = user.getSeated();
        NiuniuContext ctx = table.getContext();
        if (ctx.getShowedSeats().contains(seat)) {
            return true;
        }

        recordHand(table, user);
        ctx.getShowedSeats().add(seat);
        table.notifyPlayerShowed(seat);

        if (ctx.getShowedSeats().containsAll(table.getSeatUsers().keySet())) {
            table.onShowCompleted();
        }
        return true;
    }

    public static void onShowTimeout(NiuniuTable table) {
        NiuniuContext ctx = table.getContext();
        for (TableUser user : table.getSeatUsers().values()) {
            int seat = user.getSeated();
            if (!ctx.getShowedSeats().contains(seat)) {
                recordHand(table, user);
                ctx.getShowedSeats().add(seat);
                table.notifyPlayerShowed(seat);
            }
        }
        table.onShowCompleted();
    }

    private static void recordHand(NiuniuTable table, TableUser user) {
        NiuniuHand hand = NiuniuRules.evaluate(user.getCards(), table.getRuleConfig());
        table.getContext().getPlayerHands().put(user.getSeated(), hand);
    }

    // ======================== 庄闲结算 ========================

    public static void settle(NiuniuTable table) {
        NiuniuContext ctx = table.getContext();
        int bankerSeat = ctx.getBankerSeat();
        NiuniuHand bankerHand = ctx.getPlayerHands().get(bankerSeat);
        if (bankerSeat < 0 || bankerHand == null) {
            logger.error("牛牛结算异常: 未找到合法庄家手牌, tableId: {}", table.getTableId());
            return;
        }

        NiuniuConfig config = table.getRuleConfig();
        int baseScore = Math.max(1, table.getTableModel().getBaseScore());
        int bankerMultiplier = Math.max(1, ctx.getBankerMultiplier());

        Map<Integer, Integer> roundScores = new HashMap<>();
        int bankerTotalDelta = 0;
        int maxSeatNum = table.getTableModel().getSeatNum();
        int[] scoresArray = new int[maxSeatNum];

        for (TableUser user : table.getSeatUsers().values()) {
            int seat = user.getSeated();
            if (seat == bankerSeat) {
                continue;
            }

            NiuniuHand playerHand = ctx.getPlayerHands().get(seat);
            if (playerHand == null) {
                continue;
            }

            boolean isPlayerWin = NiuniuRules.isPlayerWin(playerHand, bankerHand);
            NiuniuHand winHand = isPlayerWin ? playerHand : bankerHand;
            int typeMultiplier = config.getMultiplier(winHand.getCardType());
            int betMultiplier = Math.max(1, ctx.getBetMultipliers().getOrDefault(seat, 1));
            int lineMultiplier = typeMultiplier * betMultiplier * bankerMultiplier;
            int delta = baseScore * lineMultiplier;

            if (isPlayerWin) {
                roundScores.put(seat, delta);
                bankerTotalDelta -= delta;
            } else {
                roundScores.put(seat, -delta);
                bankerTotalDelta += delta;
            }
        }

        roundScores.put(bankerSeat, bankerTotalDelta);
        ctx.getRoundScoreDeltas().putAll(roundScores);

        for (Map.Entry<Integer, Integer> entry : roundScores.entrySet()) {
            int s = entry.getKey();
            if (s >= 0 && s < maxSeatNum) {
                scoresArray[s] = entry.getValue();
            }
        }

        int winnerSeat = bankerTotalDelta >= 0 ? bankerSeat : -1;
        table.getGameResult().addRound(table.getCurrentRound(), winnerSeat, 1,
                scoresArray, bankerHand.getCardType().getName());

        table.broadcastSettle(roundScores);
    }

    // ======================== 通用协议操作转换（抽出公共重复） ========================

    public static ConstProto.Operation robOp(int multiplier) {
        switch (multiplier) {
            case 1: return ConstProto.Operation.NIU_ROB_1;
            case 2: return ConstProto.Operation.NIU_ROB_2;
            case 3: return ConstProto.Operation.NIU_ROB_3;
            case 4: return ConstProto.Operation.NIU_ROB_4;
            default: return ConstProto.Operation.NIU_NO_ROB;
        }
    }

    public static int parseRobMult(GameProto.OpInfo op) {
        switch (op.getChoice()) {
            case NIU_ROB_1: return 1;
            case NIU_ROB_2: return 2;
            case NIU_ROB_3: return 3;
            case NIU_ROB_4: return 4;
            case NIU_NO_ROB: return 0;
            default: return Math.max(0, Math.min(op.getChoiceValue(), 4));
        }
    }

    public static ConstProto.Operation betOp(int multiplier) {
        switch (multiplier) {
            case 2: return ConstProto.Operation.NIU_BET_2;
            case 3: return ConstProto.Operation.NIU_BET_3;
            case 4: return ConstProto.Operation.NIU_BET_4;
            case 5: return ConstProto.Operation.NIU_BET_5;
            default: return ConstProto.Operation.NIU_BET_1;
        }
    }

    public static int parseBetMult(GameProto.OpInfo op) {
        switch (op.getChoice()) {
            case NIU_BET_2: return 2;
            case NIU_BET_3: return 3;
            case NIU_BET_4: return 4;
            case NIU_BET_5: return 5;
            case NIU_BET_1: return 1;
            default: return Math.max(1, Math.min(op.getChoiceValue() > 0 ? op.getChoiceValue() : 1, 5));
        }
    }
}
