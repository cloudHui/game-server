package com.cloud.weball.game.domain.state;

import com.cloud.weball.game.domain.table.RobotOperationDelay;
import com.cloud.weball.game.domain.table.Table;
import com.cloud.weball.game.domain.table.TableUser;
import com.cloud.weball.game.domain.ddz.DdzBidService;
import com.cloud.weball.game.domain.ddz.DdzTable;
import com.cloud.weball.game.domain.tractor.TractorBidService;
import com.cloud.weball.game.domain.tractor.TractorTable;
import msg.annotation.ProcessEnum;
import msg.registor.enums.TableState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 等待叫地主；超时视为「不叫」。
 *
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @since 1.0
 */
@ProcessEnum(TableState.IDLE_ROB)
public class IdleRob extends AbstractTableHandle {

    private static final Logger logger = LoggerFactory.getLogger(IdleRob.class);

    @Override
    public boolean handle(Table table) {
        if (table instanceof TractorTable) {
            long now = System.currentTimeMillis();
            int seat = table.getOp().getCurrOpSeat();
            TableUser u = table.getSeatUser(seat);
            if (u != null && u.isRobot()
                    && now >= table.getStateStartTime() + randomRobotDelay()) {
                TractorBidService.autoBid(
                        (TractorTable) table, seat);
                return false;
            }
            if (now >= table.getStateStartTime()
                    + TractorBidService.DECLARE_SECONDS * 1000L) {
                overTime(table);
            }
            return false;
        }
        int seat = table.getOp().getCurrOpSeat();
        TableUser u = table.getSeatUser(seat);
        if (u != null && u.isRobot()
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
    public void overTime(Table table) {
        if (table instanceof TractorTable) {
            logger.info("拖拉机亮主超时, tableId: {}", table.getTableId());
            TractorBidService.onTimeout(
                    (TractorTable) table);
            return;
        }
        logger.info("叫分/抢地主超时, tableId: {}", table.getTableId());
        DdzTable ddzTable = (DdzTable) table;
        DdzBidService.onBidTimeout(ddzTable);
    }
}
