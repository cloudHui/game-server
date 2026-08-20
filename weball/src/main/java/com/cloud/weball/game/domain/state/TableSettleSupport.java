package com.cloud.weball.game.domain.state;

import com.cloud.weball.game.Game;
import com.cloud.weball.game.domain.table.Table;
import com.cloud.weball.game.domain.ddz.DdzSettleService;
import com.cloud.weball.game.domain.mj.MjSettleService;
import com.cloud.weball.game.domain.mj.MjTable;
import com.cloud.weball.game.domain.pdk.PdkSettleService;
import com.cloud.weball.game.domain.tractor.TractorSettleService;

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
