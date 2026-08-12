package manager.util;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ProjectPaths {
    private ProjectPaths() {}

    public static Path findRoot() {
        String configured = System.getProperty("server.root");
        if (configured != null && !configured.isBlank()) return Path.of(configured).toAbsolutePath().normalize();
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if (isRoot(current)) return current;
        if (current.getParent() != null && isRoot(current.getParent())) return current.getParent();
        return current;
    }

    private static boolean isRoot(Path path) {
        return Files.isRegularFile(path.resolve("pom.xml")) && Files.isDirectory(path.resolve("game"));
    }
}
