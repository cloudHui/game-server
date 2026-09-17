package com.gamer.data.file.client.deploy;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.gamer.data.file.cmd.Logs;
import com.gamer.data.file.cmd.TaskLogView;
import com.gamer.data.task.BackgroundTasks;
import com.gamer.data.file.zip.ZipArchiveModule;

/**
 * 客户端压缩包解压与部署 Module。
 */
public final class DeployModule {

    /** 调度共享锁。 */
    private static final Object LOCK = new Object();
    /** 下载目录。 */
    public static final String DOWNLOAD_DIR = "D:\\download";
    /** 部署目标目录。 */
    private static final String TARGET_DIR = "D:\\download\\fs\\KingdomWarships";
    /** 固定日志键。 */
    public static final String LOG_KEY = "EXTRACT_DEPLOY";
    /** 固定页签标题。 */
    public static final String TAB_TITLE = "解压并部署";
    /** 日志分隔线。 */
    private static final String EXTRACT_DEPLOY_LOG_SEP = "============================================================";
    /** 移动条目日志分隔线。 */
    private static final String MOVE_ENTRY_LOG_SEP = "------------------------------------------------------------";
    /** 单条目移动最大尝试次数（含首次）。 */
    private static final int MOVE_MAX_ATTEMPTS = 10;
    /** 移动失败后的重试间隔（毫秒）。 */
    private static final long MOVE_RETRY_INTERVAL_MS = 1000L;
    /** 占用排查子进程超时（毫秒）。 */
    private static final long LOCK_PROBE_TIMEOUT_MS = 10000L;
    /** 占用排查脚本缓存锁。 */
    private static final Object LOCK_SCRIPT_LOCK = new Object();
    /** 进程内常驻的占用排查脚本（写一次复用）。 */
    private static File cachedLockProbeScript;
    /** 东八区（与下载探测/包名日期一致） */
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    /** 包名日期格式 */
    private static final DateTimeFormatter PACKAGE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 是否正在执行部署。 */
    private static boolean running;

    private DeployModule() {}

    /**
     * 解压并部署入口：防重入后启动后台工作线程。
     */
    public static void run() {
        TaskLogView logView = beginRun();
        if (logView == null) {
            return;
        }
        Logs.ensureStatusTickTimer();
        Logs.refreshLogStatus();
        final int runIndex = logView.runCount;
        final long startMillis = logView.currentStartMillis;
        BackgroundTasks.start("extract-deploy-worker", () -> {
            doExtractAndDeploy(logView, runIndex, startMillis);
            return 0;
        }, new BackgroundTasks.Listener() {
            @Override
            public void onStarted(long ignoredStartMillis) {
                // 解压部署的 UI 状态已在调度前设置，防止按钮重复触发。
            }

            @Override
            public void onSucceeded(int resultCode, long endMillis) {
                // 业务方法内部负责日志、成功状态与失败状态输出。
            }

            @Override
            public void onFailed(Exception error, long endMillis) {
                logExtractDeployError(logView, error, startMillis);
            }

            @Override
            public void onFinished() {
                // 运行标记和状态刷新由 doExtractAndDeploy 的 finally 收敛。
            }
        });
    }

    /**
     * 同步解压并部署（供下载链路等后台线程调用，阻塞至部署结束）。
     *
     * @return 部署成功 true；跳过、失败或异常 false
     */
    public static boolean runAndWait() {
        TaskLogView logView = beginRun();
        if (logView == null) {
            return false;
        }
        Logs.ensureStatusTickTimer();
        Logs.refreshLogStatus();
        return doExtractAndDeploy(logView, logView.runCount, logView.currentStartMillis);
    }

