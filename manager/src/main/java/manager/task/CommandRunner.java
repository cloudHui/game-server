package manager.task;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class CommandRunner {
    private final Path workingDirectory;
    public CommandRunner(Path workingDirectory) { this.workingDirectory = workingDirectory; }

    public void run(List<String> command, Consumer<String> output) throws IOException {
        output.accept("$ " + String.join(" ", command));
        Process process = new ProcessBuilder(command).directory(workingDirectory.toFile()).redirectErrorStream(true).start();
        try (BufferedReader reader = process.inputReader(StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) output.accept(line);
        }
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) throw new IOException("命令退出码 " + exitCode);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            process.destroy();
            throw new IOException("命令被中断", error);
        }
    }
}
