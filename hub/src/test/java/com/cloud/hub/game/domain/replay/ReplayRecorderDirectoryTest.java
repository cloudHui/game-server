package com.cloud.hub.game.domain.replay;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertTrue;

public class ReplayRecorderDirectoryTest {
    @Test
    public void saveWritesUnderConfiguredReplayRoot() throws Exception {
        Path root = Files.createTempDirectory("hub-replay-root");
        ReplayDirectories.configure(root);
        DdzReplayRecorder recorder = new DdzReplayRecorder(7L, 1);
        recorder.writeHeader("斗地主", 1, 3,
                Collections.singletonMap(0, 1), Collections.singletonMap(0, "p1"));
        recorder.save();
        assertTrue(Files.walk(root)
                .anyMatch(path -> path.getFileName().toString().equals("7_1.txt")));
    }
}
