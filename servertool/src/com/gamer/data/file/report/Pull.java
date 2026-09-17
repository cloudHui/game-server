package com.gamer.data.file.report;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.gamer.data.file.client.workday.Scheduler;
import com.gamer.data.file.cmd.Logs;
import com.gamer.data.file.cmd.TaskLogView;
import com.gamer.data.file.config.SshConfig;
import com.gamer.data.file.job.Run;
import com.gamer.data.file.ssh.Ssh;
import com.gamer.data.file.task.TaskFields;
import com.gamer.data.file.zip.ZipArchiveModule;

/**
 * 测服下载：SSH 筛选/打包，只下载本次结果。工作日 10:00 与按钮同一入口。
 */
public final class Pull {

    /** 测服下载日志键；按钮与定时任务共用。 */
    public static final String LOG_KEY = "TEST_LOG_DL";
    /** 测服下载页签标题。 */
    public static final String TAB_TITLE = "测服下载";
    /** 互斥。 */
    private static final Run RUN = new Run();

    private Pull() {}

    /**
     * 打开或关闭工作日 10:00。
     *
     * @param on
     *            启用
     */
    public static void setScheduled(boolean on) {
        Scheduler.setEnabled(TAB_TITLE, on, msg -> Logs.appendLog(view(), "[SCHED] " + msg), () -> startRun(true));
    }

    /**
     * 按钮或页签「执行」。
     */
    public static void run() {
        startRun(false);
    }

    /**
     * 启动手动或定时的测服下载任务。
     *
     * @param scheduled
     *            是否为定时任务；定时任务每次使用昨天到今天
     */
    private static void startRun(final boolean scheduled) {
        final TaskLogView view = view();
        RUN.start("跳过：测服下载仍在执行", "test-log-pull", view, () -> {
            doRun(view, scheduled);
            return 0;
        });
    }

    private static TaskLogView view() {
        return Logs.bindLogView(LOG_KEY, TAB_TITLE, false);
    }

    /**
     * SSH 筛选 → 下载，不清远端；压缩包同名覆盖文件，目录结果不覆盖。
     *
     * @param view
     *            测服下载页签
     * @param scheduled
     *            是否为定时任务
     * @throws Exception
     *             筛选或下载失败
     */
    private static void doRun(TaskLogView view, boolean scheduled) throws Exception {
        Logs.appendLog(view, "---- 测服下载 ----");
        SshConfig.Conn conn = TaskFields.resolveSshConn(view);
        // 定时任务不读取手动日期，保持手动输入不变，并由远端脚本动态计算日期范围。
        TaskFields.LogPull pull = scheduled ? TaskFields.resolveScheduledLogPull() : TaskFields.resolveLogPull();
        if (scheduled) {
            Logs.appendLog(view, "定时日期：每次按昨天到今天动态计算");
        }
        Logs.appendLog(view, "连接 " + conn.brief());
        File script = SshConfig.localCopyLogScript();
        try {
            String cmd = SshConfig.copyCmd(pull.server, pull.compress, pull.from, pull.to, pull.condition);
            try (Ssh ssh = new Ssh(conn)) {
                String remote = ssh.execScript(script, cmd, view);
                if (remote == null) {
                    throw new IOException("本地脚本远端执行未返回结果路径");
                }
                if (remote.isEmpty()) {
                    Logs.appendLog(view, "指定范围无命中，不创建目录或压缩包");
                    return;
                }
                fetchOne(ssh, remote, view);
            }
        } finally {
            if (script.getName().startsWith("copylog-") && !script.delete()) {
                script.deleteOnExit();
            }
        }
        Logs.appendLog(view, "结束");
    }

    /**
     * 下载到本次暂存目录后发布。压缩包覆盖同名文件；目录结果不覆盖，也不删除 Desktop/log。
     */
    private static void fetchOne(Ssh ssh, String remote, TaskLogView view) throws IOException {
        String name = SshConfig.exportName(remote);
        Path root = SshConfig.localLog().toPath().toAbsolutePath().normalize();
        Files.createDirectories(root);
        Path stage = Files.createTempDirectory(root, ".download-");
        Path staged = stage.resolve(name).normalize();
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IOException("拒绝写到下载根目录: " + target);
        }
        try {
            ssh.fetchPath(remote, stage.toFile(), view);
            if (Files.isDirectory(target)) {
                throw new IOException("目标已是目录，拒绝覆盖: " + target);
            }
            Files.deleteIfExists(target);
            Files.move(staged, target);
        } catch (IOException e) {
            Logs.appendLog(view, "下载失败，暂存保留在 " + stage);
            throw e;
        }
        Logs.appendLog(view, "已保存 " + target);
        try {
            Files.delete(stage);
        } catch (IOException e) {
            Logs.appendLog(view, "[警告] 空暂存目录未清理: " + stage);
        }
        unpackLocal(target, root, view);
    }

    /**
     * 压缩包解到下载根下的 gameserver/worldserver/commonserver，成功后删除压缩包。 不套日期目录，也不删除下载根。结束解压则保留压缩包和旧目录。
     */
    private static void unpackLocal(Path archive, Path root, TaskLogView view) throws IOException {
        String name = archive.getFileName().toString();
        if (!name.endsWith(".tar.gz") && !name.endsWith(".zip")) {
            return;
        }
        Path tmp = Files.createTempDirectory(root, ".unpack-");
        Unpack.begin();
        ZipArchiveModule.Listener listener = Unpack.listen(view);
        try {
            Logs.appendLog(view, "解压 " + name);
            ZipArchiveModule.unpack(archive.toFile(), tmp.toFile(), listener);
            listener.checkpoint();
            publish(tmp, root, listener);
            Files.delete(archive);
            Logs.appendLog(view, "已解压到 " + root + "，已删除 " + name);
        } catch (IOException e) {
            if (Unpack.stopped()) {
                Logs.appendLog(view, "解压已结束，压缩包仍在 " + archive);
                return;
            }
            throw e;
        } finally {
            ZipArchiveModule.deleteRecursively(tmp.toFile(), listener);
            Unpack.end();
        }
    }

    /**
     * 把临时目录里的服目录挪到下载根，只替换这些目录。
     */
    private static void publish(Path tmp, Path root, ZipArchiveModule.Listener listener) throws IOException {
        File[] kids = tmp.toFile().listFiles();
        if (kids == null) {
            return;
        }
        for (File kid : kids) {
            String kidName = kid.getName();
            if (kidName.startsWith(".")) {
                continue;
            }
            Path dest = root.resolve(kidName).normalize();
            if (!dest.startsWith(root) || dest.equals(root)) {
                throw new IOException("拒绝解压到: " + dest);
            }
            if (Files.exists(dest) && !ZipArchiveModule.deleteRecursively(dest.toFile(), listener)) {
                throw new IOException("无法替换: " + dest);
            }
            Files.move(kid.toPath(), dest);
        }
    }
}