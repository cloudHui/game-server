package com.cloud.hub.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hub 服务生命周期统一编排管理器。
 * <p>
 * 严格按照拓扑依赖顺序启动组件（STORAGE → LOBBY → GAME → GATEWAY → WEB），并在出现异常或停服事件时，
 * 逆序安全回滚释放资源，同时提供全组件的健康状态快照。单个方法均在 35 行以内。
 *
 * @author cloud
 * @version 1.0
 * @since 1.0
 */
@Component
public class HubLifecycle {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(HubLifecycle.class);
    /** 各受管组件当前的状态字典 */
    private final EnumMap<HubComponent, ComponentState> states = new EnumMap<>(HubComponent.class);
    /** 已成功启动的受管组件列表（用于停服时逆序优雅停机） */
    private final List<ManagedComponent> started = new ArrayList<>();

    public HubLifecycle() {
        for (HubComponent component : HubComponent.values()) {
            states.put(component, ComponentState.NOT_STARTED);
        }
    }

    /**
     * 按顺序启动传入的受管组件列表；若任意组件启动失败，自动逆序回滚已启动组件并抛出异常。
     *
     * @param components 待启动的受管组件列表
     */
    public synchronized void start(List<? extends ManagedComponent> components) {
        for (ManagedComponent managed : components) {
            HubComponent component = managed.component();
            requireExpectedOrder(component);
            states.put(component, ComponentState.STARTING);
            try {
                managed.start();
                started.add(managed);
                states.put(component, ComponentState.READY);
                logger.info("Hub component ready: {}", component);
            } catch (Exception e) {
                states.put(component, ComponentState.FAILED);
                rollback();
                throw new IllegalStateException("Hub component failed: " + component, e);
            }
        }
    }

    /**
     * 标记指定组件为 READY 就绪状态。
     *
     * @param component 目标组件
     */
    public synchronized void markReady(HubComponent component) {
        states.put(component, ComponentState.READY);
    }

    /**
     * 标记指定组件为 DEGRADED 降级状态。
     *
     * @param component 目标组件
     */
    public synchronized void markDegraded(HubComponent component) {
        states.put(component, ComponentState.DEGRADED);
    }

    /**
     * 获取指定组件当前的运行状态。
     *
     * @param component 目标组件
     * @return 组件状态
     */
    public synchronized ComponentState state(HubComponent component) {
        return states.get(component);
    }

    /**
     * 生成当前所有组件状态的快照字典（小写名称 -> 状态名）。
     *
     * @return 不可变的状态快照映射
     */
    public synchronized Map<String, String> snapshot() {
        Map<String, String> result = new LinkedHashMap<>();
        for (HubComponent component : HubComponent.values()) {
            result.put(component.name().toLowerCase(), states.get(component).name());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 检查当前系统是否所有组件均处于 READY 正常服务状态。
     *
     * @return true 为全部就绪
     */
    public synchronized boolean isReady() {
        for (ComponentState state : states.values()) {
            if (state != ComponentState.READY) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查当前系统是否有组件处于 DEGRADED 降级状态。
     *
     * @return true 为存在降级
     */
    public synchronized boolean isDegraded() {
        for (ComponentState state : states.values()) {
            if (state == ComponentState.DEGRADED) {
                return true;
            }
        }
        return false;
    }

    /**
     * 响应 Spring 容器关闭事件，执行全系统组件的逆序优雅停机与状态更新。
     */
    @EventListener(ContextClosedEvent.class)
    public synchronized void close() {
        for (HubComponent component : HubComponent.values()) {
            if (states.get(component) == ComponentState.READY || states.get(component) == ComponentState.DEGRADED) {
                states.put(component, ComponentState.STOPPING);
            }
        }
        rollback();
        for (HubComponent component : HubComponent.values()) {
            if (states.get(component) == ComponentState.STOPPING || states.get(component) == ComponentState.READY) {
                states.put(component, ComponentState.STOPPED);
            }
        }
    }

    /**
     * 严格校验组件启动顺序是否符合拓扑依赖规范。
     *
     * @param component 待校验组件
     */
    private void requireExpectedOrder(HubComponent component) {
        int expectedIndex = started.size();
        if (expectedIndex >= HubComponent.values().length
                || HubComponent.values()[expectedIndex] != component) {
            throw new IllegalArgumentException("Unexpected component order: " + component);
        }
    }

    /**
     * 逆序回滚并停止已启动的所有组件，确保异常发生时不留残留后台线程或未释放锁。
     */
    private void rollback() {
        for (int i = started.size() - 1; i >= 0; i--) {
            ManagedComponent managed = started.get(i);
            try {
                managed.stop();
            } catch (Exception e) {
                logger.error("Hub component stop failed: {}", managed.component(), e);
            } finally {
                states.put(managed.component(), ComponentState.STOPPED);
            }
        }
        started.clear();
    }
}
