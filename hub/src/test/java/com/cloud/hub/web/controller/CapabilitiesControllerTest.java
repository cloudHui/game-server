package com.cloud.hub.web.controller;

import com.cloud.hub.bootstrap.ComponentState;
import com.cloud.hub.bootstrap.ManagedComponent;
import com.cloud.hub.bootstrap.HubComponent;
import com.cloud.hub.bootstrap.HubLifecycle;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class CapabilitiesControllerTest {
    @Test
    public void failedGameDoesNotReportReadyOrOnlineTables() {
        HubLifecycle lifecycle = new HubLifecycle();
        try {
            lifecycle.start(Arrays.asList(
                    ok(HubComponent.STORAGE),
                    ok(HubComponent.LOBBY),
                    exploding(HubComponent.GAME)));
            fail("expected startup failure");
        } catch (IllegalStateException ignored) {
            // expected
        }
        Map<String, Object> body = new CapabilitiesController(lifecycle).capabilities();
        assertEquals(Boolean.FALSE, body.get("ready"));
        assertEquals(Boolean.FALSE, body.get("center"));
        assertEquals(Boolean.FALSE, body.get("game"));
        assertEquals(Boolean.FALSE, body.get("onlineTables"));
        assertEquals(ComponentState.FAILED, lifecycle.state(HubComponent.GAME));
        assertFalse(lifecycle.isReady());
    }

    @Test
    public void unstartedLifecycleIsNotReady() {
        Map<String, Object> body = new CapabilitiesController(new HubLifecycle()).capabilities();
        assertEquals(Boolean.FALSE, body.get("ready"));
        assertEquals(Boolean.FALSE, body.get("degraded"));
        assertEquals(Boolean.FALSE, body.get("center"));
        assertEquals("一体化服务联网牌桌尚未就绪", body.get("message"));
    }

    private static ManagedComponent ok(HubComponent name) {
        return component(name, false);
    }

    private static ManagedComponent exploding(HubComponent name) {
        return component(name, true);
    }

    private static ManagedComponent component(final HubComponent name, final boolean boom) {
        return new ManagedComponent() {
            public HubComponent component() { return name; }
            public void start() { if (boom) throw new IllegalStateException("boom"); }
            public void stop() { }
        };
    }
}
