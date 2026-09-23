package com.cloud.hub.game.domain.ddz;

import com.cloud.hub.game.domain.table.TableUser;
import com.cloud.hub.game.domain.banner.Banner;
import com.cloud.hub.game.domain.replay.DdzReplayRecorder;
import utils.registry.enums.TableState;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 叫分 1/2/3（每人一轮）→ 两名农民各一轮抢地主（每次「抢」倍数×2），再确定地主并入桌出牌。
 *
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @since 1.0
 */
public final class DdzBidService {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(DdzBidService.class);

    private DdzBidService() {
    }

    /**
     * 叫分或抢地主操作超时时的自动托管兜底处理：
     * 处于叫分阶段自动视为「不叫」，处于抢地主阶段自动视为「不抢」。
     *
     * @param table 斗地主牌桌实例
     */
    public static void onBidTimeout(DdzTable table) {
        int seat = table.getOp().getCurrOpSeat();
        TableUser u = table.getSeatUser(seat);
        if (u == null) {
            return;
        }
        Banner banner = table.getBanner();
        logger.info("叫分超时自动处理, tableId: {}, seat: {}, robPhase: {}",
                table.getTableId(), seat, banner.isRobPhase());
        if (!banner.isRobPhase()) {
            apply(table, u.getUserId(), GameProto.OpInfo.newBuilder()
                    .setChoice(ConstProto.Operation.NOT_CALL).build());
        } else {
            apply(table, u.getUserId(), GameProto.OpInfo.newBuilder()
                    .setChoice(ConstProto.Operation.NOT_ROB).build());
        }
    }

    /**
     * 处理客户端提交的叫分/抢地主操作。
     *
     * @param table  斗地主牌桌实例
     * @param userId 操作玩家用户 ID
     * @param opInfo 操作信息载荷（包含 choiceValue）
     * @return 错误码（0 表示成功，非 0 为 {@link ConstProto.Result} 错误码）
     */
    public static int apply(DdzTable table, int userId, GameProto.OpInfo opInfo) {
        if (table.getTableState() != TableState.IDLE_ROB) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        TableUser user = table.getUsers().get(userId);
        if (user == null || user.getSeated() != table.getOp().getCurrOpSeat()) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        Banner banner = table.getBanner();
        // 根据当前横幅是否处于抢地主阶段，分流至叫分或抢地主分支
        if (!banner.isRobPhase()) {
            return applyCall(table, userId, opInfo, user, banner);
        }
        return applyRob(table, userId, opInfo, user, banner);
    }

