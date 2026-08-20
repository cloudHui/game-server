package com.cloud.weball.game.domain.state;

import com.cloud.weball.game.config.GameRuntimeConfig;
import com.cloud.weball.game.domain.table.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 处理网页心跳超时后的牌桌生命周期，不掺入具体玩法状态机。 */
public final class TableHeartbeatLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(TableHeartbeatLifecycle.class);

    private TableHeartbeatLifecycle() {
    }

    /**
     * 真人网页心跳超时后立即按已完成局数总结算并解散机器人体验桌。
     * 普通真人桌和纯机器人后台对局不在此处处理。
     */
    public static boolean closeExpiredRobotRoom(Table table) {
        long now = System.currentTimeMillis();
        long timeout = GameRuntimeConfig.webOfflineTimeoutMillis();
        if (!table.isRobotRoom() || !table.hasExpiredWebPlayersOnly(now, timeout)) return false;
        if (!table.beginClosing()) return true;
        logger.info("网页心跳超时，结束机器人房, tableId: {}, completedRounds: {}, timeoutMs: {}",
                table.getTableId(), table.getGameResult().getCompletedRounds(), timeout);
        TableSettleSupport.sendFinalResultAndRemove(table);
        return true;
    }
}
