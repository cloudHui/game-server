package game.manager.table.state;

import game.manager.table.Table;
import game.manager.table.TractorTable;
import game.manager.table.tractor.TractorDealService;
import msg.annotation.ProcessEnum;
import msg.registor.enums.TableState;

/**
 * 默认阶段：斗地主等依赖超时切入下一状态；拖拉机发牌见 {@link TractorDealService}。
 */
@ProcessEnum(TableState.START_ANI)
public class DefaultHandle extends AbstractTableHandle {

	@Override
	public boolean handle(Table table) {
		if (table instanceof TractorTable) {
			return TractorDealService.onTiming((TractorTable) table);
		}
		return super.handle(table);
	}

	@Override
	protected void overTime(Table table) {
		table.upNextState();
	}
}
