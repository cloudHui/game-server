package com.gamer.data.gdg.generate;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.gamer.data.log.Log;
import com.gamer.data.limit.ColumnLengthLimitStore;
import com.gamer.data.limit.LimitFileData;
import com.gamer.data.limit.LimitPathUtil;

import com.gamer.data.gdg.excel.ExcelOperate;
import com.gamer.data.gdg.excel.XlsxSheetStreamReader;
import com.gamer.data.gdg.progress.GdMultiLineProgressLog;
import com.gamer.data.gdg.progress.GdProgressContext;
import com.gamer.data.gdg.progress.GdSheetProgressReporter;
import com.gamer.data.gdg.util.GdBatchResult;
import com.gamer.data.gdg.util.GdPathUtil;
import com.gamer.data.gdg.util.GdPipelineSettings;
import com.gamer.data.gdg.validate.GdErrorCollector;

/**
 * 并行 GD 生成：每 Sheet 单遍读→校验→写 tmp；失败删除该 Sheet 的 gd/tmp 并回滚。
 */
public class XlsxGdParallelProcessor {

    private XlsxGdParallelProcessor() {}

    /**
     * 处理 xlsx 全部 Sheet 生成 GD。
     *
     * @param file
     *            xlsx 文件
     * @param ctx
     *            进度上下文
     * @param cancelFlag
     *            取消标记
     * @return 批处理结果
     */
    public static GdBatchResult process(File file, GdProgressContext ctx, AtomicBoolean cancelFlag) {
        return process(file, ctx, cancelFlag, null);
    }

    /**
     * 处理 xlsx 全部 Sheet 生成 GD。
     *
     * @param file
     *            xlsx 文件
     * @param ctx
     *            进度上下文
     * @param cancelFlag
     *            取消标记
     * @param limitDir
     *            列长 limit 目录；null 则按 user.dir 解析（StrategyTool）
     * @return 批处理结果
     */
    public static GdBatchResult process(File file, GdProgressContext ctx, AtomicBoolean cancelFlag, File limitDir) {
        return process(file, ctx, cancelFlag, limitDir, null);
    }

    /**
     * 处理 xlsx 全部 Sheet 生成 GD。
     *
     * @param file xlsx 文件
     * @param ctx 进度上下文
     * @param cancelFlag 取消标记
     * @param limitDir 列长 limit 目录；null 则按 user.dir 解析
     * @param gdOutDir GD 输出目录；null 则写在 xlsx 同目录
     * @return 批处理结果
     */
    public static GdBatchResult process(File file, GdProgressContext ctx, AtomicBoolean cancelFlag, File limitDir,
        File gdOutDir) {
        try {
            return process(file, XlsxSheetStreamReader.readSheetNames(file), ctx, cancelFlag, limitDir, gdOutDir);
        } catch (Exception e) {
            ctx.logError("生成 GD 失败: " + e.getMessage());
            return GdBatchResult.fail(file.getName());
        }
    }

    /**
     * 处理指定 Sheet 列表生成 GD。
     *
     * @param file
     *            xlsx 文件
     * @param allSheetNames
     *            Sheet 名列表
     * @param ctx
     *            进度上下文
     * @param cancelFlag
     *            取消标记
     * @return 批处理结果
     */
    public static GdBatchResult process(File file, List<String> allSheetNames, GdProgressContext ctx,
        AtomicBoolean cancelFlag) {
        return process(file, allSheetNames, ctx, cancelFlag, null);
    }

    /**
     * 处理指定 Sheet 列表生成 GD。
     *
     * @param file
     *            xlsx 文件
     * @param allSheetNames
     *            Sheet 名列表
     * @param ctx
     *            进度上下文
     * @param cancelFlag
     *            取消标记
     * @param limitDir
     *            列长 limit 目录；null 则按 user.dir 解析（StrategyTool）
     * @return 批处理结果
     */
    public static GdBatchResult process(File file, List<String> allSheetNames, GdProgressContext ctx,
        AtomicBoolean cancelFlag, File limitDir) {
        return process(file, allSheetNames, ctx, cancelFlag, limitDir, null);
    }

