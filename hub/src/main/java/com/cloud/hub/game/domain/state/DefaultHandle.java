package com.cloud.hub.game.domain.state;

import com.cloud.hub.game.domain.table.Table;
import msg.annotation.ProcessEnum;
import msg.registor.enums.TableState;

/**
 * 开局动画/发牌过渡阶段处理器。
 * <p>
 * <b>职责与使用场景：</b>
 * <ul>
 *   <li>绑定状态 {@link TableState#START_ANI}；</li>
 *   <li>先委托 {@link Table#onStartAniTiming()} 允许特定玩法子类接管定制（例如拖拉机逐张发牌倒计时动画）；</li>
 *   <li>若子类未接管则执行通用倒计时超时，自动推进至下一个游戏状态。</li>
 * </ul>
 */
@ProcessEnum(TableState.START_ANI)
public class DefaultHandle extends AbstractTableHandle {

    @Override
    public boolean handle(Table table) {
        if (table.onStartAniTiming()) {
            return false;
        }
        return super.handle(table);
    }

    @Override
    protected void overTime(Table table) {
        table.upNextState();
    }
}
