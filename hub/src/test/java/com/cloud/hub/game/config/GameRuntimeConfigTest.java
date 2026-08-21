package com.cloud.hub.game.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GameRuntimeConfigTest {
    @Test
    public void defaultOfflineTimeoutIs30Seconds() {
        GameRuntimeConfig.configure(30, 3000, 6000);
        assertEquals(30_000L, GameRuntimeConfig.webOfflineTimeoutMillis());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveOfflineTimeout() {
        GameRuntimeConfig.configure(0, 3000, 6000);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvertedRobotDelayRange() {
        GameRuntimeConfig.configure(30, 6000, 3000);
    }
}
