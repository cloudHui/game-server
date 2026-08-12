package manager.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ManagerLog {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final Path file;

    public ManagerLog(Path root) { file = root.resolve("logs/manager/manager.log"); }
    public Path file() { return file; }

    public synchronized void write(String message) {
        try {
            Files.createDirectories(file.getParent());
            String line = TIME.format(LocalDateTime.now()) + " " + message + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {}
    }
}
