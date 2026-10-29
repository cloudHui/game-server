package game.domain.poker.niuniu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import game.runtime.HubMetrics;
import game.db.ScoreRepository;
import game.domain.cards.Card;
import game.domain.replay.ReplayRecorder;
import game.domain.table.TableUser;

import proto.ConstProto;
import proto.GameProto;

/**
 * 牛牛核心阶段流转与结算服务（明牌抢庄、闲家加倍、拼牛摊牌、庄闲零和比牌结算）。
 */
public final class NiuniuFlow {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(NiuniuFlow.class);

    /** 抢庄倍数（0~4）对应的协议操作码表 */
    private static final ConstProto.Operation[] ROB_OPS = {
            ConstProto.Operation.NIU_NO_ROB,
            ConstProto.Operation.NIU_ROB_1,
            ConstProto.Operation.NIU_ROB_2,
            ConstProto.Operation.NIU_ROB_3,
            ConstProto.Operation.NIU_ROB_4
    };

    /** 闲家下注倍数（1~5，下标 0 对应 1 倍）对应的协议操作码表 */
    private static final ConstProto.Operation[] BET_OPS = {
            ConstProto.Operation.NIU_BET_1,
            ConstProto.Operation.NIU_BET_2,
            ConstProto.Operation.NIU_BET_3,
            ConstProto.Operation.NIU_BET_4,
            ConstProto.Operation.NIU_BET_5
    };

    private NiuniuFlow() {
    }

    /**
     * 处理玩家提交抢庄倍数操作。
     *
     * @param table      牛牛牌桌实例
     * @param userId     操作玩家 ID
     * @param multiplier 抢庄倍数（0 不抢，1~4 倍）
     * @return true 表示操作有效并已受理
     */
    public static boolean onRob(NiuniuTable table, int userId, int multiplier) {
        TableUser user = table.getUserByUserId(userId);
        NiuniuContext ctx = table.getContext();
        // 仅在抢庄阶段且该座位首次表态时受理，防客户端连点与超时并发重入
        if (user == null || user.getSeated() < 0
                || ctx.getStage() != NiuniuContext.STAGE_ROB
                || ctx.getRobMultipliers().containsKey(user.getSeated())) {
            return false;
        }
        int seat = user.getSeated();
        int finalMult = clamp(multiplier, 0, 4);
        ctx.getRobMultipliers().put(seat, finalMult);
        audit(table, "座" + seat + (finalMult > 0 ? " 抢庄 ×" + finalMult : " 不抢"));
        table.broadcastRob(userId, finalMult);
        if (ctx.getRobMultipliers().keySet().containsAll(table.getSeatUsers().keySet())) {
            electBanker(table);
        }
        return true;
    }

    /**
     * 抢庄超时处理：未表态座位默认按“不抢(0倍)”补齐并定庄。
     *
     * @param table 牛牛牌桌实例
     */
    public static void onRobTimeout(NiuniuTable table) {
        NiuniuContext ctx = table.getContext();
        if (ctx.getStage() != NiuniuContext.STAGE_ROB) {
            return;
        }
        for (Map.Entry<Integer, TableUser> e : table.getSeatUsers().entrySet()) {
            int seat = e.getKey();
            if (!ctx.getRobMultipliers().containsKey(seat)) {
                ctx.getRobMultipliers().put(seat, 0);
                audit(table, "座" + seat + " 超时不抢");
                table.broadcastRob(e.getValue().getUserId(), 0);
            }
        }
        electBanker(table);
    }

