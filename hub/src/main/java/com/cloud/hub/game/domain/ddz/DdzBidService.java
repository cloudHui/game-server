package com.cloud.hub.game.domain.ddz;

import com.cloud.hub.game.domain.banner.Banner;
import com.cloud.hub.game.domain.replay.DdzReplayRecorder;
import com.cloud.hub.game.domain.table.TableUser;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;
import utils.registry.enums.TableState;

import java.util.ArrayList;
import java.util.List;

/**
 * 斗地主叫地主、抢地主与叫分阶段核心业务处理服务。
 * <p>
 * 遵循单一职责设计，将复杂的抢地主流转拆解为清晰的小颗粒度方法，所有单个方法行数严格控制在 50 行以内。
 *
 * @author cloud
 * @version 1.0
 * @since 1.0
 */
public final class DdzBidService {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(DdzBidService.class);

    private DdzBidService() {
    }

    /**
     * 叫分或抢地主操作超时时的自动托管兜底处理。
     * <p>
     * 处于首叫阶段超时自动视为「不叫」；处于抢地主阶段超时自动视为「不抢」。
     *
     * @param table 斗地主牌桌实体
     */
    public static void onBidTimeout(DdzTable table) {
        int seat = table.getOp().getCurrOpSeat();
        TableUser u = table.getSeatUser(seat);
        if (u == null) {
            return;
        }
        Banner banner = table.getBanner();
        // 根据是否已有玩家叫地主，动态选择超时兜底动作（不抢 vs 不叫）
        int choice = banner.hasCaller() ? ConstProto.Operation.NOT_ROB_VALUE : ConstProto.Operation.NOT_CALL_VALUE;
        apply(table, u.getUserId(), GameProto.OpInfo.newBuilder().setChoiceValue(choice).build());
    }

    /**
     * 处理客户端提交的叫地主/抢地主/叫分操作入口。
     *
     * @param table  斗地主牌桌实体
     * @param userId 提交操作的用户 ID
     * @param opInfo 客户端操作载荷
     * @return 操作结果状态码（0 为成功，非 0 对应 ConstProto.Result 错误码）
     */
    public static int apply(DdzTable table, int userId, GameProto.OpInfo opInfo) {
        // 门禁：仅在等待叫抢状态且轮到当前座位的玩家提交才合法
        if (table.getTableState() != TableState.IDLE_ROB) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        TableUser user = table.getUsers().get(userId);
        if (user == null || user.getSeated() != table.getOp().getCurrOpSeat()) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        Banner banner = table.getBanner();
        // 玩法分流：gameSubType == 1 为经典叫抢玩法，其余为 1/2/3 叫分玩法
        if (table.getTableModel().getGameSubType() == 1) {
            return applyClassicRob(table, userId, opInfo, user, banner);
        }
        return applyCallScore(table, userId, opInfo, user, banner);
    }

    /**
     * 经典抢地主模式总调度（根据是否已有首叫者分流）。
     *
     * @param table  牌桌实体
     * @param userId 用户 ID
     * @param opInfo 操作信息
     * @param user   玩家座位实体
     * @param banner 叫抢状态上下文
     * @return 错误码
     */
    private static int applyClassicRob(DdzTable table, int userId, GameProto.OpInfo opInfo, TableUser user,
            Banner banner) {
        int cv = opInfo.getChoiceValue();
        int seat = user.getSeated();
        int seatNum = table.getTableModel().getSeatNum();
        // 尚未产生首叫者时进入阶段一，已有首叫者时进入阶段二抢地主
        if (!banner.hasCaller()) {
            return handleCallPhase(table, userId, cv, seat, seatNum, user, banner);
        }
        return handleRobPhase(table, userId, cv, seat, seatNum, user, banner);
    }

