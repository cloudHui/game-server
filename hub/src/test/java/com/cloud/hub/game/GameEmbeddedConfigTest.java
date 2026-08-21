package com.cloud.hub.game;

import org.junit.Test;

public class GameEmbeddedConfigTest {
    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveWorkerCount() {
        Game.getInstance().startEmbedded("/tmp/unused-hub-score.db", 0, 1000, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveQueueCapacity() {
        Game.getInstance().startEmbedded("/tmp/unused-hub-score.db", 1, 0, 1);
    }
}
