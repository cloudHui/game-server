package com.gamer.data.process;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 文本子进程执行 Module。 */
public final class ProcessRunner {

    private static final int BUFFER_SIZE = 32768;

    private ProcessRunner() {}

    /**
     * 执行子进程，统一处理输出、超时和中断清理。
     *
     * @param command
     *            命令及参数
     * @param options
     *            执行选项，可空
     * @param output
     *            输出消费者，可空
     * @return 退出码
     * @throws IOException
     *             执行失败
     */
    public static int run(List<String> command, ProcessOptions options, ProcessOutput output) throws IOException {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        ProcessOptions actual = options == null ? new ProcessOptions() : options;
        ProcessBuilder builder = new ProcessBuilder(command);
        configure(builder, actual);

        Process process = builder.start();
        AtomicReference<IOException> readError = new AtomicReference<>();
        Thread reader = new Thread(() -> readOutput(process, actual, output, readError), "process-output-reader");
        reader.setDaemon(true);
        reader.start();

        try {
            boolean finished = actual.timeoutMillis <= 0
                ? waitFor(process)
                : process.waitFor(actual.timeoutMillis, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                join(reader);
                throw new IOException("进程执行超时（" + actual.timeoutMillis + "ms）");
            }
            join(reader);
            if (readError.get() != null) {
                throw readError.get();
            }
            return process.exitValue();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("进程执行被中断", e);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static void configure(ProcessBuilder builder, ProcessOptions options) {
        builder.redirectErrorStream(!options.discardError);
        if (options.workDirectory != null) {
            builder.directory(options.workDirectory);
        }
        if (options.inputFile != null) {
            builder.redirectInput(ProcessBuilder.Redirect.from(options.inputFile));
        } else if (options.nullInput) {
            builder.redirectInput(ProcessBuilder.Redirect.from(nullDevice()));
        }
        if (options.discardError) {
            builder.redirectError(ProcessBuilder.Redirect.to(nullDevice()));
        }
        builder.environment().putAll(options.environment);
    }

    private static void readOutput(Process process, ProcessOptions options, ProcessOutput output,
        AtomicReference<IOException> error) {
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), options.charset), BUFFER_SIZE)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output != null) {
                    output.accept(line);
                }
            }
        } catch (Exception e) {
            error.set(e instanceof IOException ? (IOException)e : new IOException("读取进程输出失败", e));
            process.destroyForcibly();
        }
    }

    private static boolean waitFor(Process process) throws InterruptedException {
        process.waitFor();
        return true;
    }

    /**
     * 等输出线程；子进程已退出后最多再等 3 秒。避免 javaw 继承管道导致永远读不完。
     *
     * @param thread
     *            输出线程
     */
    private static void join(Thread thread) throws InterruptedException {
        thread.join(3000L);
    }

    private static File nullDevice() {
        return new File(System.getProperty("os.name", "").toUpperCase().startsWith("WINDOWS") ? "NUL" : "/dev/null");
    }
}
