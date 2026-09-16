package com.cloud.hub.storage;

import org.junit.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.assertEquals;

public class DataPathResolverTest {
    @Test
    public void relativePathsResolveAgainstConfiguredRoot() {
        DataPathResolver resolver = new DataPathResolver("/srv/game");
        assertEquals(Paths.get("/srv/game").toAbsolutePath().resolve("data/lobby.db").normalize(), resolver.resolve("data/lobby.db"));
    }

    @Test
    public void absolutePathsRemainAbsolute() {
        DataPathResolver resolver = new DataPathResolver("/srv/game");
        Path abs = Paths.get("/mnt/game/replay").toAbsolutePath().normalize();
        assertEquals(abs, resolver.resolve(abs.toString()));
    }
}
