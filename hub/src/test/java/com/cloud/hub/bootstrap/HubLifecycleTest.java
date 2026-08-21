package com.cloud.hub.bootstrap;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class HubLifecycleTest {
    @Test public void failureRollsBackStartedComponentsInReverseOrder() {
        HubLifecycle lifecycle = new HubLifecycle();
        StringBuilder stops = new StringBuilder();
        try {
            lifecycle.start(Arrays.asList(component(HubComponent.STORAGE, false, stops),
                    component(HubComponent.LOBBY, false, stops),
                    component(HubComponent.GAME, true, stops)));
            fail("expected startup failure");
        } catch (IllegalStateException expected) {
            assertEquals("LOBBYSTORAGE", stops.toString());
            assertEquals(ComponentState.STOPPED, lifecycle.state(HubComponent.STORAGE));
            assertEquals(ComponentState.STOPPED, lifecycle.state(HubComponent.LOBBY));
            assertEquals(ComponentState.FAILED, lifecycle.state(HubComponent.GAME));
            assertFalse(lifecycle.isReady());
            assertFalse(lifecycle.isDegraded());
        }
    }

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

    @Test(expected = IllegalArgumentException.class)
    public void rejectsComponentsStartedOutOfOrder() {
        new HubLifecycle().start(Arrays.asList(component(HubComponent.GAME, false, new StringBuilder())));
    }

    private static ManagedComponent component(final HubComponent name, final boolean fail,
                                              final StringBuilder stops) {
        return new ManagedComponent() {
            public HubComponent component() { return name; }
            public void start() { if (fail) throw new IllegalStateException("boom"); }
            public void stop() { stops.append(name.name()); }
        };
    }
}
