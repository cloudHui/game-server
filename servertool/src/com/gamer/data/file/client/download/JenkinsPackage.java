package com.gamer.data.file.client.download;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;

import com.gamer.data.file.client.deploy.DeployModule;
import com.gamer.data.file.cmd.Logs;
import com.gamer.data.file.cmd.TaskLogView;
import com.gamer.data.file.task.TaskFields;

/**
 * Jenkins 客户端包探测、下载与解压部署。
 * <p>
 * 主流程见 {@link #runDownload}，日志步骤对应关系：
 * <ul>
 * <li>步骤1：按基准日估算工作日天数（仅跳过周六日）</li>
 * <li>步骤2~3：推算起始 Jenkins 构建号与包序号、最大探测编号</li>
 * <li>步骤4：HEAD 探测最新包（状态栏实时进度 + 按构建号汇总）</li>
 * <li>步骤5：确认最终选用的构建号与序号</li>
 * <li>步骤6~7：下载 artifact 到本地目录</li>
 * <li>步骤8：内存记录上次成功位置，供下次增量探测</li>
 * <li>步骤9：同步解压并部署（独立日志页签，完成后才收尾）</li>
 * </ul>
 * 构建号/序号估算与定时下载的节假日判定无关。
 */
final class JenkinsPackage {

    /** Jenkins 任务地址 */
    private static final String JENKINS_BASE_URL = "http://10.3.113.211:8080/job/SendFileToRobot";
    /** Jenkins Basic 认证 */
    private static final String AUTH =
        "Basic " + Base64.getEncoder().encodeToString("admin:admin".getBytes(StandardCharsets.UTF_8));
    /** 下载进度刷新间隔（字节） */
    private static final long DOWNLOAD_PROGRESS_INTERVAL = 10 * 1024L;
    /** 步骤4 探测状态栏刷新最小间隔（毫秒） */
    private static final long PROBE_PHASE_REFRESH_INTERVAL_MS = 200L;
    /** 东八区 */
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    /** 包名日期格式 */
    private static final DateTimeFormatter PACKAGE_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 日志分隔线 */
    private static final String LOG_SEP = "============================================================";

    /** 上次成功构建号 */
    private static int lastBuildNumber = 0;
    /** 上次成功包序号 */
    private static int lastPackageIndex = 0;

    private JenkinsPackage() {}

