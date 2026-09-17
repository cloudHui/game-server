package com.gamer.data.file.job;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.gamer.data.file.cmd.Logs;
import com.gamer.data.file.cmd.TaskLogView;
import com.gamer.data.process.ProcessOptions;
import com.gamer.data.process.ProcessOutput;
import com.gamer.data.process.ProcessRunner;

/**
 * 子进程 stdout 批量写页签，避免每行一次 EDT。
 */
public final class Pipe {

    /** 攒多少行刷一次。 */
    private static final int FLUSH_LINES = 24;
    /** 最长多久刷一次（毫秒）。 */
    private static final int FLUSH_MS = 100;
    /**
     * cmd 管道编码：Windows GBK，其它 UTF-8。
     *
     * @return 字符集
     */
    public static Charset cmdCs() {
        String os = System.getProperty("os.name", "");
        if (!os.toUpperCase().startsWith("WINDOWS")) {
            return StandardCharsets.UTF_8;
        }
        try {
            return Charset.forName("GBK");
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
    }

    /**
     * 跑 cmd /c，收回全部输出（不刷页签）。给 netstat/tasklist 用。
     *
     * @param line
     *            整行命令
     * @return stdout+stderr
     * @throws IOException
     *             失败
     */
    public static String capture(String line) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add("cmd.exe");
        cmd.add("/c");
        cmd.add(line);
        StringBuilder sb = new StringBuilder();
        exec(cmd, null, null, false, cmdCs(), sb, null);
        return sb.toString();
    }

    /**
     * 跑命令，合并 stderr，批量写 {@code view}。
     *
     * @param cmd
     *            命令行
     * @param workDir
     *            工作目录，可空
     * @param env
     *            额外环境变量，可空
     * @param nulIn
     *            true 则 stdin 接 NUL（SSH_ASKPASS）
     * @param view
     *            页签
     * @return 退出码
     * @throws IOException
     *             启动失败或被中断
     */
    public static int run(List<String> cmd, File workDir, Map<String, String> env, boolean nulIn, TaskLogView view)
        throws IOException {
        return exec(cmd, workDir, env, nulIn, StandardCharsets.UTF_8, null, view);
    }

    /**
     * 跑命令并把输出逐行交给调用方。
     *
     * @param cmd
     *            命令行
     * @param workDir
     *            工作目录，可空
     * @param env
     *            额外环境变量，可空
     * @param nulIn
     *            true 则 stdin 接空设备
     * @param charset
     *            输出编码
     * @param consumer
     *            输出行消费者，可空
     * @return 退出码
     * @throws IOException
     *             启动、读取或中断失败
     */
    public static int runLines(List<String> cmd, File workDir, Map<String, String> env, boolean nulIn,
        Charset charset, ProcessOutput consumer) throws IOException {
        return runLines(cmd, workDir, env, nulIn, null, charset, consumer);
    }

    /**
     * 跑命令；stdinFile 非空则从该文件喂 stdin。
     *
     * @param cmd
     *            命令行
     * @param workDir
     *            工作目录，可空
     * @param env
     *            额外环境变量，可空
     * @param nulIn
     *            stdinFile 为空且为 true 时 stdin 接空设备
     * @param stdinFile
     *            本地 stdin，可空
     * @param charset
     *            输出编码
     * @param consumer
     *            输出行消费者，可空
     * @return 退出码
     * @throws IOException
     *             启动、读取或中断失败
     */
    public static int runLines(List<String> cmd, File workDir, Map<String, String> env, boolean nulIn, File stdinFile,
        Charset charset, ProcessOutput consumer) throws IOException {
        ProcessOptions options = new ProcessOptions()
            .workDirectory(workDir)
            .environment(env)
            .nullInput(stdinFile == null && nulIn)
            .inputFile(stdinFile)
            .charset(charset);
        return ProcessRunner.run(cmd, options, consumer);
    }

    /**
     * 启动进程：先排空输出再 waitFor，避免管道堵死。
     *
     * @param cmd
     *            命令行
     * @param workDir
     *            工作目录，可空
     * @param env
     *            额外环境变量，可空
     * @param nulIn
     *            true 则 stdin 接 NUL
     * @param cs
     *            输出编码
     * @param capture
     *            非空则收齐文本到此，不刷页签
     * @param view
     *            页签，capture 为空时写入
     * @return 退出码
     * @throws IOException
     *             启动失败或被中断
     */
    private static int exec(List<String> cmd, File workDir, Map<String, String> env, boolean nulIn, Charset cs,
        StringBuilder capture, TaskLogView view) throws IOException {
        if (capture != null) {
            return runLines(cmd, workDir, env, nulIn, cs, line -> capture.append(line).append('\n'));
        }

        final UiLineBuffer buffer = new UiLineBuffer(view);
        try {
            return runLines(cmd, workDir, env, nulIn, cs, buffer);
        } finally {
            buffer.flush();
        }
    }

    /** 批量刷新任务页签的输出消费者。 */
    private static final class UiLineBuffer implements ProcessOutput {
        /** 目标页签。 */
        private final TaskLogView view;
        /** 待刷新文本。 */
        private final StringBuilder buffer = new StringBuilder(4096);
        /** 当前累计行数。 */
        private int lines;
        /** 上次刷新时间。 */
        private long lastFlushMillis = System.currentTimeMillis();

        private UiLineBuffer(TaskLogView view) {
            this.view = view;
        }

        @Override
        public void accept(String line) {
            buffer.append(line).append('\n');
            lines++;
            long now = System.currentTimeMillis();
            if (lines >= FLUSH_LINES || now - lastFlushMillis >= FLUSH_MS) {
                flush();
            }
        }

        private void flush() {
            if (buffer.length() == 0) {
                return;
            }
            Logs.appendLogChunk(view, buffer.toString());
            buffer.setLength(0);
            lines = 0;
            lastFlushMillis = System.currentTimeMillis();
        }
    }
}
