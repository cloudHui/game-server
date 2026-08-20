package com.cloud.game.manager.table.state;

import com.cloud.game.manager.table.RobotOperationDelay;
import com.cloud.game.manager.table.Table;
import com.cloud.game.manager.table.TableUser;
import com.cloud.game.manager.table.ddz.DdzPlayService;
import com.cloud.game.manager.table.ddz.DdzTable;
import com.cloud.game.manager.table.pdk.PdkPlayService;
import com.cloud.game.manager.table.pdk.PdkTable;
import com.cloud.game.manager.table.tractor.TractorPlayService;
import com.cloud.game.manager.table.tractor.TractorTable;
import msg.annotation.ProcessEnum;
import msg.registor.enums.TableState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 等待玩家出牌；超时自动处理（首家出最小单张，否则视为过）。
 *
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @since 1.0
 */
@ProcessEnum(TableState.IDLE_CARD)
public class IdleCardPlay extends AbstractTableHandle {

    private static final Logger logger = LoggerFactory.getLogger(IdleCardPlay.class);
    private static final long TRACTOR_LEAD_ROBOT_DELAY_MILLIS = 2_000L;

    @Override
    public boolean handle(Table table) {
        int seat = table.getOp().getCurrOpSeat();
        TableUser u = table.getSeatUser(seat);
        if (u != null && u.isRobot()
                && System.currentTimeMillis() >= table.getStateStartTime() + robotDelay(table)) {
            overTime(table);
            return false;
        }
        return super.handle(table);
    }

    private long randomRobotDelay() {
        return RobotOperationDelay.randomMillis();
    }

    private long robotDelay(Table table) {
        long delay = randomRobotDelay();
        if (table instanceof TractorTable
                && ((TractorTable) table)
                .getTractor().getLeadCombo() == null) {
            return Math.max(delay, TRACTOR_LEAD_ROBOT_DELAY_MILLIS);
        }
        return delay;
    }

    @Override
    public void overTime(Table table) {
        if (table instanceof PdkTable) {
            overTimePdk((PdkTable) table);
            return;
        }
        if (table instanceof TractorTable) {
            TractorPlayService.autoPlay(
                    (TractorTable) table, table.getOp().getCurrOpSeat());
            return;
        }
        DdzTable ddzTable = (DdzTable) table;
        int seat = table.getOp().getCurrOpSeat();
        TableUser u = table.getSeatUser(seat);
        if (u == null) {
            return;
        }
        logger.info("出牌超时自动处理, tableId: {}, seat: {}, userId: {}", table.getTableId(), seat, u.getUserId());
        if (DdzPlayService.autoPlayAi(ddzTable, u.getUserId())) {
            return;
        }
        if (ddzTable.getDdz().getLastHand() == null) {
            DdzPlayService.autoPlaySmallest(ddzTable, u.getUserId());
        } else {
            DdzPlayService.apply(ddzTable, u.getUserId(),
                    proto.GameProto.OpInfo.newBuilder().setChoice(proto.ConstProto.Operation.PASS).build());
        }
    }

    private void overTimePdk(PdkTable table) {
        int seat = table.getOp().getCurrOpSeat();
        TableUser u = table.getSeatUser(seat);
        if (u == null) return;
        logger.info("跑得快出牌超时, tableId: {}, seat: {}, userId: {}", table.getTableId(), seat, u.getUserId());
        if (PdkPlayService.autoPlayAi(table, u.getUserId())) return;
        if (table.getPdk().getLastHand() == null) {
            PdkPlayService.autoPlaySmallest(table, u.getUserId());
            return;
        }
        // 有牌必管：能压则不能 PASS，只能再走 AI/出牌；关不上才允许过
        if (table.canCurrentPlayerPass()) {
            PdkPlayService.apply(table, u.getUserId(),
                    proto.GameProto.OpInfo.newBuilder().setChoice(proto.ConstProto.Operation.PASS).build());
        }
    }
}
