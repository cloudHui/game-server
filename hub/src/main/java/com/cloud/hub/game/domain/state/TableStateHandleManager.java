package com.cloud.hub.game.domain.state;

import com.cloud.hub.game.domain.table.Table;
import msg.annotation.ProcessEnum;
import msg.registor.enums.TableState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.registry.HandlerRegistry;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 桌子状态处理器全局管理器。
 * <p>
 * <b>职责与使用场景：</b>
 * <ul>
 * <li>负责管理扑克/麻将桌子核心生命周期状态机（Waiting、Deal、Rob、Play、Discard、Claim 等）；</li>
 * <li>依托 {@link HandlerRegistry#buildSingle} 在类加载阶段自动扫描
 * {@code com.cloud.hub.game.domain}
 * 包下所有标注 {@link ProcessEnum} 的 {@link AbstractTableHandle} 实现类并构建不可变单例映射；</li>
 * <li>在 {@code Table.tick()} 周期循环中按当前状态 {@link TableState}
 * 路由分发给对应的状态处理器执行。</li>
 * </ul>
 *
 * @author cloud
 * @version 1.0
 */
public class TableStateHandleManager {

    private static final Logger logger = LoggerFactory.getLogger(TableStateHandleManager.class);

    private static final Map<TableState, AbstractTableHandle> STATE_TABLE_HANDLE_MAP = HandlerRegistry
            .buildSingle("com.cloud.hub.game.domain", AbstractTableHandle.class, ProcessEnum.class, TableState.class);
    /**
     * 缺 Handle 只告警一次，避免桌循环刷屏。
     */
    private static final Set<String> MISSING_HANDLE_LOGGED = ConcurrentHashMap.newKeySet();

    static {
        // 校验麻将核心状态处理器是否都已注册，缺少任何一个则抛出异常终止初始化。
        validateRequiredHandles();
    }

    /**
     * 校验麻将核心状态处理器是否都已注册，缺少任何一个则抛出异常终止初始化。
     */
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

    /**
     * 校验指定状态是否已注册对应的处理器。
     *
     * @param state 状态枚举
     * @return true 表示已注册，false 表示未注册
     */
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
