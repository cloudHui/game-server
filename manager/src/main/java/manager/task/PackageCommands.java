package manager.task;

import manager.config.RuntimeSettings;
import manager.util.Platform;
import java.nio.file.Path;
import java.util.List;

public final class PackageCommands {
    private final Path root;
    private final RuntimeSettings settings;
    public PackageCommands(Path root, RuntimeSettings settings) { this.root = root; this.settings = settings; }

    public List<String> buildAll() {
        return List.of(Platform.mavenExecutable(), "-f", "pom.xml", "install", "-DskipTests");
    }

    public List<String> applicationImage() {
        Path jar = root.resolve("manager/target/ServerManager.jar");
        return List.of(settings.jpackageExecutable().toString(), "--type", "app-image", "--name", "ServerManager",
            "--input", jar.getParent().toString(), "--main-jar", jar.getFileName().toString(),
            "--main-class", "manager.ServerManager", "--dest", root.resolve("build/manager-package").toString(),
            "--app-version", "1.0");
    }
}
