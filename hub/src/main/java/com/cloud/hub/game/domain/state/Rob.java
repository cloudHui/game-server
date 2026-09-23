package com.cloud.hub.game.domain.state;

import com.cloud.hub.game.domain.table.Table;
import utils.registry.annotation.ProcessEnum;
import utils.registry.enums.TableState;

/**
 * 广播叫分或抢地主选项，并进入 {@link TableState#IDLE_ROB}。
 *
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @since 1.0
 */
@ProcessEnum(TableState.ROB)
public class Rob extends AbstractTableHandle {

    @Override
    public boolean onTiming(Table table) {
        return table.onRobTiming();
    }
}
