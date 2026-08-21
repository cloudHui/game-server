package com.cloud.hub.game.domain.replay;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Hub 回放落盘根目录。启动时由 DataPathResolver 冻结。 */
public final class ReplayDirectories {
    private static volatile Path root = Paths.get("replay").toAbsolutePath().normalize();

    private ReplayDirectories() {
    }

    public static void configure(Path path) {
        if (path == null) throw new IllegalArgumentException("回放目录不能为空");
        root = path.toAbsolutePath().normalize();
    }

    public static Path root() {
        return root;
    }
}