    /**
     * 处理指定 Sheet 列表生成 GD。
     *
     * @param file xlsx 文件
     * @param allSheetNames Sheet 名列表
     * @param ctx 进度上下文
     * @param cancelFlag 取消标记
     * @param limitDir 列长 limit 目录；null 则按 user.dir 解析
     * @param gdOutDir GD 输出目录；null 则写在 xlsx 同目录
     * @return 批处理结果
     */
    public static GdBatchResult process(File file, List<String> allSheetNames, GdProgressContext ctx,
        AtomicBoolean cancelFlag, File limitDir, File gdOutDir) {
        String fileName = file.getName();
        GdErrorCollector collector = new GdErrorCollector();
        Log log = ctx.getLog();
        GdMultiLineProgressLog lineLog = log instanceof GdMultiLineProgressLog ? (GdMultiLineProgressLog) log : null;
        try {
            ExcelOperate.applyLargeFileZipSettings();
            List<String> taskSheets = filterValidSheetNames(allSheetNames);
            int sheetTotal = taskSheets.size();
            if (sheetTotal == 0) {
                ctx.logError("没有合法英文 Sheet");
                return GdBatchResult.fail(fileName);
            }
            final AtomicInteger validateDone = new AtomicInteger(0);
            final AtomicInteger writeDone = new AtomicInteger(0);
            final AtomicInteger generateDone = new AtomicInteger(0);
            if (lineLog != null) {
                lineLog.resetProgressLines(taskSheets);
                refreshFileCounts(lineLog, fileName, sheetTotal, validateDone, writeDone, generateDone, false);
            }
            LimitFileData limitData = loadLimitFileData(fileName, limitDir);
            final File workDir = gdOutDir != null ? gdOutDir : file.getParentFile();
            if (workDir == null || !workDir.isDirectory()) {
                ctx.logError("GD 输出目录不存在: " + (workDir != null ? workDir.getAbsolutePath() : "null"));
                return GdBatchResult.fail(fileName);
            }
            final boolean keepExistingGd = gdOutDir != null;
            int batchRows = GdPipelineSettings.getBatchRows();
            int parallelism = GdPipelineSettings.resolveParallelism(sheetTotal);
            long pipelineStart = System.currentTimeMillis();
            ctx.logProgress("开始流水线：校验通过即写入（" + parallelism + " 线程，每 " + batchRows + " 行刷新）");
            List<String> written = runPipelineParallel(file, workDir, taskSheets, limitData, collector, cancelFlag,
                lineLog, fileName, sheetTotal, validateDone, writeDone, generateDone, batchRows, parallelism,
                keepExistingGd);
            if (cancelFlag.get()) {
                rollbackNarrow(workDir, taskSheets, keepExistingGd);
                ctx.logError("已取消，已回滚本次 gd");
                return GdBatchResult.fail(fileName);
            }
            if (collector.hasErrors() || written.size() != taskSheets.size()) {
                rollbackNarrow(workDir, taskSheets, keepExistingGd);
                logCollectorErrors(ctx, collector);
                return GdBatchResult.fail(fileName);
            }
            ctx.logDone("全部成功，已写入 " + written.size() + " 个 .gd，总耗时 " + GdProgressContext.formatElapsed(pipelineStart));
            if (lineLog != null) {
                validateDone.set(sheetTotal);
                writeDone.set(sheetTotal);
                generateDone.set(sheetTotal);
                refreshFileCounts(lineLog, fileName, sheetTotal, validateDone, writeDone, generateDone, false);
            }
            return GdBatchResult.success(fileName, written);
        } catch (Exception e) {
            ctx.logError("生成 GD 失败: " + e.getMessage());
            return GdBatchResult.fail(fileName);
        }
    }

