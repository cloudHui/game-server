package com.cloud.hub.game.domain.mj.state;

import com.cloud.hub.game.domain.table.RobotOperationDelay;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import com.cloud.hub.game.domain.mj.MjDrawService;
import com.cloud.hub.game.domain.mj.MjExposedSet;
import com.cloud.hub.game.domain.mj.MjPlayService;
import com.cloud.hub.game.domain.mj.MjTable;
import com.cloud.hub.game.domain.mj.MjTableContext;
import com.cloud.hub.game.domain.mj.MjWinChecker;
import com.cloud.hub.game.domain.mj.ai.MjSimpleAi;
import com.cloud.hub.game.domain.state.AbstractTableHandle;
import utils.registry.annotation.ProcessEnum;
import utils.registry.enums.TableState;
import msg.registor.message.GMsg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;
import proto.GameProto;

import java.util.Collections;
import java.util.List;

/**
 * 麻将出牌阶段：等待玩家出牌。
 * 首次进入时发送出牌提示(NotOperation)，超时自动出刚摸到的牌。
 * 支持: 出牌、暗杠、补杠
 */
@ProcessEnum(TableState.MJ_DISCARD)
public class MjDiscard extends AbstractTableHandle {

    private static final Logger logger = LoggerFactory.getLogger(MjDiscard.class);

    @Override
    public boolean handle(Table table) {
        MjTable mjTable = (MjTable) table;
        MjTableContext ctx = mjTable.getMjContext();
        logger.info("麻将进入出牌状态, tableId: {}, seat: {}, tileDrawn: {}, drawnTile: {}, discardAfterClaim: {}",
                table.getTableId(), table.getOp().getCurrOpSeat(), ctx.isTileDrawn(),
                ctx.getDrawnTile(), ctx.isDiscardAfterClaim());
        if (!ctx.isDiscardPromptSent()) {
            sendDiscardPrompt(mjTable);
            ctx.setDiscardPromptSent(true);
        }
        TableUser seatUser = table.getSeatUser(table.getOp().getCurrOpSeat());
        if (seatUser != null && seatUser.isRobot()
                && System.currentTimeMillis() >= table.getStateStartTime() + randomRobotDelay()) {
            overTime(table);
            return false;
        }
        return super.handle(table);
    }

    private long randomRobotDelay() {
        return RobotOperationDelay.randomMillis();
    }

    @Override
    public boolean onTiming(Table table) {
        return false;
    }

    /**
     * 出牌超时处理：AI出牌或自动出牌，然后走公共afterDiscard流程
     */
    @Override
    public void overTime(Table table) {
        MjTable mjTable = (MjTable) table;
        logger.info("麻将出牌超时, tableId: {}, seat: {}", table.getTableId(), table.getOp().getCurrOpSeat());

        int seat = table.getOp().getCurrOpSeat();
        TableUser seatUser = table.getSeatUser(seat);
        boolean allowAi = table.isAutoPlayEnabled()
                || (seatUser != null && seatUser.isRobot());
        if (!allowAi) {
            MjPlayService.afterDiscard(mjTable);
            return;
        }

        MjTableContext ctx = mjTable.getMjContext();
        int aiLevel = ctx.getAiLevel();
        if (aiLevel >= 0) {
            TableUser user = seatUser;
            if (user != null) {
                int aiTile = MjSimpleAi.decideDiscard(mjTable, user, ctx.getDrawnTile());
                if (aiTile > 0) {
                    GameProto.OpInfo op = GameProto.OpInfo.newBuilder()
                            .setChoice(ConstProto.Operation.DISCARD)
                            .addOpCards(GameProto.CardInfo.newBuilder()
                                    .addCards(GameProto.Card.newBuilder().setValue(aiTile).build())
                                    .build())
                            .build();
                    if (MjPlayService.applyDiscard(mjTable, user.getUserId(), op)) {
                        MjPlayService.afterDiscard(mjTable);
                        return;
                    }
                }
            }
        }

        MjDrawService.autoDiscard(mjTable);
        MjPlayService.afterDiscard(mjTable);
    }

    /**
     * 向当前出牌玩家下发出牌及可用的暗杠/补杠提示。
     */
    private void sendDiscardPrompt(MjTable table) {
        int seat = table.getOp().getCurrOpSeat();
        TableUser user = table.getSeatUser(seat);
        if (user == null) return;

        table.getOp().clearChoiceMap();
        GameProto.NotOperation.Builder notBuilder = GameProto.NotOperation.newBuilder()
                .setWait(TableState.MJ_DISCARD.getOverTime())
                .setOpSeat(seat);

        // 出牌选项(总是可以出牌)
        GameProto.OpInfo discard = GameProto.OpInfo.newBuilder()
                .setChoice(ConstProto.Operation.DISCARD).build();
        table.getOp().addPosOpInfo(seat, discard);
        notBuilder.addChoice(discard);

        // 填充暗杠与补杠选项
        populateGangChoices(table, user, seat, notBuilder);

        // 广播提示
        table.sendTableMessage(notBuilder.build(), GMsg.NOT_OP);
    }

    /** 检查手牌与亮牌并向当前座位注入可用的暗杠/补杠操作 */
    private void populateGangChoices(MjTable table, TableUser user, int seat, GameProto.NotOperation.Builder notBuilder) {
        MjWinChecker winChecker = MjPlayService.createWinChecker(table);
        boolean allowAnGang = table.getTableModel().getAllowGangAn() != 0;
        List<Integer> anGangTiles = allowAnGang ? winChecker.getAnGangTiles(user.getCards()) : Collections.emptyList();
        for (int gangTileId : anGangTiles) {
            GameProto.OpInfo anGang = buildGangOp(gangTileId);
            table.getOp().addPosOpInfo(seat, anGang);
            notBuilder.addChoice(anGang);
        }

        boolean allowBuGang = table.getTableModel().getAllowGangBu() != 0;
        MjTableContext ctx = table.getMjContext();
        for (MjExposedSet set : ctx.getExposedSets(seat)) {
            if (set.getType() == MjExposedSet.Type.PENG) {
                int pengTileId = set.getTileIds().get(0);
                if (allowBuGang && winChecker.canBuGang(user.getCards(), ctx.getExposedSets(seat), pengTileId)) {
                    GameProto.OpInfo buGang = buildGangOp(pengTileId);
                    table.getOp().addPosOpInfo(seat, buGang);
                    notBuilder.addChoice(buGang);
                }
            }
        }
    }

    /** 构建杠牌操作 OpInfo */
    private static GameProto.OpInfo buildGangOp(int tileId) {
        return GameProto.OpInfo.newBuilder()
                .setChoice(ConstProto.Operation.MJ_GANG)
                .addOpCards(GameProto.CardInfo.newBuilder()
                        .addCards(GameProto.Card.newBuilder().setValue(tileId).build())
                        .build())
                .build();
    }
}
