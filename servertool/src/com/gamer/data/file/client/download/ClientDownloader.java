package com.gamer.data.file.client.download;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.gamer.data.file.client.workday.Scheduler;
import com.gamer.data.file.cmd.Logs;
import com.gamer.data.file.cmd.TaskLogView;
import com.gamer.data.task.BackgroundTasks;

/**
 * 客户端自动下载入口：定时调度 + 手动触发下载。
 */
public final class ClientDownloader {

    /** 输入框初始值与非法回退：基准 2026-08-14 / 85 / 156，探测范围构建号 +10、序号 +20。 */
    private static final Params DEFAULTS = new Params(LocalDate.of(2026, 9, 3), 95, 178, 10, 20);

    private static boolean downloadRunning;

    private ClientDownloader() {}

    /**
     * 客户端下载参数：基准用于估算起点，探测范围用于从起点往后扫描。
     * 五个字段与输入框顺序一致：日期、基准构建号、基准序号、探测构建号范围、探测序号范围。
     */
    public static final class Params {
        /** 基准日期，格式 yyyy-MM-dd */
        public final LocalDate baseDate;
        /** 基准 Jenkins 构建号 */
        public final int baseBuildNumber;
        /** 基准包名序号 */
        public final int basePackageIndex;
        /** 构建号探测范围：从起始构建号往后 */
        public final int buildRange;
        /** 序号探测范围：从起始序号往后 */
        public final int indexRange;

        public Params(LocalDate baseDate, int baseBuildNumber, int basePackageIndex, int buildRange, int indexRange) {
            this.baseDate = Objects.requireNonNull(baseDate, "baseDate");
            this.baseBuildNumber = baseBuildNumber;
            this.basePackageIndex = basePackageIndex;
            this.buildRange = buildRange;
            this.indexRange = indexRange;
        }

        /** 与输入框顺序一致的文本，供启动填充和非法回写。 */
        public String[] toFieldTexts() {
            return new String[] {baseDate.toString(), String.valueOf(baseBuildNumber),
                String.valueOf(basePackageIndex), String.valueOf(buildRange), String.valueOf(indexRange)};
        }
    }

    /** 输入框解析结果。 */
    public static final class ParseResult {
        /** 可用于下载的参数 */
        public final Params params;
        /** 非法项已替换为默认值后的框内文本 */
        public final String[] fieldTexts;
        /** 非法回退警告，无则为空列表 */
        public final List<String> warnings;

        private ParseResult(Params params, String[] fieldTexts, List<String> warnings) {
            this.params = params;
            this.fieldTexts = fieldTexts;
            this.warnings = warnings;
        }
    }

    /**
     * 打开或关闭工作日 10:00。
     *
     * @param on
     *            启用
     */
    public static void setScheduled(boolean on) {
        Scheduler.setEnabled("客户端下载", on, Log::logScheduler, ClientDownloader::download);
    }

    /**
     * @return 客户端下载默认参数
     */
    public static Params getDefaults() {
        return DEFAULTS;
    }

    /**
     * 按五个输入框文本解析参数；非法项回退默认值，并给出写回文本与警告。
     *
     * @param texts
     *            与 {@link Params#toFieldTexts()} 同序；不足或空视为该项非法
     * @return 解析结果
     */
    public static ParseResult parseFields(String[] texts) {
        String[] canonical = DEFAULTS.toFieldTexts();
        List<String> warnings = new ArrayList<>();
        LocalDate date = parseIsoDate(textAt(texts, 0));
        if (date == null) {
            date = DEFAULTS.baseDate;
            warnings.add(invalidWarn("基准日期", date));
        } else {
            canonical[0] = date.toString();
        }
        int[] ints = new int[] {DEFAULTS.baseBuildNumber, DEFAULTS.basePackageIndex, DEFAULTS.buildRange,
            DEFAULTS.indexRange};
        String[] intNames = new String[] {"基准构建号", "基准包名序号", "构建号范围", "序号范围"};
        for (int i = 0; i < ints.length; i++) {
            Integer parsed = parsePositiveInteger(textAt(texts, i + 1));
            if (parsed == null) {
                warnings.add(invalidWarn(intNames[i], ints[i]));
            } else {
                ints[i] = parsed;
                canonical[i + 1] = String.valueOf(parsed);
            }
        }
        return new ParseResult(new Params(date, ints[0], ints[1], ints[2], ints[3]), canonical, warnings);
    }

    private static String textAt(String[] texts, int index) {
        return texts != null && index < texts.length ? texts[index] : null;
    }

    private static String invalidWarn(String name, Object value) {
        return "[警告] " + name + "输入非法，已回退为 " + value;
    }

    private static Integer parsePositiveInteger(String text) {
        if (text == null) {
            return null;
        }
        try {
            int value = Integer.parseInt(text.trim());
            return value >= 1 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseIsoDate(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 手动或定时触发一次 Jenkins 客户端包下载（防重复执行）。 */
    public static void download() {
        synchronized (ClientDownloader.class) {
            if (downloadRunning) {
                Log.logScheduler("跳过：上次下载尚未结束");
                return;
            }
            downloadRunning = true;
        }

        final TaskLogView logView = Log.getLogView();
        final long startMillis = System.currentTimeMillis();
        int runIndex;
        synchronized (ClientDownloader.class) {
            logView.runCount++;
            runIndex = logView.runCount;
            logView.currentStartMillis = startMillis;
            logView.phaseDetail = "执行中";
        }

        Logs.ensureStatusTickTimer();
        Logs.refreshLogStatus();
        final int workerRunIndex = runIndex;
        BackgroundTasks.start("client-download-worker", () -> {
            JenkinsPackage.runDownload(logView, workerRunIndex, startMillis);
            return 0;
        }, new BackgroundTasks.Listener() {
            @Override
            public void onStarted(long ignoredStartMillis) {}

            @Override
            public void onSucceeded(int resultCode, long endMillis) {}

            @Override
            public void onFailed(Exception error, long endMillis) {
                JenkinsPackage.logDownloadError(logView, error, startMillis);
            }

            @Override
            public void onFinished() {
                synchronized (ClientDownloader.class) {
                    downloadRunning = false;
                }
                Logs.stopStatusTickTimerIfIdle();
                Logs.refreshLogStatus();
            }
        });
    }
}

