package com.cloud.hub.game.client.handle;

import com.cloud.hub.game.Game;
import com.cloud.hub.game.domain.table.Table;
import net.client.Sender;
import net.message.TCPMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import proto.ConstProto;

/**
 * 牌桌消息处理辅助工具类。
 * <p>
 * 统一承揽“查桌 -> 判空提示/回包 -> 串行切入桌线程 -> 统一异常捕获”的重复样板流程，
 * 简化 Handler 编写，让业务聚焦纯粹的桌内状态处理。
 */
public final class TableHandlerHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(TableHandlerHelper.class);

    private TableHandlerHelper() {
    }

    /**
     * 桌内业务逻辑函数接口。
     */
    @FunctionalInterface
    public interface TableAction {
        /**
         * 在桌串行线程中执行的具体业务逻辑。
         *
         * @param table 当前目标桌子实例
         * @throws Exception 业务异常
         */
        void execute(Table table) throws Exception;
    }

    /**
     * 查桌并直接在桌串行线程内执行逻辑（若桌子不存在则静默忽略）。
     *
     * @param tableId    目标桌号
     * @param actionName 业务动作名称（用于定位与排错）
     * @param action     桌内业务逻辑
     * @return 恒为 true（适配 Handler.handler 返回值约定）
     */
    public static boolean dispatch(long tableId, String actionName, TableAction action) {
        Table table = Game.getInstance().getTableManager().getTable(tableId);
        if (table == null) {
            return true;
        }
        table.execute(actionName, () -> {
            try {
                action.execute(table);
            } catch (Throwable e) {
                LOGGER.error("执行桌内逻辑异常 [action={}, tableId={}]", actionName, tableId, e);
            }
        });
        return true;
    }

    /**
     * 查桌并切入桌串行线程执行（若桌子不存在则向客户端回送 TABLE_NULL_VALUE 错误码）。
     *
     * @param sender     消息发送方
     * @param tableId    目标桌号
     * @param actionName 业务动作名称（用于定位与排错）
     * @param action     桌内业务逻辑
     * @return 恒为 true
     */
    public static boolean dispatchOrReplyNull(Sender sender, long tableId, String actionName, TableAction action) {
        Table table = Game.getInstance().getTableManager().getTable(tableId);
        if (table == null) {
            LOGGER.warn("桌子不存在 [action={}, tableId={}]", actionName, tableId);
            if (sender != null) {
                sender.sendMessage(TCPMessage.newInstance(ConstProto.Result.TABLE_NULL_VALUE));
            }
            return true;
        }
        table.execute(actionName, () -> {
            try {
                action.execute(table);
            } catch (Throwable e) {
                LOGGER.error("执行桌内逻辑异常 [action={}, tableId={}]", actionName, tableId, e);
            }
        });
        return true;
    }
}
