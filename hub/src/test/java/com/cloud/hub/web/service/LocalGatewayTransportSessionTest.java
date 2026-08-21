package com.cloud.hub.web.service;

import org.junit.Test;

import static org.junit.Assert.*;

public class LocalGatewayTransportSessionTest {
    @Test
    public void laterBindForSameUserDropsPreviousSession() {
        LocalGatewayTransport transport = new LocalGatewayTransport();
        try {
            transport.bind("sess-old", 7, "u7", "七");
            transport.bind("sess-new", 7, "u7", "七");
            assertFalse(transport.isAuthenticated("sess-old"));
            assertTrue(transport.isAuthenticated("sess-new"));
        } finally {
            transport.shutdown();
        }
    }

    @Test
    public void unknownSessionSendAndWaitFails() throws Exception {
        LocalGatewayTransport transport = new LocalGatewayTransport();
        try {
            transport.sendAndWait("missing", 1, null, 1).handle((ok, error) -> {
                assertNotNull(error);
                assertEquals("会话不存在", error.getMessage());
                return null;
            }).get();
        } finally {
            transport.shutdown();
        }
    }
}
