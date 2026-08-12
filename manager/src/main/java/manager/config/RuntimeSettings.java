package manager.config;

import manager.util.Platform;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

public final class RuntimeSettings {
    private final Path file;
    private Path jdkHome;

    public RuntimeSettings(Path root) {
        file = root.resolve("manager/config.properties");
        Path bundled = root.resolve("../../jdk-17").normalize().toAbsolutePath();
        jdkHome = Files.isExecutable(bundled.resolve("bin").resolve(Platform.executable("java")))
            ? bundled : Path.of(System.getProperty("java.home"));
        load();
    }

    public synchronized Path jdkHome() { return jdkHome; }
    public synchronized Path javaExecutable() { return tool("java"); }
    public synchronized Path jpackageExecutable() { return tool("jpackage"); }

    public synchronized void setJdkHome(Path value) throws IOException {
        Path normalized = value.toAbsolutePath().normalize();
        Path java = normalized.resolve("bin").resolve(Platform.executable("java"));
        Path jpackage = normalized.resolve("bin").resolve(Platform.executable("jpackage"));
        if (!Files.isExecutable(java) || !Files.isExecutable(jpackage))
            throw new IOException("目录不是完整 JDK 17，缺少 java 或 jpackage: " + normalized);
        jdkHome = normalized;
        Properties properties = new Properties();
        properties.setProperty("jdk.home", jdkHome.toString());
        Files.createDirectories(file.getParent());
        try (Writer writer = Files.newBufferedWriter(file)) { properties.store(writer, "Server Manager settings"); }
    }

    private Path tool(String name) { return jdkHome.resolve("bin").resolve(Platform.executable(name)); }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
            String saved = properties.getProperty("jdk.home");
            if (saved != null && !saved.isBlank()) jdkHome = Path.of(saved).toAbsolutePath().normalize();
        } catch (IOException ignored) {}
    }
}