    /**
     * 阶段一：尚未产生首叫者时的轮询处理（仅支持「叫地主」或「不叫」）。
     *
     * @param table   牌桌实体
     * @param userId  用户 ID
     * @param cv      操作选项值
     * @param seat    当前玩家座位
     * @param seatNum 总座位数
     * @param user    玩家实体
     * @param banner  叫抢上下文
     * @return 错误码
     */
    private static int handleCallPhase(DdzTable table, int userId, int cv, int seat, int seatNum, TableUser user,
            Banner banner) {
        if (cv != ConstProto.Operation.CALL_VALUE && cv != ConstProto.Operation.NOT_CALL_VALUE) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        DdzReplayRecorder replay = table.getDdzReplay();
        if (cv == ConstProto.Operation.NOT_CALL_VALUE) {
            recordReplayAudit(replay, seat, "选择 不叫", false);
            banner.addGivenUpSeat(seat);
            broadcastAck(table, userId, cv);
            logger.info("[DDZ-Bid] TableId: {}, Seat: {}, User: {}, Choice: NOT_CALL, GivenUpCount: {}/{}",
                    table.getTableId(), seat, userId, banner.getGivenUpCount(), seatNum);
            // 若 3 人全部选择「不叫」，触发流局重新洗牌发牌
            if (banner.getGivenUpCount() >= seatNum) {
                logger.info("[DDZ-Bid] TableId: {}, All players NOT_CALL -> RedealCards", table.getTableId());
                table.redealCards();
                return ConstProto.Result.SUCCESS_VALUE;
            }
            advanceToNextRobber(table, seat, seatNum, banner);
            return ConstProto.Result.SUCCESS_VALUE;
        }

        // 玩家首次选择「叫地主」：确立首叫者身份与当前候选地主
        recordReplayAudit(replay, seat, "选择 叫地主", true);
        banner.setFirstCallerSeat(seat);
        banner.setCandidateSeat(seat);
        banner.setMaxCallScore(1);
        banner.setRobPhase(true);
        broadcastAck(table, userId, cv);
        logger.info("[DDZ-Bid] TableId: {}, Seat: {}, User: {}, First CALL accepted, Candidate: {}",
                table.getTableId(), seat, userId, seat);

        // 若此前其他玩家已全都不叫，直接定庄；否则顺延进入抢地主流程
        if (banner.getGivenUpCount() >= seatNum - 1) {
            finishBidding(table, banner);
        } else {
            advanceToNextRobber(table, seat, seatNum, banner);
        }
        return ConstProto.Result.SUCCESS_VALUE;
    }

    /**
     * 阶段二：抢地主阶段处理（分流为首叫者终局裁决与普通玩家抢地主）。
     *
     * @param table   牌桌实体
     * @param userId  用户 ID
     * @param cv      操作选项值
     * @param seat    当前玩家座位
     * @param seatNum 总座位数
     * @param user    玩家实体
     * @param banner  叫抢上下文
     * @return 错误码
     */
    private static int handleRobPhase(DdzTable table, int userId, int cv, int seat, int seatNum, TableUser user,
            Banner banner) {
        if (cv != ConstProto.Operation.ROB_VALUE && cv != ConstProto.Operation.NOT_ROB_VALUE) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        // 首叫者作为最终结束点：由其进行最终反抢决策
        if (seat == banner.getFirstCallerSeat()) {
            return handleFirstCallerFinal(table, userId, cv, seat, banner);
        }
        return handleNormalRob(table, userId, cv, seat, seatNum, banner);
    }

    /**
     * 首叫者终局决胜表态：表态后流程必定闭环定庄。
     *
     * @param table  牌桌实体
     * @param userId 用户 ID
     * @param cv     操作选项值（ROB 或 NOT_ROB）
     * @param seat   首叫者座位
     * @param banner 叫抢上下文
     * @return 错误码
     */
    private static int handleFirstCallerFinal(DdzTable table, int userId, int cv, int seat, Banner banner) {
        DdzReplayRecorder replay = table.getDdzReplay();
        if (cv == ConstProto.Operation.ROB_VALUE) {
            recordReplayAudit(replay, seat, "(首叫者终局) 选择 抢地主", true);
            banner.setRobMultiplierAccum(banner.getRobMultiplierAccum() * 2);
            banner.setCandidateSeat(seat);
            logger.info("[DDZ-Bid] TableId: {}, FirstCaller Seat: {}, User: {}, Final ROB -> Multiplier: {}",
                    table.getTableId(), seat, userId, banner.getRobMultiplierAccum());
        } else {
            recordReplayAudit(replay, seat, "(首叫者终局) 选择 不抢", false);
            logger.info("[DDZ-Bid] TableId: {}, FirstCaller Seat: {}, User: {}, Final NOT_ROB",
                    table.getTableId(), seat, userId);
        }
        broadcastAck(table, userId, cv);
        // 首叫者表态完毕，抢地主流程彻底闭环，立即定庄
        finishBidding(table, banner);
        return ConstProto.Result.SUCCESS_VALUE;
    }

