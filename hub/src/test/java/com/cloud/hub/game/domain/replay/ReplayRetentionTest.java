package com.cloud.hub.game.domain.replay;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReplayRetentionTest {
    @Test
    public void keepsTwentyNewestFilesPerHumanAndDeletesTheRest() throws Exception {
        Path root = Files.createTempDirectory("replay-keep");
        List<Path> userSeven = new ArrayList<Path>();
        for (int i = 0; i < 21; i++) {
            Path file = write(root, "2026-08-01", "7_" + i + ".txt", 7, 1000L + i);
            userSeven.add(file);
        }
        Path shared = write(root, "2026-08-02", "shared.txt", 7, 8, 50L);
        Path onlyEight = write(root, "2026-08-02", "8_only.txt", 8, 40L);
        int removed = ReplayRetention.prune(root);
        assertEquals(1, removed);
        assertFalse(Files.exists(userSeven.get(0)));
        for (int i = 1; i < 21; i++) assertTrue(Files.exists(userSeven.get(i)));
        assertTrue(Files.exists(shared));
        assertTrue(Files.exists(onlyEight));
    }

    @Test
    public void visibleForUserReturnsAtMostTwentyNewest() throws Exception {
        Path root = Files.createTempDirectory("replay-visible");
        for (int i = 0; i < 25; i++) {
            write(root, "2026-01-01", "u_" + i + ".txt", 3, 2000L + i);
        }
        write(root, "2019-01-01", "old.txt", 3, 1L);
        List<ReplayRetention.FileRef> visible = ReplayRetention.visibleForUser(ReplayRetention.scan(root), 3);
        assertEquals(20, visible.size());
        assertEquals("u_24.txt", visible.get(0).path.getFileName().toString());
        assertEquals("u_5.txt", visible.get(19).path.getFileName().toString());
    }

    private static Path write(Path root, String day, String name, int userId, long mtime) throws Exception {
        return write(root, day, name, userId, 0, mtime);
    }

    private static Path write(Path root, String day, String name, int userA, int userB, long mtime) throws Exception {
        Path dir = root.resolve(day);
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        StringBuilder text = new StringBuilder("=== 玩家 ===\n座0: userId=").append(userA).append(", nick=a\n");
        if (userB != 0) text.append("座1: userId=").append(userB).append(", nick=b\n");
        Files.write(file, text.toString().getBytes(StandardCharsets.UTF_8));
        file.toFile().setLastModified(mtime);
        return file;
    }
}