    /**
     * 执行叫分阶段（第一阶段）的操作判定与状态机转移。
     *
     * @param table  斗地主牌桌实例
     * @param userId 操作玩家用户 ID
     * @param opInfo 操作信息载荷
     * @param user   操作玩家座位实体
     * @param banner 局内横幅与叫分上下文
     * @return 错误码（0 表示成功）
     */
    private static int applyCall(DdzTable table, int userId, GameProto.OpInfo opInfo, TableUser user, Banner banner) {
        int cv = opInfo.getChoiceValue();
        // gameSubType == 1：经典二人/三人抢地主玩法（叫地主/不叫）
        if (table.getTableModel().getGameSubType() == 1) {
            if (cv != ConstProto.Operation.CALL_VALUE && cv != ConstProto.Operation.NOT_CALL_VALUE) {
                return ConstProto.Result.OP_CURR_ERROR_VALUE;
            }
            if (cv == ConstProto.Operation.NOT_CALL_VALUE) {
                broadcastAck(table, userId, GameProto.OpInfo.newBuilder().setChoiceValue(cv).build());
                banner.setRobPhase(false);
                int next = (user.getSeated() + 1) % table.getTableModel().getSeatNum();
                banner.setFirstRandomRobSeat(next);
                table.getOp().setCurrOpSeat(next);
                banner.setRobBroadcastDone(false);
                table.upNextStateWithTime(TableState.ROB, System.currentTimeMillis());
                return ConstProto.Result.SUCCESS_VALUE;
            }
            banner.setCandidateSeat(user.getSeated());
            banner.setMaxCallScore(1);
            banner.setRobPhase(true);
            banner.prepareRobFarmerOrder(user.getSeated(), table.getTableModel().getSeatNum());
            banner.setRobBroadcastDone(false);
            table.getOp().setCurrOpSeat(banner.getCurrentRobSeat());
            broadcastAck(table, userId, GameProto.OpInfo.newBuilder().setChoiceValue(cv).build());
            table.upNextStateWithTime(TableState.ROB, System.currentTimeMillis());
            return ConstProto.Result.SUCCESS_VALUE;
        }

        // 标准 1/2/3 叫分玩法
        if (cv != ConstProto.Operation.NOT_CALL_VALUE && !DdzBidOpcodes.isCallScore(cv)) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        int score = cv == ConstProto.Operation.NOT_CALL_VALUE ? 0 : DdzBidOpcodes.callScoreFromChoiceValue(cv);
        if (score > 0 && !banner.isScoreAvailable(score)) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        banner.addCalledScore(score);

        DdzReplayRecorder replay = table.getDdzReplay();
        if (replay != null) {
            replay.writeAuditEvent("座" + user.getSeated() + " 收到选项 不叫/叫1分/叫2分/叫3分 → 客户端展示");
            replay.writeAuditEvent("座" + user.getSeated() + " " + (user.isRobot() ? "机器人" : "玩家")
                    + "选择 " + (score > 0 ? "叫" + score + "分" : "不叫"));
            if (score > 0) {
                replay.recordBid(user.getSeated(), score);
            } else {
                replay.recordNotCall(user.getSeated());
            }
        }

        if (score > banner.getMaxCallScore()) {
            banner.setMaxCallScore(score);
            banner.setCandidateSeat(user.getSeated());
        }
        broadcastAck(table, userId, GameProto.OpInfo.newBuilder().setChoiceValue(cv).build());
        banner.addBidResponse();

        // 叫到 3 分直接封顶，无需后续叫牌与抢地主，直接定庄开局
        if (score == 3) {
            banner.setCandidateSeat(user.getSeated());
            banner.setMaxCallScore(3);
            finishBidding(table, banner);
            return ConstProto.Result.SUCCESS_VALUE;
        }
        int seatNum = table.getTableModel().getSeatNum();
        // 若所有人均已叫分完毕，进入农民抢地主阶段；否则顺延下一家继续叫分
        if (banner.getBidResponses() >= seatNum) {
            completeCallPhase(table, banner, seatNum);
        } else {
            table.getOp().moveToNextOp();
            banner.setRobBroadcastDone(false);
            table.upNextStateWithTime(TableState.ROB, System.currentTimeMillis());
        }
        return ConstProto.Result.SUCCESS_VALUE;
    }

    /**
     * 第一轮叫分全部结束后的过渡处理：
     * 若全场均不叫分，则随机挑选一名玩家作为保底地主；随后初始化农民抢地主次序。
     *
     * @param table   斗地主牌桌实例
     * @param banner  横幅上下文
     * @param seatNum 总座位数
     */
    private static void completeCallPhase(DdzTable table, Banner banner, int seatNum) {
        if (banner.getMaxCallScore() <= 0) {
            banner.setCandidateSeat(ThreadLocalRandom.current().nextInt(seatNum));
            banner.setMaxCallScore(1);
        }
        banner.setRobPhase(true);
        banner.prepareRobFarmerOrder(banner.getCandidateSeat(), seatNum);
        banner.setRobBroadcastDone(false);
        table.getOp().setCurrOpSeat(banner.getCurrentRobSeat());
        table.upNextStateWithTime(TableState.ROB, System.currentTimeMillis());
    }

    /**
     * 执行抢地主阶段（第二阶段）的操作判定与倍率累计。
     *
     * @param table  斗地主牌桌实例
     * @param userId 操作玩家用户 ID
     * @param opInfo 操作信息载荷
     * @param user   操作玩家座位实体
     * @param banner 横幅上下文
     * @return 错误码（0 表示成功）
     */
    private static int applyRob(DdzTable table, int userId, GameProto.OpInfo opInfo, TableUser user, Banner banner) {
        int cv = opInfo.getChoiceValue();
        if (cv != ConstProto.Operation.ROB_VALUE && cv != ConstProto.Operation.NOT_ROB_VALUE) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        if (user.getSeated() != banner.getCurrentRobSeat()) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }

