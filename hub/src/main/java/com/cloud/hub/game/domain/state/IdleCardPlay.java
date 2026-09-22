package com.cloud.hub.game.domain.state;

import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.table.TableUser;
import msg.annotation.ProcessEnum;
import msg.registor.enums.TableState;

/**
 * 等待出牌超时自动出牌或PASS处理。
 *
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @since 1.0
 */
@ProcessEnum(TableState.IDLE_CARD)
public class IdleCardPlay extends AbstractTableHandle {

    @Override
    public boolean handle(Table table) {
        int seat = table.getOp().getCurrOpSeat();
        TableUser u = table.getSeatUser(seat);
        if (u != null && u.isRobot()
                && System.currentTimeMillis() >= table.getStateStartTime() + table.getRobotCardDelay()) {
            overTime(table);
            return false;
        }
        return super.handle(table);
    }

    @Override
    public void overTime(Table table) {
        table.onCardOverTime();
    }
}
