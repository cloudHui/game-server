package com.cloud.hub.game.domain.state;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.domain.table.Table;
import com.cloud.hub.game.domain.ddz.DdzSettleService;
import com.cloud.hub.game.domain.mj.MjSettleService;
import com.cloud.hub.game.domain.mj.MjTable;
import com.cloud.hub.game.domain.pdk.PdkSettleService;
import com.cloud.hub.game.domain.tractor.TractorSettleService;

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
