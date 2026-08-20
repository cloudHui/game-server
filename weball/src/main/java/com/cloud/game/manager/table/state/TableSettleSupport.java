package com.cloud.game.manager.table.state;

import com.cloud.game.Game;
import com.cloud.game.manager.table.Table;
import com.cloud.game.manager.table.ddz.DdzSettleService;
import com.cloud.game.manager.table.mj.MjSettleService;
import com.cloud.game.manager.table.mj.MjTable;
import com.cloud.game.manager.table.pdk.PdkSettleService;
import com.cloud.game.manager.table.tractor.TractorSettleService;

/**
 * 总结算发送与散桌的公共入口，避免 TableOverBridge / ReqOpHandle 重复分支。
 */
public final class TableSettleSupport {

    private TableSettleSupport() {
    }

    public static void sendFinalGameResult(Table table) {
        if (table.getGameType() == 1) {
            MjSettleService.sendGameResult((MjTable) table);
        } else if (table.getGameType() == 3) {
            PdkSettleService.sendGameResult(table);
        } else if (table.getGameType() == 4) {
            TractorSettleService.sendGameResult(table);
        } else {
            DdzSettleService.sendGameResult(table);
        }
    }

    public static void sendFinalResultAndRemove(Table table) {
        if (table.isMultiRound()) {
            sendFinalGameResult(table);
        }
        Game.getInstance().getTableManager().removeTableAsync(table.getTableId());
    }
}