    /**
     * 占用运行锁并初始化部署日志页签；已占用时写跳过日志并返回 null。
     * 次数与开始时间写在 {@link TaskLogView} 上，不再另建内部类。
     */
    private static TaskLogView beginRun() {
        synchronized (LOCK) {
            if (running) {
                TaskLogView skipLogView = Logs.bindLogView(LOG_KEY, TAB_TITLE, false);
                Logs.appendLog(skipLogView, "[跳过] 解压并部署正在执行中");
                return null;
            }
            running = true;
        }
        try {
            TaskLogView logView = Logs.bindLogView(LOG_KEY, TAB_TITLE, false);
            long startMillis = System.currentTimeMillis();
            synchronized (LOCK) {
                logView.runCount++;
                logView.currentStartMillis = startMillis;
                logView.phaseDetail = "执行中";
            }
            return logView;
        } catch (RuntimeException | Error e) {
            synchronized (LOCK) {
                running = false;
            }
            throw e;
        }
    }

    /**
     * 解压并部署工作方法：扫描 zip、解压、查找目录、部署到目标并清理。
     *
     * @return 部署成功 true；业务失败或异常 false
     */
    private static boolean doExtractAndDeploy(TaskLogView logView, int runIndex, long startMillis) {
        try {
            appendExtractDeployHeader(logView, runIndex);

            Logs.appendLog(logView, "[步骤1] 扫描下载目录查找当天日期的 zip 文件...");
            File zipFile = findTodayZip();
            if (zipFile == null) {
                failExtractDeploy(logView, "[失败] 未找到当天日期的 zip 文件，下载目录: " + DOWNLOAD_DIR, "失败 - 未找到 zip 文件",
                    startMillis);
                return false;
            }
            String baseName = getZipBaseName(zipFile);
            Logs.appendLog(logView, "找到压缩包: " + zipFile.getAbsolutePath());
            Logs.appendLog(logView, "基础名称: " + baseName);

            File tempDir = new File(zipFile.getParent(), baseName);
            Logs.appendLog(logView, "[步骤2] 解压到临时目录: " + tempDir.getAbsolutePath());
            extractZip(zipFile, tempDir, logView);

            Logs.appendLog(logView, "[步骤3] 在临时目录中递归查找目录: " + baseName);
            File sourceDir = findDirByName(tempDir, baseName);
            if (sourceDir == null) {
                failExtractDeploy(logView, "[失败] 未找到名为 " + baseName + " 的目录", "失败 - 未找到目标目录 " + baseName, startMillis);
                return false;
            }
            Logs.appendLog(logView, "找到目标目录: " + sourceDir.getAbsolutePath());
            Logs.appendLog(logView, "目录内容数量: " + countFiles(sourceDir) + " 个条目");

            File targetDir = new File(TARGET_DIR);
            Logs.appendLog(logView, "[步骤4] 清空目标目录: " + targetDir.getAbsolutePath());
            clearDirectory(targetDir, logView);
            Logs.appendLog(logView, "目标目录已清空");

            Logs.appendLog(logView, "[步骤5] 移动文件到目标目录...");
            moveDirectoryContents(sourceDir, targetDir, logView);

            cleanupAfterDeploy(tempDir, zipFile, logView);

            long endMillis = System.currentTimeMillis();
            appendExtractDeployFooter(logView, endMillis);
            Logs.finishTaskTiming(logView, endMillis, true);
            Logs.setLogPhaseDetail(logView, "完成");
            return true;
        } catch (Throwable t) {
            Exception e = t instanceof Exception ? (Exception) t : new Exception(t);
            logExtractDeployError(logView, e, startMillis);
            return false;
        } finally {
            synchronized (LOCK) {
                running = false;
            }
            Logs.stopStatusTickTimerIfIdle();
            Logs.refreshLogStatus();
        }
    }

    /**
     * 输出解压并部署开始头。
     */
    private static void appendExtractDeployHeader(TaskLogView logView, int runIndex) {
        Logs.appendLog(logView, EXTRACT_DEPLOY_LOG_SEP);
        Logs.appendLog(logView, "解压并部署 开始（第" + runIndex + "次）");
        Logs.appendLog(logView, "开始时间: " + Logs.formatTime(System.currentTimeMillis()));
        Logs.appendLog(logView, "下载目录: " + DOWNLOAD_DIR);
        Logs.appendLog(logView, "目标目录: " + TARGET_DIR);
        Logs.appendLog(logView, EXTRACT_DEPLOY_LOG_SEP);
    }

