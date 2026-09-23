package com.cloud.hub.game.domain.state;

import com.cloud.hub.game.domain.table.Table;
import utils.registry.annotation.ProcessEnum;
import utils.registry.enums.TableState;

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

    @Override
    public boolean handle(Table table) {
        if (table.onIdleRobHandle()) {
            return false;
        }
        return super.handle(table);
    }

    @Override
    public void overTime(Table table) {
        table.onRobOverTime();
    }
}
