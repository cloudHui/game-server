package com.cloud.hub.game.domain.state;

import com.cloud.hub.game.domain.table.Table;
import utils.registry.annotation.ProcessEnum;
import utils.registry.enums.TableState;

/**
 * 亮牌/扣底阶段处理器。
 * <p>
 * <b>职责与使用场景：</b>
 * <ul>
 *   <li>绑定状态 {@link TableState#IDLE_SHOW_CARD}；</li>
 *   <li>涵盖斗地主的地主明牌确认，以及拖拉机的庄家拿底与扣底流程；</li>
 *   <li>每 tick 委托 {@link Table#onIdleShowCardHandle()}，超时触发 {@link Table#onIdleShowCardOverTime()}，
 *       实现各玩法的纯多态隔离，消灭外层类型强转与硬编码分支。</li>
 * </ul>
 */
@ProcessEnum(TableState.IDLE_SHOW_CARD)
public class IdleShowCard extends AbstractTableHandle {

    /** 拖拉机扣底时限兼容常量 */
    public static final int TRACTOR_BURY_SECONDS = com.cloud.hub.game.domain.tractor.TractorTable.TRACTOR_BURY_SECONDS;

    @Override
    public boolean handle(Table table) {
        if (table.onIdleShowCardHandle()) {
            return false;
        }
        return super.handle(table);
    }

    @Override
    public void overTime(Table table) {
        table.onIdleShowCardOverTime();
    }
}
