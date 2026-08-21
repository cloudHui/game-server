package com.cloud.hub.web.service;

import com.cloud.hub.storage.DataPathResolver;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class ReplayServiceUserLimitTest {
    @Test
    public void pageForUserCapsAtTwentyNewest() throws Exception {
        Path root = Files.createTempDirectory("replay-svc");
        Path replay = root.resolve("data/replay");
        for (int i = 0; i < 25; i++) {
            Path day = replay.resolve("2026-08-01");
            Files.createDirectories(day);
            Path file = day.resolve(i + "_1.txt");
            Files.write(file, ("座0: userId=9, nick=p\n").getBytes(StandardCharsets.UTF_8));
            file.toFile().setLastModified(1000L + i);
        }
        ReplayService service = new ReplayService(new DataPathResolver(root.toString()), "data/replay");
        Map<String, Object> page = service.pageForUser(9, 1, 50);
        assertEquals(20, page.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> replays = (List<Map<String, Object>>) page.get("replays");
        assertEquals(20, replays.size());
        assertEquals(Integer.valueOf(20), page.get("pageSize"));
        assertEquals("24_1.txt", replays.get(0).get("name"));
    }
}
