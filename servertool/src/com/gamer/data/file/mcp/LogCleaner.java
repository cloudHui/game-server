package com.gamer.data.file.mcp;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

import com.gamer.data.file.client.workday.Scheduler;
import com.gamer.data.file.cmd.Logs;
import com.gamer.data.file.cmd.TaskLogView;
import com.gamer.data.file.config.PathConfig;
import com.gamer.data.task.BackgroundTasks;

/**
 * MCP 按日日志清理：删除 Server/.mcp/log 中早于今天的日志。
 */
public final class LogCleaner {

    /** 东八区（与 MCP DailyFileLog 按天切文件一致） */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 日志文件名日期段 */
    private static final DateTimeFormatter LOG_DAY_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    /** 任务日志时间戳 */
    private static final SimpleDateFormat LOG_TIME_FMT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private static boolean running;

    private LogCleaner() {}

    /**
     * 打开或关闭工作日 10:00。
     *
     * @param on
     *            启用
     */
    public static void setScheduled(boolean on) {
        Scheduler.setEnabled("清理以前日志", on, CleanLog::logScheduler, LogCleaner::run);
    }

    /** 手动或定时触发清理（进程内互斥）。 */
    public static void run() {
        synchronized (LogCleaner.class) {
            if (running) {
                CleanLog.append("[跳过] 清理以前日志正在执行中");
                return;
            }
            running = true;
        }

        final TaskLogView logView = CleanLog.ensureLogView();
        final long startMillis = System.currentTimeMillis();
        final int runIndex;
        synchronized (LogCleaner.class) {
            logView.runCount++;
            runIndex = logView.runCount;
            logView.currentStartMillis = startMillis;
            logView.phaseDetail = "执行中";
        }

        Logs.ensureStatusTickTimer();
        Logs.refreshLogStatus();
        BackgroundTasks.start("mcp-log-clean",
            new CleanWork(logView, runIndex, startMillis),
            new CleanListener(logView));
    }

    /**
     * 清理 MCP 日志目录中的过期按日日志。
     *
     * @param logView
     *            任务日志视图
     * @param runIndex
     *            第几次执行
     * @param startMillis
     *            任务开始时间戳
     */
    private static void doClean(TaskLogView logView, int runIndex, long startMillis) {
        LocalDate today = LocalDate.now(ZONE);
        File logDir = PathConfig.mcpLogDir(new File(PathConfig.SERVER_DIR));

        CleanLog.append("============================================================");
        CleanLog.append("第 " + runIndex + " 次执行 | 开始: " + LOG_TIME_FMT.format(new Date(startMillis)));
        CleanLog.append("保留: " + today + "（含）及以后；目录: " + logDir.getAbsolutePath());

        CleanCounts total = cleanLogDir(logDir, today);

        long endMillis = System.currentTimeMillis();
        CleanLog.append("汇总: 删除 " + total.deleted + "，失败 " + total.failed + "，跳过 " + total.skipped
            + "，耗时 " + (endMillis - startMillis) + "ms");
        CleanLog.append("结束: " + LOG_TIME_FMT.format(new Date(endMillis)));
        CleanLog.append("============================================================");
        Logs.finishTaskTiming(logView, endMillis, total.failed == 0);
        Logs.setLogPhaseDetail(logView, total.failed == 0 ? "完成" : "部分失败");
    }

    /**
     * 删除目录内早于今天的 {@code yyyy-MM-dd.log}。
     *
     * @param logDir
     *            MCP 日志目录
     * @param today
     *            保留边界（含今天）
     * @return 统计
     */
    private static CleanCounts cleanLogDir(File logDir, LocalDate today) {
        CleanCounts counts = new CleanCounts();
        if (!logDir.isDirectory()) {
            CleanLog.append("[跳过] 目录不存在");
            return counts;
        }
        File[] files = logDir.listFiles();
        if (files == null) {
            CleanLog.append("[跳过] 无法列出文件");
            return counts;
        }
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }
            LocalDate fileDay = parseLogDay(file.getName());
            if (fileDay == null || !fileDay.isBefore(today)) {
                counts.skipped++;
                continue;
            }
            try {
                Files.delete(file.toPath());
                counts.deleted++;
                CleanLog.append("[删除] " + file.getName());
            } catch (IOException e) {
                counts.failed++;
                CleanLog.append("[失败] " + file.getAbsolutePath() + " | " + formatDeleteFailure(e));
            }
        }
        return counts;
    }

    /**
     * 将删除异常收成一行可读原因（类名 + message）。
     *
     * @param e
     *            删除抛出的 IO 异常
     * @return 简短失败原因
     */
    private static String formatDeleteFailure(IOException e) {
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) {
            return e.getClass().getSimpleName();
        }
        return e.getClass().getSimpleName() + ": " + msg;
    }

    /** 解析 {@code yyyy-MM-dd.log} 中的日期；非此格式返回 null。 */
    private static LocalDate parseLogDay(String fileName) {
        if (fileName == null || !fileName.endsWith(".log") || fileName.length() != 14) {
            return null;
        }
        try {
            return LocalDate.parse(fileName.substring(0, 10), LOG_DAY_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 单次清理计数。 */
    private static final class CleanCounts {
        /** 成功删除数 */
        int deleted;
        /** 删除失败数 */
        int failed;
        /** 跳过数（非目标文件或无需删除） */
        int skipped;
    }

    /** 后台清理工作。 */
    private static final class CleanWork implements BackgroundTasks.Work {
        private final TaskLogView logView;
        private final int runIndex;
        private final long startMillis;

        private CleanWork(TaskLogView logView, int runIndex, long startMillis) {
            this.logView = logView;
            this.runIndex = runIndex;
            this.startMillis = startMillis;
        }

        @Override
        public int execute() {
            doClean(logView, runIndex, startMillis);
            return 0;
        }
    }

    /** 清理任务生命周期。 */
    private static final class CleanListener implements BackgroundTasks.Listener {
        private final TaskLogView logView;

        private CleanListener(TaskLogView logView) {
            this.logView = logView;
        }

        @Override
        public void onStarted(long ignoredStartMillis) {}

        @Override
        public void onSucceeded(int resultCode, long endMillis) {}

        @Override
        public void onFailed(Exception error, long endMillis) {
            CleanLog.append("[错误] " + error.getMessage());
            Logs.finishTaskTiming(logView, endMillis, false);
        }

        @Override
        public void onFinished() {
            synchronized (LogCleaner.class) {
                running = false;
            }
            Logs.stopStatusTickTimerIfIdle();
            Logs.refreshLogStatus();
        }
    }
}