    /**
     * 根据全桌抢庄结果选举本局庄家（最高倍当选，同最高倍随机，全员不抢随机定庄且按 1 倍计）。
     *
     * @param table 牛牛牌桌实例
     */
    public static void electBanker(NiuniuTable table) {
        NiuniuContext ctx = table.getContext();
        if (ctx.getBankerSeat() >= 0) {
            return;
        }
        Map<Integer, Integer> robs = ctx.getRobMultipliers();
        int maxRob = 0;
        for (int m : robs.values()) {
            maxRob = Math.max(maxRob, m);
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
        if (candidates.isEmpty()) {
            return;
        }
        int bankerSeat = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        ctx.setBankerSeat(bankerSeat);
        ctx.setBankerMultiplier(maxRob);
        audit(table, "定庄 座" + bankerSeat + " (×" + maxRob + ")");
        table.onBankerDetermined();
    }

    /**
     * 处理闲家提交下注倍数操作。
     *
     * @param table      牛牛牌桌实例
     * @param userId     操作玩家 ID
     * @param multiplier 下注倍数（1~5 倍）
     * @return true 表示下注成功
     */
    public static boolean onBet(NiuniuTable table, int userId, int multiplier) {
        TableUser user = table.getUserByUserId(userId);
        NiuniuContext ctx = table.getContext();
        int seat = user != null ? user.getSeated() : -1;
        // 庄家不可参与闲家下注，且每位闲家单局仅允许锁定一次下注倍数
        if (seat < 0 || ctx.getStage() != NiuniuContext.STAGE_BET
                || seat == ctx.getBankerSeat() || ctx.getBetMultipliers().containsKey(seat)) {
            return false;
        }
        int finalMult = clamp(multiplier, 1, 5);
        ctx.getBetMultipliers().put(seat, finalMult);
        audit(table, "座" + seat + " 下注 ×" + finalMult);
        table.broadcastBet(userId, finalMult);
        if (isAllBet(table)) {
            table.onBetCompleted();
        }
        return true;
    }

    /**
     * 闲家下注超时处理：未下注闲家默认补齐 1 倍并进入发第 5 张牌流程。
     *
     * @param table 牛牛牌桌实例
     */
    public static void onBetTimeout(NiuniuTable table) {
        NiuniuContext ctx = table.getContext();
        if (ctx.getStage() != NiuniuContext.STAGE_BET) {
            return;
        }
        int bankerSeat = ctx.getBankerSeat();
        for (Map.Entry<Integer, TableUser> e : table.getSeatUsers().entrySet()) {
            int seat = e.getKey();
            if (seat != bankerSeat && !ctx.getBetMultipliers().containsKey(seat)) {
                ctx.getBetMultipliers().put(seat, 1);
                audit(table, "座" + seat + " 超时下注 ×1");
                table.broadcastBet(e.getValue().getUserId(), 1);
            }
        }
        table.onBetCompleted();
    }

    /**
     * 处理玩家拼牛摊牌操作，全员摊牌后立即触发比牌结算。
     *
     * @param table  牛牛牌桌实例
     * @param userId 操作玩家 ID
     * @return true 表示摊牌已受理
     */
    public static boolean onShow(NiuniuTable table, int userId) {
        TableUser user = table.getUserByUserId(userId);
        NiuniuContext ctx = table.getContext();
        if (user == null || user.getSeated() < 0 || ctx.getStage() != NiuniuContext.STAGE_SHOW) {
            return false;
        }
        int seat = user.getSeated();
        if (ctx.getShowedSeats().add(seat)) {
            recordHand(table, user);
            table.notifyPlayerShowed(seat);
            if (ctx.getShowedSeats().containsAll(table.getSeatUsers().keySet())) {
                table.onShowCompleted();
            }
        }
        return true;
    }

    /**
     * 拼牛摊牌超时处理：对未摊牌座位自动算牛亮牌并触发结算。
     *
     * @param table 牛牛牌桌实例
     */
    public static void onShowTimeout(NiuniuTable table) {
        NiuniuContext ctx = table.getContext();
        if (ctx.getStage() != NiuniuContext.STAGE_SHOW) {
            return;
        }
        for (TableUser user : table.getSeatUsers().values()) {
            int seat = user.getSeated();
            if (ctx.getShowedSeats().add(seat)) {
                recordHand(table, user);
                table.notifyPlayerShowed(seat);
            }
        }
        table.onShowCompleted();
    }

    /**
     * 执行单局庄闲逐一比牌零和结算，落盘战绩与回放并广播结算详情。
     *
     * @param table 牛牛牌桌实例
     */
    public static void settle(NiuniuTable table) {
        NiuniuContext ctx = table.getContext();
        int bankerSeat = ctx.getBankerSeat();
        NiuniuHand bankerHand = ctx.getPlayerHands().get(bankerSeat);
        if (bankerSeat < 0 || bankerHand == null) {
            logger.error("牛牛结算异常: 未找到庄家手牌, tableId: {}", table.getTableId());
            return;
        }

        int baseScore = Math.max(1, table.getTableModel().getBaseScore());
        int bankerMult = Math.max(1, ctx.getBankerMultiplier());
        int maxFan = table.getTableModel().getMaxFan();
        int maxSeatNum = table.getTableModel().getSeatNum();

        Map<Integer, Integer> roundScores = new HashMap<>();
        int[] scoresArray = new int[maxSeatNum];
        int bankerDelta = 0;

        for (TableUser user : table.getSeatUsers().values()) {
            int seat = user.getSeated();
            NiuniuHand playerHand = ctx.getPlayerHands().get(seat);
            if (seat == bankerSeat || playerHand == null) {
                continue;
            }
            boolean playerWin = NiuniuRules.isPlayerWin(playerHand, bankerHand);
            int typeMult = (playerWin ? playerHand : bankerHand).getCardType().getDefaultMultiplier();
            int betMult = Math.max(1, ctx.getBetMultipliers().getOrDefault(seat, 1));
            int lineMult = typeMult * betMult * bankerMult;
            // 应用房间 maxFan 倍数保护上限，防止特殊牌型叠加高倍抢庄产生极端分差
            if (maxFan > 0) {
                lineMult = Math.min(lineMult, maxFan * 5);
            }
            int signed = (playerWin ? 1 : -1) * baseScore * lineMult;
            roundScores.put(seat, signed);
            bankerDelta -= signed;
        }
        roundScores.put(bankerSeat, bankerDelta);

        int bestSeat = bankerSeat;
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<Integer, Integer> e : roundScores.entrySet()) {
            int s = e.getKey();
            int val = e.getValue();
            if (s >= 0 && s < maxSeatNum) {
                scoresArray[s] = val;
            }
            if (val > bestScore) {
                bestScore = val;
                bestSeat = s;
            }
        }

        String summaryType = bankerHand.getCardType().getName();
        table.getGameResult().addRound(table.getCurrentRound(), bestSeat, bankerMult, scoresArray, summaryType);
        ScoreRepository.getInstance().saveRound(table);

        HubMetrics metrics = HubMetrics.getInstance();
        if (metrics != null) {
            metrics.recordRoundSettled("niuniu");
        }

        ReplayRecorder replay = table.getReplayRecorder();
        if (replay != null) {
            replay.writeAuditEvent("结算 庄家座" + bankerSeat + " 庄家牌型 " + summaryType + " 各座得分 " + Arrays.toString(scoresArray));
            replay.writeSettlement(bestSeat, bankerMult, summaryType, scoresArray);
            replay.save();
        }
        table.broadcastSettle(roundScores);
    }

