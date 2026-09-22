package com.cloud.hub.bootstrap;

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Hub 生命周期管理器（{@link HubLifecycle}）状态机与启停编排单元测试。
 *
 * <p><b>覆盖核心场景：</b>
 * <ul>
 *   <li>组件启动中途失败时，必须按逆序安全回滚已启动组件并更新对应状态；</li>
 *   <li>任一组件处于降级态（DEGRADED）时系统整体不可标记为就绪（READY）；</li>
 *   <li>严格校验依赖组件启动顺序，禁止越级越序启动。</li>
 * </ul>
 */
public class HubLifecycleTest {

    /**
     * 验证：当某一组件启动抛出异常时，前序已成功启动的组件能够按倒序依次执行 stop 回滚。
     */
    @Test
    public void failureRollsBackStartedComponentsInReverseOrder() {
        HubLifecycle lifecycle = new HubLifecycle();
        StringBuilder stops = new StringBuilder();
        try {
            lifecycle.start(Arrays.asList(
                    component(HubComponent.STORAGE, false, stops),
                    component(HubComponent.LOBBY, false, stops),
                    component(HubComponent.GAME, true, stops)));
            fail("expected startup failure");
        } catch (IllegalStateException expected) {
            // LOBBY 后启动，故回滚时先停止 LOBBY，再停止 STORAGE
            assertEquals("LOBBYSTORAGE", stops.toString());
            assertEquals(ComponentState.STOPPED, lifecycle.state(HubComponent.STORAGE));
            assertEquals(ComponentState.STOPPED, lifecycle.state(HubComponent.LOBBY));
            assertEquals(ComponentState.FAILED, lifecycle.state(HubComponent.GAME));
            assertFalse(lifecycle.isReady());
            assertFalse(lifecycle.isDegraded());
        }
    }

    /**
     * 验证：即使核心组件就绪，只要存在降级组件（如 Web 管理后台降级），整体就绪状态仍应受阻。
     */
    @Test
    public void degradedComponentPreventsReady() {
        HubLifecycle lifecycle = new HubLifecycle();
        lifecycle.markReady(HubComponent.STORAGE);
        lifecycle.markReady(HubComponent.LOBBY);
        lifecycle.markReady(HubComponent.GAME);
        lifecycle.markReady(HubComponent.GATEWAY);
        lifecycle.markDegraded(HubComponent.WEB);
        assertTrue(lifecycle.isDegraded());
        assertFalse(lifecycle.isReady());
        assertEquals(ComponentState.DEGRADED, lifecycle.state(HubComponent.WEB));
    }

    /**
     * 验证：组件必须按照拓扑依赖顺序启动，若缺失前置组件（如直接启动 GAME）应拒绝并抛出异常。
     */
    @Test(expected = IllegalArgumentException.class)
    public void rejectsComponentsStartedOutOfOrder() {
        new HubLifecycle().start(Arrays.asList(component(HubComponent.GAME, false, new StringBuilder())));
    }

    /**
     * 构造用于测试生命周期流转的轻量级组件桩对象。
     *
     * @param name  组件枚举类型
     * @param fail  是否在启动时模拟抛出异常
     * @param stops 用于记录 stop 顺序的字符串缓冲区
     * @return 可受管组件实例
     */
    private static ManagedComponent component(final HubComponent name, final boolean fail,
                                              final StringBuilder stops) {
        return new ManagedComponent() {
            public HubComponent component() { return name; }
            public void start() { if (fail) throw new IllegalStateException("boom"); }
            public void stop() { stops.append(name.name()); }
        };
    }
}

