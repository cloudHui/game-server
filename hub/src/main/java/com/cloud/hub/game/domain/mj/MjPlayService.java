package com.cloud.hub.game.domain.mj;

import com.cloud.hub.game.domain.table.TableUser;
import com.cloud.hub.game.domain.replay.MjReplayRecorder;
import msg.registor.enums.TableState;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 麻将流程控制服务
 * 负责出牌处理、流程推进、WinChecker/Scoring工厂创建
 * 具体业务拆分到: MjDrawService(摸牌), MjClaimService(claim), MjGangService(杠), MjSettleService(结算)
 */
public class MjPlayService {

    private static final Logger logger = LoggerFactory.getLogger(MjPlayService.class);

    private MjPlayService() {
    }

    /**
     * 子类型 → WinChecker 工厂注册表。
     * 新增麻将变种时在此添加一行，无需修改 createWinChecker。
     */
    private static final Map<Integer, Function<MjTable, MjWinChecker>> CHECKER_FACTORIES = new HashMap<>();

    /**
     * 子类型 → Scoring 工厂注册表。
     * 新增麻将变种时在此添加一行，无需修改 createScoring。
     */
    private static final Map<Integer, Function<MjTable, MjScoring>> SCORING_FACTORIES = new HashMap<>();

    static {
        // subType=1: 荆门麻将
        CHECKER_FACTORIES.put(1, t -> new JmWinChecker(
                t.getMjContext().getLaiZiTileId(),
                t.getTableModel().getAllowSevenPairs() != 0));
        SCORING_FACTORIES.put(1, t -> new JmMjScoring());

        // subType=2: 卡五星
        CHECKER_FACTORIES.put(2, t -> new KwWinChecker(
                new int[]{1, 2},
                t.getTableModel().getAllowSevenPairs() != 0, true));
        SCORING_FACTORIES.put(2, t -> {
            MjWinChecker checker = CHECKER_FACTORIES.get(2).apply(t);
            return new KwMjScoring((KwWinChecker) checker);
        });
    }

    // ======================== 出牌 ========================

    /**
     * 处理玩家出牌请求
     */
    public static boolean applyDiscard(MjTable table, int userId, GameProto.OpInfo opInfo) {
        if (table.getTableState() != TableState.MJ_DISCARD) {
            logger.warn("非出牌阶段拒绝出牌, table: {}, state: {}, userId: {}",
                    table.getTableId(), table.getTableState(), userId);
            return false;
        }
        MjTableContext ctx = table.getMjContext();
        if (!ctx.isTileDrawn() && !ctx.isDiscardAfterClaim()) {
            logger.warn("本回合尚未摸牌且非碰吃接牌出牌, 拒绝出牌, table: {}, seat: {}, userId: {}",
                    table.getTableId(), table.getOp().getCurrOpSeat(), userId);
            return false;
        }
        int seat = table.getOp().getCurrOpSeat();
        TableUser user = table.getSeatUser(seat);
        if (user == null || user.getUserId() != userId) {
            logger.warn("出牌操作座位不匹配, table: {}, seat: {}, userId: {}", table.getTableId(), seat, userId);
            return false;
        }
        if (opInfo.getOpCardsCount() != 1 || opInfo.getOpCards(0).getCardsCount() != 1) {
            logger.warn("出牌牌数非法, table: {}, userId: {}, opCardsCount: {}, cardCount: {}",
                    table.getTableId(), userId, opInfo.getOpCardsCount(),
                    opInfo.getOpCardsCount() == 0 ? 0 : opInfo.getOpCards(0).getCardsCount());
            return false;
        }

        int tileId = opInfo.getOpCards(0).getCards(0).getValue();
        int beforeCount = user.getCards().size();
        boolean removed = user.removeCardsByProtoIds(Collections.singletonList(tileId));
        if (!removed) {
            logger.warn("出牌不在手牌中, table: {}, userId: {}, tile: {}", table.getTableId(), userId, tileId);
            return false;
        }

        ctx.setLastDiscardTile(tileId);
        ctx.setLastDiscardSeat(seat);
        ctx.addDiscard(seat, tileId);
        ctx.resetTurn();
        ctx.setGangShangKaiHua(false);

        GameProto.NotMjState not = GameProto.NotMjState.newBuilder()
                .setOpSeat(seat).setTileId(tileId)
                .setAction(ConstProto.Operation.DISCARD)
                .setWallLeft(table.getMjTilePool().remaining()).build();
        table.sendTableMessage(not, GMsg.MJ_TILE_NOT);

        MjReplayRecorder replay = (MjReplayRecorder) table.getReplayRecorder();
        if (replay != null) replay.recordDiscard(seat, tileId);

        logger.info("麻将出牌, table: {}, userId: {}, seat: {}, tile: {}, hand: {} -> {}, exposed: {}, discardCount: {}",
                table.getTableId(), userId, seat, tileId, beforeCount, user.getCards().size(),
                ctx.getExposedSets(seat).size(), ctx.getDiscardPile(seat).size());
        return true;
    }

    // ======================== 流程控制 ========================

    /**
     * 出牌后的公共流程：检测claim，无人响应则进入下一个玩家摸牌
     */
    public static void afterDiscard(MjTable table) {
        if (!MjClaimService.checkClaim(table)) {
            nextPlayer(table);
            table.upNextStateWithTime(TableState.MJ_PLAY, System.currentTimeMillis());
        }
        int nextSeat = table.getOp().getCurrOpSeat();
        logger.info("麻将出牌后轮转, table: {}, fromSeat: {}, nextState: {}, nextSeat: {}, tileDrawn: {}, discardAfterClaim: {}, nextCanOperate: {}",
                table.getTableId(), table.getMjContext().getLastDiscardSeat(), table.getTableState(), nextSeat,
                table.getMjContext().isTileDrawn(), table.getMjContext().isDiscardAfterClaim(),
                table.getOp().getSeatOps(nextSeat));
    }

    /**
     * 移动到下一个玩家
     */
    public static void nextPlayer(MjTable table) {
        int currSeat = table.getOp().getCurrOpSeat();
        int nextSeat = table.nextSeat(currSeat);
        table.getOp().setCurrOpSeat(nextSeat);
        table.getMjContext().resetTurn();
        table.getMjContext().setGangShangKaiHua(false);
    }

    // ======================== 工厂方法 ========================

    /**
     * 根据桌子配置创建 WinChecker。
     * 新增子类型时在 CHECKER_FACTORIES 注册一行即可，无需修改此处。
     */
    public static MjWinChecker createWinChecker(MjTable table) {
        int subType = table.getTableModel().getGameSubType();
        Function<MjTable, MjWinChecker> factory = CHECKER_FACTORIES.get(subType);
        if (factory != null) return factory.apply(table);
        // 默认：标准麻将
        return new MjWinChecker(table.getTableModel().getAllowSevenPairs() != 0);
    }

    /**
     * 根据桌子配置创建 Scoring。
     * 新增子类型时在 SCORING_FACTORIES 注册一行即可，无需修改此处。
     */
    public static MjScoring createScoring(MjTable table) {
        int subType = table.getTableModel().getGameSubType();
        Function<MjTable, MjScoring> factory = SCORING_FACTORIES.get(subType);
        if (factory != null) return factory.apply(table);
        // 默认：标准麻将计分
        return new JmMjScoring();
    }
}