    /**
     * 将抢庄倍数（0~4）转换为协议操作枚举。
     *
     * @param multiplier 抢庄倍数
     * @return 协议操作枚举
     */
    public static ConstProto.Operation robOp(int multiplier) {
        return ROB_OPS[clamp(multiplier, 0, 4)];
    }

    /**
     * 从客户端操作消息解析抢庄倍数（0~4）。
     *
     * @param op 客户端操作详情
     * @return 抢庄倍数
     */
    public static int parseRobMult(GameProto.OpInfo op) {
        for (int i = 0; i < ROB_OPS.length; i++) {
            if (ROB_OPS[i] == op.getChoice()) {
                return i;
            }
        }
        return clamp(op.getChoiceValue(), 0, 4);
    }

    /**
     * 将闲家下注倍数（1~5）转换为协议操作枚举。
     *
     * @param multiplier 下注倍数
     * @return 协议操作枚举
     */
    public static ConstProto.Operation betOp(int multiplier) {
        return BET_OPS[clamp(multiplier, 1, 5) - 1];
    }

    /**
     * 从客户端操作消息解析闲家下注倍数（1~5）。
     *
     * @param op 客户端操作详情
     * @return 下注倍数
     */
    public static int parseBetMult(GameProto.OpInfo op) {
        for (int i = 0; i < BET_OPS.length; i++) {
            if (BET_OPS[i] == op.getChoice()) {
                return i + 1;
            }
        }
        return clamp(op.getChoiceValue(), 1, 5);
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

    private static void recordHand(NiuniuTable table, TableUser user) {
        while (user.getCards().size() < 5 && !table.getCardPool().getPoolCards().isEmpty()) {
            user.addCards(table.getCardPool().getPoolCards().remove(0));
        }
        NiuniuHand hand = NiuniuRules.evaluate(user.getCards());
        int seat = user.getSeated();
        table.getContext().getPlayerHands().put(seat, hand);
        List<Integer> ids = new ArrayList<>(user.getCards().size());
        for (Card c : user.getCards()) {
            ids.add(c.getId());
        }
        audit(table, "座" + seat + " 亮牌 " + ids + " " + hand.getCardType().getName());
    }

    private static void audit(NiuniuTable table, String event) {
        ReplayRecorder replay = table.getReplayRecorder();
        if (replay != null) {
            replay.writeAuditEvent(event);
        }
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(val, max));
    }
}