    private static List<String> runPipelineParallel(final File file, final File workDir, final List<String> taskSheets,
        final LimitFileData limitData, final GdErrorCollector collector, final AtomicBoolean cancelFlag,
        final GdMultiLineProgressLog lineLog, final String fileName, final int sheetTotal,
        final AtomicInteger validateDone, final AtomicInteger writeDone, final AtomicInteger generateDone,
        final int batchRows, final int parallelism, final boolean keepExistingGd) throws Exception {
        final AtomicBoolean abort = new AtomicBoolean(false);
        final List<String> written = java.util.Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (final String sheetName : taskSheets) {
                futures.add(pool.submit((Callable<Void>) () -> {
                    if (abort.get() || cancelFlag.get() || collector.shouldStop()) {
                        return null;
                    }
                    GdSheetProgressReporter reporter = null;
                    if (lineLog != null) {
                        reporter = new GdSheetProgressReporter(sheetName, lineLog,
                                () -> refreshFileCounts(lineLog, fileName, sheetTotal, validateDone, writeDone,
                                    generateDone, cancelFlag.get()), batchRows);
                    }
                    File tmp = GdPathUtil.currGdTempFile(workDir, sheetName);
                    boolean ok = XlsxGdSheetPipeline.processSheet(file, sheetName, limitData, collector, tmp,
                        null, reporter);
                    if (!ok || collector.hasErrors()) {
                        abort.set(true);
                        dropSheetOutput(workDir, sheetName, keepExistingGd);
                        return null;
                    }
                    written.add(sheetName);
                    validateDone.incrementAndGet();
                    writeDone.incrementAndGet();
                    generateDone.incrementAndGet();
                    if (lineLog != null) {
                        reporter.flushNow();
                        refreshFileCounts(lineLog, fileName, sheetTotal, validateDone, writeDone, generateDone,
                            cancelFlag.get());
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            pool.shutdown();
        }
        if (abort.get() || cancelFlag.get() || collector.shouldStop() || written.size() != taskSheets.size()) {
            return new ArrayList<>();
        }
        return commitAllGdFiles(workDir, taskSheets);
    }

    private static List<String> commitAllGdFiles(File workDir, List<String> taskSheets) {
        List<String> committed = new ArrayList<>();
        for (String sheetName : taskSheets) {
            File tmp = GdPathUtil.currGdTempFile(workDir, sheetName);
            File target = GdPathUtil.currGdFile(workDir, sheetName);
            if (!tmp.exists()) {
                return new ArrayList<>();
            }
            if (target.exists() && !target.delete()) {
                throw new IllegalStateException("无法删除旧 gd: " + target.getAbsolutePath());
            }
            if (!tmp.renameTo(target)) {
                throw new IllegalStateException("无法提交 gd: " + target.getAbsolutePath());
            }
            committed.add(sheetName);
        }
        return committed;
    }

    private static void refreshFileCounts(GdMultiLineProgressLog lineLog, String fileName, int sheetTotal,
        AtomicInteger validateDone, AtomicInteger writeDone, AtomicInteger generateDone, boolean cancelled) {
        lineLog.updateFileCounts(fileName, sheetTotal, validateDone.get(), writeDone.get(), generateDone.get(),
            cancelled);
    }

    private static List<String> filterValidSheetNames(List<String> allSheets) {
        List<String> taskSheets = new ArrayList<>();
        if (allSheets == null) {
            return taskSheets;
        }
        for (String name : allSheets) {
            if (ExcelOperate.isValidSheetName(name)) {
                taskSheets.add(name);
            }
        }
        return taskSheets;
    }

    private static void rollbackNarrow(File workDir, List<String> taskSheetNames, boolean keepExistingGd) {
        if (taskSheetNames == null || workDir == null) {
            return;
        }
        for (String sheet : taskSheetNames) {
            dropSheetOutput(workDir, sheet, keepExistingGd);
        }
    }

    /** 失败时删 tmp；keepExistingGd 为 false 时连正式 gd 一起删。 */
    private static void dropSheetOutput(File workDir, String sheetName, boolean keepExistingGd) {
        if (keepExistingGd) {
            GdPathUtil.currGdTempFile(workDir, sheetName).delete();
            return;
        }
        XlsxGdSheetPipeline.deleteSheetOutputs(workDir, sheetName);
    }

    /**
     * @param fileName
     *            Excel 文件名
     * @param limitDir
     *            显式 limit 目录；null 则按 user.dir 上退解析
     * @return 列长数据；目录不存在时为空（不校验）
     */
    private static LimitFileData loadLimitFileData(String fileName, File limitDir) {
        File dir = limitDir != null ? limitDir : LimitPathUtil.resolveLimitDirFromCurrDir();
        if (dir == null || !dir.isDirectory()) {
            return new LimitFileData();
        }
        File limitFile = ColumnLengthLimitStore.buildLimitFile(dir, fileName);
        return ColumnLengthLimitStore.readFull(limitFile);
    }

    private static void logCollectorErrors(GdProgressContext ctx, GdErrorCollector collector) {
        List<String> errors = collector.getErrors();
        for (String error : errors) {
            ctx.logError("生成 gd 失败, " + error);
        }
        ctx.logError("Sheet 处理结束，结果=失败");
    }
}
