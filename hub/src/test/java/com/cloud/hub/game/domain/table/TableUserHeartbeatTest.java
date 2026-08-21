package com.cloud.hub.game.domain.table;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TableUserHeartbeatTest {
    @Test
    public void expiresAfterConfiguredTimeout() {
        TableUser user = new TableUser(1, "", "p", 0);
        assertFalse(user.isWebHeartbeatExpired(1_000L, 30_000L));
        user.recordWebHeartbeat(0L);
        assertFalse(user.isWebHeartbeatExpired(30_000L, 30_000L));
        assertTrue(user.isWebHeartbeatExpired(30_001L, 30_000L));
    }
}