    /**
     * 普通玩家抢地主处理与流转驱动。
     *
     * @param table   牌桌实体
     * @param userId  用户 ID
     * @param cv      操作选项值
     * @param seat    当前座位
     * @param seatNum 总座位数
     * @param banner  叫抢上下文
     * @return 错误码
     */
    private static int handleNormalRob(DdzTable table, int userId, int cv, int seat, int seatNum, Banner banner) {
        DdzReplayRecorder replay = table.getDdzReplay();
        if (cv == ConstProto.Operation.ROB_VALUE) {
            recordReplayAudit(replay, seat, "选择 抢地主", true);
            banner.setHasRobbed(true);
            banner.setRobMultiplierAccum(banner.getRobMultiplierAccum() * 2);
            banner.setCandidateSeat(seat);
            logger.info("[DDZ-Bid] TableId: {}, Seat: {}, User: {}, ROB accepted -> Candidate: {}, Multiplier: {}",
                    table.getTableId(), seat, userId, seat, banner.getRobMultiplierAccum());
        } else {
            recordReplayAudit(replay, seat, "选择 不抢", false);
            // 选择不抢的玩家永久放弃后续抢地主资格
            banner.addGivenUpSeat(seat);
            logger.info("[DDZ-Bid] TableId: {}, Seat: {}, User: {}, NOT_ROB", table.getTableId(), seat, userId);
        }
        broadcastAck(table, userId, cv);

        int nextSeat = findNextActiveSeat(seat, seatNum, banner);
        // 关键逻辑：若顺延下家为首叫者，且后序全无人抢地主，则触发极速定庄
        if (nextSeat == banner.getFirstCallerSeat()) {
            if (!banner.isHasRobbed()) {
                finishBidding(table, banner);
                return ConstProto.Result.SUCCESS_VALUE;
            }
        }
        table.getOp().setCurrOpSeat(nextSeat);
        banner.setRobBroadcastDone(false);
        table.upNextStateWithTime(TableState.ROB, System.currentTimeMillis());
        return ConstProto.Result.SUCCESS_VALUE;
    }

    /**
     * 顺延至顺时针下一个未弃权玩家继续操作。
     *
     * @param table       牌桌实体
     * @param currentSeat 当前座位
     * @param seatNum     总座位数
     * @param banner      叫抢上下文
     */
    private static void advanceToNextRobber(DdzTable table, int currentSeat, int seatNum, Banner banner) {
        int nextSeat = findNextActiveSeat(currentSeat, seatNum, banner);
        table.getOp().setCurrOpSeat(nextSeat);
        banner.setRobBroadcastDone(false);
        table.upNextStateWithTime(TableState.ROB, System.currentTimeMillis());
    }

    /**
     * 顺时针寻找下一个尚未弃权的玩家座位（严格跳过已放弃玩家）。
     *
     * @param currentSeat 当前座位
     * @param seatNum     牌桌总座位数
     * @param banner      叫抢状态横幅
     * @return 下一个可操作的座位
     */
    private static int findNextActiveSeat(int currentSeat, int seatNum, Banner banner) {
        int next = (currentSeat + 1) % seatNum;
        for (int i = 0; i < seatNum; i++) {
            if (!banner.isGivenUp(next)) {
                return next;
            }
            next = (next + 1) % seatNum;
        }
        return currentSeat;
    }

