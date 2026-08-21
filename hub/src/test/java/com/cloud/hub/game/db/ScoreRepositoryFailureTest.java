package com.cloud.hub.game.db;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

public class ScoreRepositoryFailureTest {
    @Test(expected = IllegalStateException.class)
    public void initializeFailsWhenParentPathIsAFile() throws Exception {
        Path blocker = Files.createTempFile("hub-score-block", ".bin");
        Path db = blocker.resolve("lobby.db");
        java.util.concurrent.ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            new ScoreRepository(db.toString(), new DatabaseExecutorManager(pool));
        } finally {
            pool.shutdownNow();
        }
    }
}