    /**
     * 执行 Jenkins 下载全流程（在 {@link ClientDownloader} 异步 worker 线程中运行）。
     * <p>
     * 依次完成：日志头 → 步骤1~3 估算 → 步骤4 探测 → 步骤5~7 下载 → 步骤8 记位 → 步骤9 解压部署 → 收尾。
     *
     * @param logView
     *            「客户端下载」日志页签
     * @param runIndex
     *            累计执行次数（写入日志头）
     * @param startMillis
     *            任务开始时间戳（失败/异常时补写耗时）
     */
    static void runDownload(TaskLogView logView, int runIndex, long startMillis) {
        try {
            // 一次读入 CMD 输入框（非法项已回退默认），后续步骤都引用同一份
            ClientDownloader.Params params = TaskFields.resolveClientDownloadParams(logView);
            appendHeader(logView, runIndex, params);

            // 步骤1：统计基准日至今的工作日天数（不含周六日，不含法定节假日调休）
            long days = calculateDaysSinceBaseSkipWeekend(params.baseDate);
            Log.append(logView, "[步骤1] 计算天数：距基准日期 " + days + " 天（仅跳过周六日）");

            // 步骤2~3：有上次成功记录则从其后一位起探，否则用基准值 + 工作日偏移估算起点
            int startBuildNo = lastBuildNumber > 0 ? lastBuildNumber + 1 : params.baseBuildNumber + (int)days;
            int startIdx = lastPackageIndex > 0 ? lastPackageIndex + 1 : params.basePackageIndex + (int)days;
            int maxBuildNo = startBuildNo + params.buildRange;
            int maxPackageIndex = startIdx + params.indexRange;
            Log.append(logView, "[步骤2] 起始构建号：" + startBuildNo + "，最大构建号：" + maxBuildNo
                + "（范围 +" + params.buildRange + "）");
            Log.append(logView, "[步骤3] 起始序号：" + startIdx + "，最大序号：" + maxPackageIndex
                + "（范围 +" + params.indexRange + "）");

            // 步骤4：双层循环 HEAD 探测，选出构建号最大的命中包
            PackageLocation latest = findLatestPackage(startBuildNo, startIdx, maxBuildNo, maxPackageIndex, logView);
            if (latest == null) {
                fail(logView, "[失败] 未找到可下载的包", "失败 - 未找到包", startMillis);
                return;
            }
            // 步骤5：探测结果落日志（与步骤4 汇总呼应）
            Log.append(logView, "[步骤5] 最新包：构建号=" + latest.buildNo + "，序号=" + latest.packageIndex);

            // 步骤6~7：按当天日期 + 序号拼包名并下载
            String dateStr = LocalDate.now(SHANGHAI_ZONE).format(PACKAGE_DATE_FMT);
            String fileName = dateStr + "-" + latest.packageIndex + ".zip";
            File downloadedFile = downloadFile(latest.buildNo, fileName, logView);
            if (downloadedFile == null) {
                fail(logView, "[失败] 下载失败", "失败 - 下载失败", startMillis);
                return;
            }

            // 步骤8：更新进程内上次成功位，下次 download 从更靠后的构建号/序号起探
            lastBuildNumber = latest.buildNo;
            lastPackageIndex = latest.packageIndex;
            Log.append(logView, "[步骤8] 更新记录：构建号=" + latest.buildNo + "，序号=" + latest.packageIndex);

            // 步骤9：同步解压部署，等待结果后再判定全流程成败
            Log.append(logView, "[步骤9] 调用解压并部署...");
            if (!DeployModule.runAndWait()) {
                fail(logView, "[失败] 解压并部署失败", "失败 - 部署失败", startMillis);
                return;
            }

            long endMillis = System.currentTimeMillis();
            appendFooter(logView, endMillis);
            Logs.finishTaskTiming(logView, endMillis, true);
            Logs.setLogPhaseDetail(logView, "完成");
        } catch (Throwable t) {
            Exception error = t instanceof Exception ? (Exception) t : new Exception(t);
            logDownloadError(logView, error, startMillis);
        }
    }

    /** 探测/下载结果：Jenkins 构建号与 artifact 包序号。 */
    private static final class PackageLocation {
        /** Jenkins 构建号 */
        private final int buildNo;
        /** 包文件名中的序号段（yyyyMMdd-序号.zip） */
        private final int packageIndex;

        private PackageLocation(int buildNo, int packageIndex) {
            this.buildNo = buildNo;
            this.packageIndex = packageIndex;
        }
    }

    /** Jenkins 包 HEAD 检查结果 */
    private enum FileCheckResult {
        /** HTTP 200，包存在 */
        EXISTS,
        /** HTTP 非 200，包不存在 */
        NOT_EXISTS,
        /** 网络或 IO 异常，无法判定 */
        CHECK_FAILED
    }

    /** 步骤4 探测状态栏刷新节流 */
    private static final class ProbePhaseThrottle {
        /** 上次刷新时间戳 */
        private long lastRefreshMillis;

        /**
         * 是否应刷新状态栏。
         *
         * @param checkCount
         *            累计检查次数（从 1 起）
         * @param forceRefresh
         *            是否强制刷新（结果类型变化、构建号扫描结束等）
         */
        private boolean shouldRefresh(int checkCount, boolean forceRefresh) {
            if (forceRefresh || checkCount <= 1) {
                return true;
            }
            return System.currentTimeMillis() - lastRefreshMillis >= PROBE_PHASE_REFRESH_INTERVAL_MS;
        }

        /** 记录本次刷新时间。 */
        private void markRefreshed() {
            lastRefreshMillis = System.currentTimeMillis();
        }
    }

