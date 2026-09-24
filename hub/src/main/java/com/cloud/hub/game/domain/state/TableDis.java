package com.cloud.hub.game.domain.state;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.domain.table.Table;
import utils.registry.annotation.ProcessEnum;
import utils.registry.enums.TableState;

/**
 * 牌桌解散状态处理器。
 * <p>
 * 停止牌桌主循环并从 TableManager 异步注销，释放内存资源并向大厅服务同步销毁事件。
 *
 * @author cloud
 */
@ProcessEnum(TableState.TABLE_DIS)
public class TableDis extends AbstractTableHandle {

    @Override
    public boolean onTiming(Table table) {
        Game.getInstance().getTableManager().removeTableAsync(table.getTableId());
        return true;
    }
}