    /**
     * 输出解压并部署结束尾。
     */
    private static void appendExtractDeployFooter(TaskLogView logView, long endMillis) {
        Logs.appendLog(logView, EXTRACT_DEPLOY_LOG_SEP);
        Logs.appendLog(logView, "解压并部署 全部完成");
        Logs.appendLog(logView, "结束时间: " + Logs.formatTime(endMillis));
        Logs.appendLog(logView, EXTRACT_DEPLOY_LOG_SEP);
    }

    /**
     * 记录解压并部署失败并更新状态。
     */
    private static void failExtractDeploy(TaskLogView logView, String logMessage, String phaseDetail,
        long startMillis) {
        Logs.appendLog(logView, logMessage);
        long endMillis = System.currentTimeMillis();
        synchronized (LOCK) {
            if (logView.currentStartMillis <= 0) {
                logView.currentStartMillis = startMillis;
            }
        }
        Logs.finishTaskTiming(logView, endMillis, false);
        Logs.setLogPhaseDetail(logView, phaseDetail);
    }

    /**
     * 记录解压并部署异常栈。
     */
    private static void logExtractDeployError(TaskLogView logView, Exception e, long startMillis) {
        Logs.appendThrowable(logView, "[错误] 执行失败: ", e);
        long endMillis = System.currentTimeMillis();
        synchronized (LOCK) {
            if (logView.currentStartMillis <= 0) {
                logView.currentStartMillis = startMillis;
            }
        }
        Logs.finishTaskTiming(logView, endMillis, false);
        Logs.setLogPhaseDetail(logView, "失败 - " + e.getMessage());
    }

    /**
     * 从 zip 文件名去掉 .zip 后缀得到基础名称。
     */
    private static String getZipBaseName(File zipFile) {
        String zipName = zipFile.getName();
        return zipName.substring(0, zipName.length() - 4);
    }

    /**
     * 部署后清理：删除临时目录与原压缩包。
     */
    private static void cleanupAfterDeploy(File tempDir, File zipFile, TaskLogView logView) {
        Logs.appendLog(logView, "[步骤6] 删除临时目录: " + tempDir.getAbsolutePath());
        deleteRecursively(tempDir, logView);
        Logs.appendLog(logView, "临时目录已删除");

        Logs.appendLog(logView, "[步骤7] 删除原压缩包: " + zipFile.getAbsolutePath());
        if (!zipFile.delete()) {
            Logs.appendLog(logView, "[警告] 无法删除压缩包，可能被占用");
        } else {
            Logs.appendLog(logView, "压缩包已删除");
        }
    }

    /**
     * 查找下载目录中当天日期的 zip 文件（模式: yyyyMMdd-*.zip），多个时选最新修改时间。
     */
    private static File findTodayZip() {
        String today = LocalDate.now(SHANGHAI_ZONE).format(PACKAGE_DATE_FMT);
        File downloadDir = new File(DOWNLOAD_DIR);
        if (!downloadDir.exists() || !downloadDir.isDirectory()) {
            return null;
        }
        final String prefix = today + "-";
        File[] files = downloadDir.listFiles((dir, name) -> {
            if (name == null) {
                return false;
            }
            return name.toLowerCase().endsWith(".zip") && name.startsWith(prefix);
        });
        if (files == null || files.length == 0) {
            return null;
        }
        File newest = files[0];
        for (int i = 1; i < files.length; i++) {
            if (files[i].lastModified() > newest.lastModified()) {
                newest = files[i];
            }
        }
        return newest;
    }

    /**
     * 用 ZipFile（GBK）解压到目标目录。
     */
    private static void extractZip(File zipFile, File destDir, TaskLogView logView) throws IOException {
        int count = ZipArchiveModule.extract(zipFile, destDir, ZipArchiveModule.ZIP_CHARSET,
            Logs.zipListener(logView));
        Logs.setLogPhaseDetail(logView, "解压完成（" + count + " 个文件）");
    }