    /**
     * 自基准日到今天，仅跳过周六日的天数（用于估算 Jenkins 构建号/序号，不含调休）。
     *
     * @param baseDate
     *            输入框解析后的基准日期
     * @return 工作日天数
     */
    static long calculateDaysSinceBaseSkipWeekend(LocalDate baseDate) {
        LocalDate today = LocalDate.now(SHANGHAI_ZONE);
        LocalDate curr = baseDate;
        long days = 0;
        while (curr.isBefore(today)) {
            DayOfWeek dow = curr.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                days++;
            }
            curr = curr.plusDays(1);
        }
        return days;
    }

    /**
     * 步骤4：在构建号 × 序号二维空间内 HEAD 探测最新包。
     * <p>
     * 外层遍历到最大构建号；内层从 {@code startIdx} 起递增到最大序号。
     * 同一构建号内：连续存在的序号取最大；一旦出现「已有命中后再不存在」则停止该构建号（序号已到头）。
     * 跨构建号取构建号最大的命中结果。进度写状态栏，按构建号与结束时写日志区汇总。
     *
     * @param startBuildNo
     *            外层起始构建号
     * @param startIdx
     *            内层起始包序号
     * @param maxBuildNo
     *            最大探测构建号
     * @param maxPackageIndex
     *            最大探测序号
     * @param logView
     *            日志页签
     * @return 最新包位置；全部未命中时返回 null
     */
    private static PackageLocation findLatestPackage(int startBuildNo, int startIdx, int maxBuildNo,
        int maxPackageIndex, TaskLogView logView) {
        Log.append(logView, "[步骤4] 开始探测...");
        Logs.setLogPhaseDetail(logView, "步骤4 探测中");

        PackageLocation best = null;
        int totalChecks = 0;
        int checkFailures = 0;
        ProbePhaseThrottle probeThrottle = new ProbePhaseThrottle();
        FileCheckResult lastCheckResult;

        // 外层：逐个构建号尝试
        for (int buildNo = startBuildNo; buildNo <= maxBuildNo; buildNo++) {
            int buildLatestIdx = -1;
            lastCheckResult = null;
            // 内层：序号递增 HEAD；连续存在段内不断更新 buildLatestIdx
            for (int currentIdx = startIdx; currentIdx <= maxPackageIndex; currentIdx++) {
                totalChecks++;
                FileCheckResult checkResult = checkFileExists(buildNo, currentIdx);
                if (checkResult == FileCheckResult.CHECK_FAILED) {
                    checkFailures++;
                }
                boolean exists = checkResult == FileCheckResult.EXISTS;
                // 存在/不存在/失败切换时立即刷新状态栏，避免长时间显示旧结果
                boolean forceRefresh = lastCheckResult == null || lastCheckResult != checkResult;
                lastCheckResult = checkResult;
                updateProbePhaseDetail(logView, buildNo, currentIdx, checkResult, probeThrottle, totalChecks,
                    forceRefresh);
                if (exists) {
                    buildLatestIdx = currentIdx;
                } else if (buildLatestIdx >= 0) {
                    // 已出现过包且当前序号不存在，说明该构建号下序号已探测到末尾
                    break;
                }
            }
            // 每个构建号结束写一行汇总，避免逐步 HEAD 刷屏
            if (buildLatestIdx >= 0) {
                Log.append(logView,
                    "[步骤4] 构建号 " + buildNo + " 命中，序号 " + buildLatestIdx);
                // 跨构建号保留构建号更大者（Jenkins 构建号越大越新）
                if (best == null || buildNo > best.buildNo) {
                    best = new PackageLocation(buildNo, buildLatestIdx);
                }
            } else {
                Log.append(logView, "[步骤4] 构建号 " + buildNo + " 未命中");
            }
        }

        appendProbeSummary(logView, totalChecks, checkFailures, best);
        return best;
    }

    /**
     * 节流更新步骤4 探测状态栏文案（详细进度不进 JTextArea，见 {@link Log#append}）。
     *
     * @param logView
     *            日志页签
     * @param buildNo
     *            当前构建号
     * @param packageIndex
     *            当前包序号
     * @param checkResult
     *            HEAD 结果
     * @param throttle
     *            刷新节流器
     * @param checkCount
     *            累计检查次数
     * @param forceRefresh
     *            是否跳过节流立即刷新
     */
    private static void updateProbePhaseDetail(TaskLogView logView, int buildNo, int packageIndex,
        FileCheckResult checkResult, ProbePhaseThrottle throttle, int checkCount, boolean forceRefresh) {
        if (!throttle.shouldRefresh(checkCount, forceRefresh)) {
            return;
        }
        throttle.markRefreshed();
        Logs.setLogPhaseDetail(logView,
            "步骤4 探测 构建号 " + buildNo + " 序号 " + packageIndex + " " + formatFileCheckResult(checkResult));
    }

    /** 步骤4 结束：写日志区总汇总，并将状态栏过渡到下载或失败前状态。 */
    private static void appendProbeSummary(TaskLogView logView, int totalChecks, int checkFailures,
        PackageLocation best) {
        StringBuilder summary = new StringBuilder();
        summary.append("[步骤4] 探测完成：共检查 ").append(totalChecks).append(" 次");
        if (checkFailures > 0) {
            summary.append("，检查失败 ").append(checkFailures).append(" 次");
        }
        if (best != null) {
            summary.append("，最新包 构建号=").append(best.buildNo).append("，序号=").append(best.packageIndex);
            Log.append(logView, summary.toString());
            Logs.setLogPhaseDetail(logView, "探测完成，准备下载");
        } else {
            summary.append("，未找到可下载包");
            Log.append(logView, summary.toString());
            Logs.setLogPhaseDetail(logView, "探测完成，未找到包");
        }
    }

    /** 将 HEAD 检查结果格式化为状态栏/日志可读文案。 */
    private static String formatFileCheckResult(FileCheckResult checkResult) {
        if (checkResult == FileCheckResult.EXISTS) {
            return "存在";
        }
        if (checkResult == FileCheckResult.CHECK_FAILED) {
            return "检查失败";
        }
        return "不存在";
    }

    /**
     * 对指定构建号下的包 artifact 发 HTTP HEAD，判断当天日期对应 zip 是否存在。
     * <p>
     * 包名规则：{@code yyyyMMdd-序号.zip}（东八区当天）。网络/IO 异常与「HTTP 非 200」区分返回。
     *
     * @param buildNo
     *            Jenkins 构建号
     * @param index
     *            包序号
     * @return {@link FileCheckResult}
     */
    private static FileCheckResult checkFileExists(int buildNo, int index) {
        HttpURLConnection conn = null;
        try {
            String fileName = LocalDate.now(SHANGHAI_ZONE).format(PACKAGE_DATE_FMT) + "-" + index + ".zip";
            String urlStr = String.format(Locale.ROOT, "%s/%d/artifact/%s", JENKINS_BASE_URL, buildNo, fileName);
            conn = (HttpURLConnection)new URL(urlStr).openConnection();
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("Authorization", AUTH);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() == 200) {
                return FileCheckResult.EXISTS;
            }
            return FileCheckResult.NOT_EXISTS;
        } catch (IOException e) {
            return FileCheckResult.CHECK_FAILED;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 步骤6~7：从 Jenkins 下载 artifact 到 {@link DeployModule#DOWNLOAD_DIR}。
     * <p>
     * 步骤6 写开始日志与 URL；下载过程中用状态栏显示进度；步骤7 写完成日志。
     *
     * @param buildNo
     *            Jenkins 构建号
     * @param fileName
     *            artifact 文件名（含日期与序号）
     * @param logView
     *            日志页签
     * @return 本地文件；失败返回 null
     */
    private static File downloadFile(int buildNo, String fileName, TaskLogView logView) {
        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;
        try {
            String urlStr = String.format(Locale.ROOT, "%s/%d/artifact/%s", JENKINS_BASE_URL, buildNo, fileName);
            Log.append(logView, "[步骤6] 开始下载：" + urlStr);

            conn = (HttpURLConnection)new URL(urlStr).openConnection();
            conn.setRequestProperty("Authorization", AUTH);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            if (conn.getResponseCode() != 200) {
                Log.append(logView, "[错误] HTTP 响应码：" + conn.getResponseCode());
                return null;
            }

            long fileSize = conn.getContentLengthLong();
            Log.append(logView, "文件大小：" + formatFileSize(fileSize));

            File downloadDir = new File(DeployModule.DOWNLOAD_DIR);
            if (!downloadDir.exists()) {
                Log.append(logView, DeployModule.DOWNLOAD_DIR + " 下载:" + downloadDir.mkdirs());
            }

            File outputFile = new File(downloadDir, fileName);
            is = conn.getInputStream();
            fos = new FileOutputStream(outputFile);
            byte[] buffer = new byte[8192];
            long totalRead = 0;
            long lastReportAt = 0;
            int bytesRead;
            // 流式写入并按字节间隔刷新状态栏，避免逐块打日志
            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                if (totalRead - lastReportAt >= DOWNLOAD_PROGRESS_INTERVAL) {
                    lastReportAt = totalRead;
                    Logs.setLogPhaseDetail(logView,
                        "下载中 " + formatFileSize(totalRead) + "/" + formatFileSize(fileSize));
                }
            }
            Logs.setLogPhaseDetail(logView,
                "下载中 " + formatFileSize(totalRead) + "/" + formatFileSize(fileSize));
            Log.append(logView, "[步骤7] 下载完成，大小：" + formatFileSize(outputFile.length()));
            return outputFile;
        } catch (IOException e) {
            Log.append(logView, "[错误] 下载失败：" + e.getMessage());
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
            }
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ignored) {
                }
            }
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * 任务开始：分隔线、执行次序、时间与本次实际使用的基准配置。
     *
     * @param logView
     *            下载日志页签
     * @param runIndex
     *            累计执行次数
     * @param params
     *            输入框解析后的下载参数
     */
    private static void appendHeader(TaskLogView logView, int runIndex, ClientDownloader.Params params) {
        Log.append(logView, LOG_SEP);
        Log.append(logView, "client 下载 开始（第" + runIndex + "次）");
        Log.append(logView, "开始时间: " + Logs.formatTime(System.currentTimeMillis()));
        Log.append(logView, "基准日期: " + params.baseDate);
        Log.append(logView, "基准构建号: " + params.baseBuildNumber);
        Log.append(logView, "基准包名序号: " + params.basePackageIndex);
        Log.append(logView, LOG_SEP);
    }

    /** 全流程成功结束：分隔线与结束时间。 */
    private static void appendFooter(TaskLogView logView, long endMillis) {
        Log.append(logView, LOG_SEP);
        Log.append(logView, "client 下载 全部完成");
        Log.append(logView, "结束时间: " + Logs.formatTime(endMillis));
        Log.append(logView, LOG_SEP);
    }

    /**
     * 业务失败收尾：写日志、停止耗时计时（不计入上次成功耗时）、更新状态栏。
     *
     * @param logView
     *            日志页签
     * @param logMessage
     *            写入 JTextArea 的失败说明
     * @param phaseDetail
     *            状态栏阶段文案
     * @param startMillis
     *            任务开始时间（异常路径下 currentStartMillis 可能未设置时使用）
     */
    private static void fail(TaskLogView logView, String logMessage, String phaseDetail, long startMillis) {
        Log.append(logView, logMessage);
        long endMillis = System.currentTimeMillis();
        synchronized (ClientDownloader.class) {
            if (logView.currentStartMillis <= 0) {
                logView.currentStartMillis = startMillis;
            }
        }
        Logs.finishTaskTiming(logView, endMillis, false);
        Logs.setLogPhaseDetail(logView, phaseDetail);
    }

    /**
     * 未捕获异常收尾：堆栈写入日志页签并标记失败状态（由 {@link ClientDownloader} 异步回调触发）。
     *
     * @param logView
     *            日志页签
     * @param e
     *            异常
     * @param startMillis
     *            任务开始时间
     */
    static void logDownloadError(TaskLogView logView, Exception e, long startMillis) {
        Logs.appendThrowable(logView, "[错误] 执行失败: ", e);
        long endMillis = System.currentTimeMillis();
        synchronized (ClientDownloader.class) {
            if (logView.currentStartMillis <= 0) {
                logView.currentStartMillis = startMillis;
            }
        }
        Logs.finishTaskTiming(logView, endMillis, false);
        Logs.setLogPhaseDetail(logView, "失败 - " + e.getMessage());
    }

    /** 人类可读文件大小（B / KB / MB / GB）。 */
    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.2f KB", bytes / 1024.0);
        }
        if (bytes < 1024 * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.2f MB", bytes / (1024.0 * 1024));
        }
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
