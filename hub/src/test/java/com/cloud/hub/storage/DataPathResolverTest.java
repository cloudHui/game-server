package com.cloud.hub.storage;

import org.junit.Test;
import java.nio.file.Paths;
import static org.junit.Assert.assertEquals;

public class DataPathResolverTest {
    @Test public void relativePathsResolveAgainstConfiguredRoot() {
        DataPathResolver resolver = new DataPathResolver("/srv/game");
        assertEquals(Paths.get("/srv/game/data/lobby.db"), resolver.resolve("data/lobby.db"));
    }
    @Test public void absolutePathsRemainAbsolute() {
        DataPathResolver resolver = new DataPathResolver("/srv/game");
        assertEquals(Paths.get("/mnt/game/replay"), resolver.resolve("/mnt/game/replay"));
    }
}
