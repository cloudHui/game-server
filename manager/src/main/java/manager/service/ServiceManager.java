package manager.service;

import manager.model.ServiceCatalog;
import manager.model.ServiceSpec;
import manager.model.ServiceState;
import manager.config.RuntimeSettings;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ServiceManager {
    private final Path root;
    private final RuntimeSettings settings;
    private final PortProbe ports = new PortProbe();
    private final ProcessFinder processes = new ProcessFinder();
    private final Map<String, Process> owned = new ConcurrentHashMap<>();

    public ServiceManager(Path root, RuntimeSettings settings) { this.root = root; this.settings = settings; }
    public Path root() { return root; }

    public synchronized void start(ServiceSpec spec) throws IOException {
        ServiceState current = state(spec);
        if (current.status() != ServiceState.Status.STOPPED)
            throw new IOException(spec.name() + " 不能启动：" + current.displayText());
        Path jar = root.resolve(spec.jarPath()).normalize();
        if (!Files.isRegularFile(jar)) throw new IOException("找不到 " + jar);
        Path log = logFile(spec);
        Files.createDirectories(log.getParent());
        ProcessBuilder builder = new ProcessBuilder(
            settings.javaExecutable().toString(), "-Dfile.encoding=UTF-8", "-DLOG_HOME=" + root.resolve("logs"),
            "-Xms" + spec.heap(), "-Xmx" + spec.heap(), "-XX:+UseG1GC", "-jar", jar.toString());
        builder.directory(root.toFile()).redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        Process process = builder.start();
        owned.put(spec.id(), process);
        try {
            if (!waitUntilReady(spec.port(), process, Duration.ofSeconds(20))) {
                if (!process.isAlive()) owned.remove(spec.id());
                throw new IOException(spec.name() + " 未通过端口检查，请查看 " + log);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("启动检查被中断", error);
        }
    }

    public synchronized void stop(ServiceSpec spec) throws IOException {
        Process process = owned.get(spec.id());
        if (process == null || !process.isAlive())
            throw new IOException("拒绝停止 " + spec.name() + "：该进程不是本次管理器启动的");
        try {
            process.destroy();
            if (!process.waitFor(12, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("停止被中断", error);
        } finally { owned.remove(spec.id()); }
    }

    public void restart(ServiceSpec spec) throws IOException { stop(spec); start(spec); }

    public ServiceState state(ServiceSpec spec) {
        Process own = owned.get(spec.id());
        boolean managerOwned = own != null && own.isAlive();
        Optional<ProcessHandle> found = managerOwned ? Optional.of(own.toHandle()) : processes.find(spec);
        boolean portOpen = ports.isOpen(spec.port());
        if (found.isPresent() && portOpen) return new ServiceState(ServiceState.Status.RUNNING, found.get().pid(), managerOwned);
        if (found.isPresent()) return new ServiceState(ServiceState.Status.PROCESS_WITHOUT_PORT, found.get().pid(), managerOwned);
        if (portOpen) return new ServiceState(ServiceState.Status.PORT_CONFLICT, -1, false);
        return new ServiceState(ServiceState.Status.STOPPED, -1, false);
    }

    public void startAll() throws IOException {
        for (ServiceSpec spec : ServiceCatalog.ALL) start(spec);
    }

    public void stopAllOwned() {
        List<ServiceSpec> reversed = new ArrayList<>(ServiceCatalog.ALL);
        Collections.reverse(reversed);
        for (ServiceSpec spec : reversed) {
            Process process = owned.get(spec.id());
            if (process != null && process.isAlive()) try { stop(spec); } catch (IOException ignored) {}
        }
    }

    public Path logFile(ServiceSpec spec) { return root.resolve("logs").resolve(spec.id()).resolve("console.out"); }

    private boolean waitUntilReady(int port, Process process, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline && process.isAlive()) {
            if (ports.isOpen(port)) return true;
            Thread.sleep(300);
        }
        return ports.isOpen(port);
    }
}