    /**
     * 递归查找目录名为 name 的子目录（不包含 root 本身）。
     */
    private static File findDirByName(File root, String name) {
        if (root == null || !root.isDirectory()) {
            return null;
        }
        File[] children = root.listFiles();
        if (children == null) {
            return null;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                if (child.getName().equals(name)) {
                    return child;
                }
                File found = findDirByName(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** 统计目录下的直接条目数。 */
    private static int countFiles(File dir) {
        File[] files = dir.listFiles();
        return files == null ? 0 : files.length;
    }

    /**
     * 清空目标目录下所有内容，目录不存在则创建。
     */
    private static void clearDirectory(File dir, TaskLogView logView) throws IOException {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!deleteRecursively(f, logView)) {
                        throw new IOException("无法删除目标目录内容: " + f.getAbsolutePath());
                    }
                }
            }
        } else {
            if (!dir.mkdirs()) {
                throw new IOException("无法创建目标目录: " + dir.getAbsolutePath());
            }
        }
    }

    /**
     * 将源目录下所有内容移动到目标目录。
     */
    private static void moveDirectoryContents(File sourceDir, File targetDir, TaskLogView logView) throws IOException {
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw new IOException("无法创建目标目录: " + targetDir.getAbsolutePath());
        }
        File[] files = sourceDir.listFiles();
        if (files == null) {
            Logs.appendLog(logView, "源目录为空，无需移动");
            return;
        }
        int total = files.length;
        ZipArchiveModule.ProgressThrottle moveThrottle = new ZipArchiveModule.ProgressThrottle();
        setLogPhaseProgress(logView, "移动中 0/" + total, 0, total, moveThrottle);
        int count = 0;
        for (int i = 0; i < files.length; i++) {
            File source = files[i];
            File dest = new File(targetDir, source.getName());
            appendMoveEntryHeader(logView, i + 1, total, source, dest);
            moveEntryWithRetry(source, dest, logView);
            count++;
            setLogPhaseProgress(logView, "移动中 " + count + "/" + total, count, total, moveThrottle);
        }
        Logs.appendLog(logView, "共移动 " + count + " 个条目");
        Logs.setLogPhaseDetail(logView, "移动完成（" + count + " 个条目）");
    }

    /**
     * 输出单条目移动开始信息。
     *
     * @param logView
     *            日志视图
     * @param index
     *            当前序号（从 1 起）
     * @param total
     *            总条目数
     * @param source
     *            源路径
     * @param dest
     *            目标路径
     */
    private static void appendMoveEntryHeader(TaskLogView logView, int index, int total, File source, File dest) {
        String typeLabel = source.isDirectory() ? "目录" : "文件";
        Logs.appendLog(logView, MOVE_ENTRY_LOG_SEP);
        Logs.appendLog(logView, String.format("[移动 %d/%d] %s（%s）", index,
                total, source.getName(), typeLabel));
        Logs.appendLog(logView, "  源路径     : " + source.getAbsolutePath());
        Logs.appendLog(logView, "  目标路径   : " + dest.getAbsolutePath());
        Logs.appendLog(logView, "  源存在     : " + source.exists() + "    目标已存在 : " + dest.exists());
    }

    /**
     * 移动单个文件/目录；失败时按固定间隔重试，用尽次数后尽力排查占用进程。
     *
     * @param source
     *            源路径
     * @param dest
     *            目标路径
     * @param logView
     *            日志视图
     * @throws IOException
     *             重试耗尽仍失败时抛出最后一次异常
     */
    private static void moveEntryWithRetry(File source, File dest, TaskLogView logView) throws IOException {
        IOException lastError = null;
        for (int attempt = 1; attempt <= MOVE_MAX_ATTEMPTS; attempt++) {
            try {
                Files.move(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                if (attempt > 1) {
                    Logs.appendLog(logView, String.format("[移动成功] 第 %d/%d 次尝试成功",
                            attempt, MOVE_MAX_ATTEMPTS));
                } else {
                    Logs.appendLog(logView, "[移动成功]");
                }
                return;
            } catch (IOException e) {
                lastError = e;
                String errorType = e.getClass().getSimpleName();
                String errorMsg = e.getMessage() == null ? "" : e.getMessage();
                Logs.appendLog(logView, String.format("[移动重试] 第 %d/%d 次失败 | %s | %s",
                        attempt, MOVE_MAX_ATTEMPTS, errorType, errorMsg));
                if (attempt == MOVE_MAX_ATTEMPTS) {
                    break;
                }
                int remain = MOVE_MAX_ATTEMPTS - attempt;
                Logs.appendLog(logView, String.format("[移动重试] 等待 %d 秒后继续（剩余 %d 次）",
                        (int) (MOVE_RETRY_INTERVAL_MS / 1000L), remain));
                try {
                    Thread.sleep(MOVE_RETRY_INTERVAL_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("移动被中断: " + source.getAbsolutePath(), ie);
                }
            }
        }
        Logs.appendLog(logView, String.format("[移动失败] 已连续尝试 %d 次仍失败",
                MOVE_MAX_ATTEMPTS));
        Logs.appendLog(logView, "  源存在     : " + source.exists() + "    目标存在   : " + dest.exists());
        Logs.appendLog(logView, "  源为目录   : " + source.isDirectory() + "    目标为目录 : " + dest.isDirectory());
        tryLogFileLockOwners(source, logView);
        throw lastError;
    }

    /**
     * 尽力排查占用源路径的进程（Windows Restart Manager）；查不到则仅记日志。
     *
     * @param path
     *            被占用嫌疑路径
     * @param logView
     *            日志视图
     */
    private static void tryLogFileLockOwners(File path, TaskLogView logView) {
        Logs.appendLog(logView, MOVE_ENTRY_LOG_SEP);
        Logs.appendLog(logView, "[占用排查] 正在查询锁定进程...");
        Logs.appendLog(logView, "  排查路径   : " + path.getAbsolutePath());
        List<String> owners;
        try {
            owners = queryLockingProcesses(path);
        } catch (Exception e) {
            String errorType = e.getClass().getSimpleName();
            String errorMsg = e.getMessage() == null ? "" : e.getMessage();
            Logs.appendLog(logView, "[占用排查] 查询失败，已跳过 | " + errorType + " | " + errorMsg);
            return;
        }
        if (owners == null || owners.isEmpty()) {
            Logs.appendLog(logView, "[占用排查] 未识别到占用进程（可能无权限，或锁已释放）");
            return;
        }
        Logs.appendLog(logView, "[占用排查] 发现以下进程可能占用该路径：");
        for (String owner : owners) {
            Logs.appendLog(logView, "  - " + owner);
        }
    }

    /**
     * 通过 PowerShell + Restart Manager 查询锁定指定路径的进程列表。
     *
     * @param path
     *            文件或目录
     * @return 占用描述列表；无占用时返回空列表
     * @throws Exception
     *             脚本执行或解析失败
     */
    private static List<String> queryLockingProcesses(File path) throws Exception {
        File scriptFile = ensureLockProbeScript();
        List<String> result = new ArrayList<>();
        Process process = null;
        BufferedReader reader = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-File", scriptFile.getAbsolutePath(),
                "-TargetPath", path.getAbsolutePath());
            pb.redirectErrorStream(true);
            process = pb.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            long deadline = System.currentTimeMillis() + LOCK_PROBE_TIMEOUT_MS;
            while (true) {
                drainPidLines(reader, result, false);
                if (!process.isAlive()) {
                    break;
                }
                if (System.currentTimeMillis() >= deadline) {
                    process.destroyForcibly();
                    throw new IOException("占用排查超时（" + (LOCK_PROBE_TIMEOUT_MS / 1000L) + " 秒）");
                }
                Thread.sleep(50L);
            }
            // 进程结束后阻塞排空剩余输出
            drainPidLines(reader, result, true);
            int exitCode = process.waitFor();
            if (exitCode != 0 && result.isEmpty()) {
                throw new IOException("占用排查脚本退出码=" + exitCode);
            }
            return result;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignore) {
                    // 关闭探测输出流失败可忽略
                }
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    /**
     * 读取 stdout 行并解析 PID= 占用信息。
     *
     * @param blocking
     *            true 时读到流结束；false 时仅读当前 ready 缓冲
     */
    private static void drainPidLines(BufferedReader reader, List<String> out, boolean blocking) throws IOException {
        while (blocking || reader.ready()) {
            String line = reader.readLine();
            if (line == null) {
                return;
            }
            line = line.trim();
            if (line.isEmpty() || "NONE".equals(line) || line.startsWith("Add-Type")) {
                continue;
            }
            if (line.startsWith("PID=")) {
                out.add(formatLockOwnerLine(line));
            }
            if (!blocking && !reader.ready()) {
                return;
            }
        }
    }

    /**
     * 进程内缓存占用排查脚本：只写一次到临时目录。
     */
    private static File ensureLockProbeScript() throws IOException {
        synchronized (LOCK_SCRIPT_LOCK) {
            if (cachedLockProbeScript != null && cachedLockProbeScript.isFile()) {
                return cachedLockProbeScript;
            }
            File scriptFile = new File(System.getProperty("java.io.tmpdir"), "kw-deploy-lock-probe.ps1");
            writeLockProbeScript(scriptFile);
            scriptFile.deleteOnExit();
            cachedLockProbeScript = scriptFile;
            return scriptFile;
        }
    }

    /**
     * 将 Restart Manager 输出行格式化为可读描述。
     *
     * @param rawLine
     *            形如 PID=1234\tNAME=xxx.exe
     * @return 可读描述
     */
    private static String formatLockOwnerLine(String rawLine) {
        String pid = "";
        String name = "";
        String[] parts = rawLine.split("\t");
        for (String part : parts) {
            if (part.startsWith("PID=")) {
                pid = part.substring(4);
            } else if (part.startsWith("NAME=")) {
                name = part.substring(5);
            }
        }
        if (name.isEmpty()) {
            name = "(未知进程名)";
        }
        if (pid.isEmpty()) {
            return "process=" + name;
        }
        return "pid=" + pid + "    process=" + name;
    }

    /**
     * 写入占用排查 PowerShell 脚本（Restart Manager）。
     *
     * @param scriptFile
     *            目标脚本文件
     * @throws IOException
     *             写文件失败
     */
    private static void writeLockProbeScript(File scriptFile) throws IOException {
        String script = "param([Parameter(Mandatory=$true)][string]$TargetPath)\r\n"
            + "$ErrorActionPreference = 'Stop'\r\n"
            + "$code = @'\r\n"
            + "using System;\r\n"
            + "using System.Collections.Generic;\r\n"
            + "using System.Runtime.InteropServices;\r\n"
            + "using System.Text;\r\n"
            + "public class DeployFileLockUtil {\r\n"
            + "  private const int CCH_RM_SESSION_KEY = 32;\r\n"
            + "  private const int CCH_RM_MAX_APP_NAME = 255;\r\n"
            + "  private const int CCH_RM_MAX_SVC_NAME = 63;\r\n"
            + "  [StructLayout(LayoutKind.Sequential)]\r\n"
            + "  public struct RM_UNIQUE_PROCESS {\r\n"
            + "    public int dwProcessId;\r\n"
            + "    public System.Runtime.InteropServices.ComTypes.FILETIME ProcessStartTime;\r\n"
            + "  }\r\n"
            + "  [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]\r\n"
            + "  public struct RM_PROCESS_INFO {\r\n"
            + "    public RM_UNIQUE_PROCESS Process;\r\n"
            + "    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = CCH_RM_MAX_APP_NAME + 1)]\r\n"
            + "    public string strAppName;\r\n"
            + "    [MarshalAs(UnmanagedType.ByValTStr, SizeConst = CCH_RM_MAX_SVC_NAME + 1)]\r\n"
            + "    public string strServiceShortName;\r\n"
            + "    public uint ApplicationType;\r\n"
            + "    public uint AppStatus;\r\n"
            + "    public uint TSSessionId;\r\n"
            + "    [MarshalAs(UnmanagedType.Bool)]\r\n"
            + "    public bool bRestartable;\r\n"
            + "  }\r\n"
            + "  [DllImport(\"rstrtmgr.dll\", CharSet = CharSet.Unicode)]\r\n"
            + "  static extern int RmStartSession(out uint pSessionHandle, int dwSessionFlags, string strSessionKey);\r\n"
            + "  [DllImport(\"rstrtmgr.dll\")]\r\n"
            + "  static extern int RmEndSession(uint pSessionHandle);\r\n"
            + "  [DllImport(\"rstrtmgr.dll\", CharSet = CharSet.Unicode)]\r\n"
            + "  static extern int RmRegisterResources(uint pSessionHandle, uint nFiles, string[] rgsFilenames,\r\n"
            + "    uint nApplications, IntPtr rgApplications, uint nServices, string[] rgsServiceNames);\r\n"
            + "  [DllImport(\"rstrtmgr.dll\")]\r\n"
            + "  static extern int RmGetList(uint pSessionHandle, out uint pnProcInfoNeeded,\r\n"
            + "    ref uint pnProcInfo, [In, Out] RM_PROCESS_INFO[] rgAffectedApps, ref uint lpdwRebootReasons);\r\n"
            + "  public static List<string> GetLockingProcesses(string path) {\r\n"
            + "    List<string> list = new List<string>();\r\n"
            + "    uint handle;\r\n"
            + "    string key = Guid.NewGuid().ToString();\r\n"
            + "    if (RmStartSession(out handle, 0, key) != 0) { return list; }\r\n"
            + "    try {\r\n"
            + "      string[] resources = new string[] { path };\r\n"
            + "      if (RmRegisterResources(handle, (uint)resources.Length, resources, 0, IntPtr.Zero, 0, null) != 0) {\r\n"
            + "        return list;\r\n"
            + "      }\r\n"
            + "      uint needed = 0;\r\n"
            + "      uint count = 0;\r\n"
            + "      uint reason = 0;\r\n"
            + "      int rc = RmGetList(handle, out needed, ref count, null, ref reason);\r\n"
            + "      if (rc == 234) {\r\n"
            + "        count = needed;\r\n"
            + "        RM_PROCESS_INFO[] arr = new RM_PROCESS_INFO[count];\r\n"
            + "        rc = RmGetList(handle, out needed, ref count, arr, ref reason);\r\n"
            + "        if (rc == 0) {\r\n"
            + "          for (int i = 0; i < count; i++) {\r\n"
            + "            list.Add(\"PID=\" + arr[i].Process.dwProcessId + \"\\tNAME=\" + arr[i].strAppName);\r\n"
            + "          }\r\n"
            + "        }\r\n"
            + "      }\r\n"
            + "    } finally {\r\n"
            + "      RmEndSession(handle);\r\n"
            + "    }\r\n"
            + "    return list;\r\n"
            + "  }\r\n"
            + "}\r\n"
            + "'@\r\n"
            + "if (-not ([System.Management.Automation.PSTypeName]'DeployFileLockUtil').Type) {\r\n"
            + "  try { Add-Type -TypeDefinition $code -Language CSharp | Out-Null } catch { }\r\n"
            + "}\r\n"
            + "$items = [DeployFileLockUtil]::GetLockingProcesses($TargetPath)\r\n"
            + "if ($null -eq $items -or $items.Count -eq 0) { Write-Output 'NONE'; exit 0 }\r\n"
            + "foreach ($item in $items) { Write-Output $item }\r\n";
        try (OutputStreamWriter writer = new OutputStreamWriter(Files.newOutputStream(scriptFile.toPath()), StandardCharsets.UTF_8)) {
            writer.write(script);
        }
    }

    /**
     * 递归删除文件或目录（删除失败仅写警告日志）。
     */
    private static boolean deleteRecursively(File f, TaskLogView logView) {
        return ZipArchiveModule.deleteRecursively(f, Logs.zipListener(logView));
    }

    /**
     * 节流刷新部署进度，首尾、每 25 条或间隔超过 100 毫秒时更新状态栏。
     */
    private static void setLogPhaseProgress(TaskLogView logView, String phaseDetail, int current, int total,
        ZipArchiveModule.ProgressThrottle throttle) {
        if (throttle.shouldUpdate(current, total)) {
            Logs.setLogPhaseDetail(logView, phaseDetail);
        }
    }
}
