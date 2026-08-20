package com.cloud.game.manager.table.state;

import com.cloud.game.manager.table.Table;
import com.cloud.game.manager.table.mj.state.MjDeal;
import msg.registor.HandleTypeRegister;
import msg.registor.enums.TableState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author cloud
 * @version 1.0
 * @date 2026-05-03
 * @className TableStateHandleManager
 * @description 桌子状态处理器管理器
 * @createDate 2025/10/20 16:53
 * @since 1.0
 */
public class TableStateHandleManager {

    private static final Logger logger = LoggerFactory.getLogger(TableStateHandleManager.class);

    private static final Map<TableState, AbstractTableHandle> STATE_TABLE_HANDLE_MAP = new HashMap<>();
    /**
     * 缺 Handle 只告警一次，避免桌循环刷屏。
     */
    private static final Set<String> MISSING_HANDLE_LOGGED = ConcurrentHashMap.newKeySet();

    static {
        HandleTypeRegister.initFactoryEnum(TableStateHandleManager.class, STATE_TABLE_HANDLE_MAP);
        // 麻将状态处理器位于独立兄弟包，必须作为单独扫描根注册。
        HandleTypeRegister.initFactoryEnum(MjDeal.class, STATE_TABLE_HANDLE_MAP);
        validateRequiredHandles();
    }

    private static void validateRequiredHandles() {
        Set<TableState> required = EnumSet.of(
                TableState.MJ_DEAL,
                TableState.MJ_PLAY,
                TableState.MJ_DISCARD,
                TableState.MJ_CLAIM);
        required.removeAll(STATE_TABLE_HANDLE_MAP.keySet());
        if (!required.isEmpty()) {
            throw new ExceptionInInitializerError("麻将状态处理器未注册: " + required);
        }
    }

    static boolean hasHandle(TableState state) {
        return STATE_TABLE_HANDLE_MAP.containsKey(state);
    }

    /**
     * 桌子状态处理器处理
     *
     * @param table 牌局
     */
    public static boolean handle(Table table) {
        AbstractTableHandle handle = STATE_TABLE_HANDLE_MAP.get(table.getTableState());

        if (handle == null) {
            fallbackMissingHandle(table);
            return false;
        }
        boolean exit = false;
        if (logger.isDebugEnabled()) {
            logger.debug("桌子状态处理开始, tableId: {}, state:{}", table.getTableId(), table.getTableState());
        }
        try {
            exit = handle.handle(table);
        } catch (Exception e) {
            table.addErrorTime();
            logger.error("桌子状态处理异常, tableId: {}, state: {}",
                    table.getTableId(), table.getTableState(), e);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("桌子状态处理结束, tableId: {}, state:{}", table.getTableId(), table.getTableState());
        }
        return exit;
    }

    /**
     * 缺状态处理器时：首次 ERROR，并按枚举 next 或 TABLE_OVER 兜底推进，避免卡死。
     */
    private static void fallbackMissingHandle(Table table) {
        TableState state = table.getTableState();
        String key = table.getTableId() + ":" + state.name();
        if (MISSING_HANDLE_LOGGED.add(key)) {
            logger.error("table:{} state:{} no handle, 兜底切下一状态", table.getTableId(), state);
        }
        TableState next = state.getNext();
        long now = System.currentTimeMillis();
        if (next != null) {
            table.upNextStateWithTime(next, now);
        } else {
            table.upNextStateWithTime(TableState.TABLE_OVER, now);
        }
    }
}
