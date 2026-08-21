package com.cloud.hub.game.domain.replay;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

public class ReplayRecorderFailureTest {
    @Test
    public void saveOnFileBlockingReplayDirDoesNotWriteTxt() throws Exception {
        Path parent = Files.createTempDirectory("hub-replay-block");
        Path blocker = parent.resolve("replay-root");
        Files.write(blocker, new byte[] { 1 });
        ReplayDirectories.configure(blocker);
        DdzReplayRecorder recorder = new DdzReplayRecorder(42L, 1);
        recorder.writeHeader("斗地主", 1, 3,
                Collections.singletonMap(0, 1), Collections.singletonMap(0, "p1"));
        recorder.save();
        assertFalse(Files.walk(parent)
                .anyMatch(path -> path.getFileName().toString().endsWith(".txt")));
        assertEquals(1, blocker.toFile().length());
    }
}
