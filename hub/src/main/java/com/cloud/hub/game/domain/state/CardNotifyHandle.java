package com.cloud.hub.game.domain.state;

import com.cloud.hub.game.domain.table.Table;
import utils.registry.annotation.ProcessEnum;
import utils.registry.enums.TableState;

/**
 * 出牌阶段：广播当前座位可操作项（出牌 / 过）。
 *
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @since 1.0
 */
@ProcessEnum(TableState.CARD)
public class CardNotifyHandle extends AbstractTableHandle {

    @Override
    public boolean onTiming(Table table) {
        return table.onCardTiming();
    }
}