    /**
     * 标准 1/2/3 叫分玩法（gameSubType == 0）。
     *
     * @param table  牌桌实体
     * @param userId 用户 ID
     * @param opInfo 操作信息
     * @param user   玩家座位实体
     * @param banner 叫抢上下文
     * @return 错误码
     */
    private static int applyCallScore(DdzTable table, int userId, GameProto.OpInfo opInfo, TableUser user,
            Banner banner) {
        int cv = opInfo.getChoiceValue();
        if (cv != ConstProto.Operation.NOT_CALL_VALUE && !DdzBidOpcodes.isCallScore(cv)) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        int score = cv == ConstProto.Operation.NOT_CALL_VALUE ? 0 : DdzBidOpcodes.callScoreFromChoiceValue(cv);
        if (score > 0 && !banner.isScoreAvailable(score)) {
            return ConstProto.Result.OP_CURR_ERROR_VALUE;
        }
        banner.addCalledScore(score);
        if (score > banner.getMaxCallScore()) {
            banner.setMaxCallScore(score);
            banner.setCandidateSeat(user.getSeated());
        }
        logger.info("[DDZ-Bid] TableId: {}, Seat: {}, User: {}, CallScore: {}, CurrentMax: {}, Candidate: {}",
                table.getTableId(), user.getSeated(), userId, score, banner.getMaxCallScore(), banner.getCandidateSeat());
        broadcastAck(table, userId, cv);
        banner.addBidResponse();

        // 叫满 3 分直接封顶定庄
        if (score == 3) {
            banner.setCandidateSeat(user.getSeated());
            banner.setMaxCallScore(3);
            logger.info("[DDZ-Bid] TableId: {}, Seat: {}, User: {} called 3 points (MAX) -> FinishBidding directly",
                    table.getTableId(), user.getSeated(), userId);
            finishBidding(table, banner);
            return ConstProto.Result.SUCCESS_VALUE;
        }
        int seatNum = table.getTableModel().getSeatNum();
        if (banner.getBidResponses() >= seatNum) {
            completeCallPhase(table, banner);
        } else {
            table.getOp().moveToNextOp();
            banner.setRobBroadcastDone(false);
            table.upNextStateWithTime(TableState.ROB, System.currentTimeMillis());
        }
        return ConstProto.Result.SUCCESS_VALUE;
    }

    /**
     * 叫分模式结束处理：全不叫则流局重发，否则最高叫分者定庄。
     *
     * @param table  牌桌实体
     * @param banner 叫抢上下文
     */
    private static void completeCallPhase(DdzTable table, Banner banner) {
        if (banner.getMaxCallScore() <= 0) {
            logger.info("[DDZ-Bid] TableId: {}, All players called 0 points -> RedealCards", table.getTableId());
            table.redealCards();
        } else {
            finishBidding(table, banner);
        }
    }

    /**
     * 叫抢全部闭环，确定地主座位并亮底牌进入正式出牌状态（CARD）。
     *
     * @param table  斗地主牌桌实例
     * @param banner 叫抢上下文
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

        logger.info("[DDZ-Bid] TableId: {}, Bidding finished -> LandlordSeat: {}, BaseScore: {}, RobMultiplier: {}, BottomCards: {}",
                table.getTableId(), landlordSeat, baseScore, banner.getRobMultiplierAccum(), table.getDdz().getRevealedBottomCards());

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
     * 记录录像与审计日志。
     *
     * @param replay   录像记录器
     * @param seat     座位编号
     * @param msg      审计文本
     * @param isAction 是否积极动作（叫/抢为 true，不叫/不抢为 false）
     */
    private static void recordReplayAudit(DdzReplayRecorder replay, int seat, String msg, boolean isAction) {
        if (replay == null)
            return;
        replay.writeAuditEvent("座" + seat + " " + msg);
        if (isAction) {
            replay.recordRob(seat);
        } else {
            replay.recordNotRob(seat);
        }
    }

    /**
     * 广播操作结果应答并同步当前累计倍数。
     *
     * @param table       牌桌实例
     * @param actorUserId 操作发起人用户 ID
     * @param choiceValue 操作选项枚举值
     */
    private static void broadcastAck(DdzTable table, int actorUserId, int choiceValue) {
        int base = Math.max(1, table.getBanner().getMaxCallScore());
        int rob = Math.max(1, table.getBanner().getRobMultiplierAccum());
        GameProto.AckOp msg = GameProto.AckOp.newBuilder()
                .setOp(GameProto.OpInfo.newBuilder().setChoiceValue(choiceValue).build())
                .setOpId(actorUserId)
                .setOpFrom(actorUserId)
                .setBaseScore(base).setRobMultiplier(rob)
                .setBombMultiplier(1).setCurrentMultiplier(base * rob)
                .build();
        table.sendTableMessage(msg, GMsg.ACK_OP);
    }
}