        DdzReplayRecorder replay = table.getDdzReplay();
        if (replay != null) {
            replay.writeAuditEvent("座" + user.getSeated() + " 收到选项 抢地主/不抢 → 客户端展示");
            replay.writeAuditEvent("座" + user.getSeated() + " " + (user.isRobot() ? "机器人" : "玩家")
                    + "选择 " + (cv == ConstProto.Operation.ROB_VALUE ? "抢地主" : "不抢"));
            if (cv == ConstProto.Operation.ROB_VALUE) {
                replay.recordRob(user.getSeated());
            } else {
                replay.recordNotRob(user.getSeated());
            }
        }

        // 抢地主倍率翻倍：每次「抢」倍数×2，并更新当前地主候选人
        if (cv == ConstProto.Operation.ROB_VALUE) {
            banner.setRobMultiplierAccum(banner.getRobMultiplierAccum() * 2);
            banner.setCandidateSeat(user.getSeated());
        }
        broadcastAck(table, userId, GameProto.OpInfo.newBuilder().setChoiceValue(cv).build());
        banner.addRobResponse();
        // 两位农民均表态完毕，抢地主流程彻底闭环，进入开局定庄
        if (banner.getRobResponses() >= banner.getRobFarmerSeats().size()) {
            finishBidding(table, banner);
        } else {
            banner.advanceRobTurn();
            table.getOp().setCurrOpSeat(banner.getCurrentRobSeat());
            banner.setRobBroadcastDone(false);
            table.upNextStateWithTime(TableState.ROB, System.currentTimeMillis());
        }
        return ConstProto.Result.SUCCESS_VALUE;
    }

    /**
     * 叫分与抢地主全部终结，定庄、发底牌并切入正式出牌状态（CARD）。
     *
     * @param table  斗地主牌桌实例
     * @param banner 横幅上下文
     */
    private static void finishBidding(DdzTable table, Banner banner) {
        int landlordSeat = banner.getCandidateSeat();
        int baseScore = Math.max(1, banner.getMaxCallScore());
        table.getDdz().setLandlordSeat(landlordSeat);
        table.getDdz().setBaseScore(baseScore);
        table.getDdz().setRobMultiplier(banner.getRobMultiplierAccum());
        table.getDdz().resetCurrentTrickCards();
        table.getDdz().setLastPlaySeat(-1);
        table.getDdz().setFarmerEverPlayed(false);
        table.getDdz().setLandlordPlayCount(0);
        // 将三张底牌亮出并装配到地主手牌集合中
        table.getCardPool().attachBottomToLandlord(table, landlordSeat);

        DdzReplayRecorder replay = table.getDdzReplay();
        if (replay != null) {
            List<Integer> bottomIds = new ArrayList<>(table.getDdz().getRevealedBottomCards());
            replay.recordBottomCards(landlordSeat, bottomIds);
        }

        // 重置操作计数器并将优先出牌权授予地主，正式切入出牌状态
        table.getOp().reset();
        table.getOp().setCurrOpSeat(landlordSeat);
        table.upNextStateWithTime(TableState.CARD, System.currentTimeMillis());
    }

    /**
     * 向桌内全员广播叫分/抢地主操作结果应答并同步当前累计倍数。
     *
     * @param table       牌桌实例
     * @param actorUserId 操作发起人用户 ID
     * @param op          操作数据结构
     */
    private static void broadcastAck(DdzTable table, int actorUserId, GameProto.OpInfo op) {
        int base = Math.max(1, table.getBanner().getMaxCallScore());
        int rob = Math.max(1, table.getBanner().getRobMultiplierAccum());
        GameProto.AckOp msg = GameProto.AckOp.newBuilder()
                .setOp(op)
                .setOpId(actorUserId)
                .setOpFrom(actorUserId)
                .setBaseScore(base).setRobMultiplier(rob)
                .setBombMultiplier(1).setCurrentMultiplier(base * rob)
                .build();
        table.sendTableMessage(msg, GMsg.ACK_OP);
    }
}
