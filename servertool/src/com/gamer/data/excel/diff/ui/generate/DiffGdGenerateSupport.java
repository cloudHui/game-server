package com.gamer.data.excel.diff.ui.generate;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.gamer.data.log.Log;
import com.gamer.data.gdg.excel.ExcelOperate;
import com.gamer.data.gdg.generate.XlsxGdParallelProcessor;
import com.gamer.data.gdg.progress.GdProgressContext;
import com.gamer.data.gdg.util.GdBatchResult;
import com.gamer.data.gdg.util.GdProcessResult;

/**
 * 批量生成当前目录 Excel 的 GD 文件（不含差异对比）。
 */
public final class DiffGdGenerateSupport {

    /** 状态回传。 */
    public interface StatusSink {
        /**
         * @param statusText
         *            状态文案
         */
        void onStatus(String statusText);
    }

    /** 批量生成结果。 */
    public static final class Outcome {
        /** 成功文件数 */
        public final int okFiles;
        /** 失败文件数 */
        public final int failFiles;
        /** 成功写出的 Sheet 数 */
        public final int okSheets;
        /** 已写出的 Sheet 名（去重、保序） */
        public final List<String> writtenSheetNames;

        private Outcome(int okFiles, int failFiles, int okSheets, List<String> writtenSheetNames) {
            this.okFiles = okFiles;
            this.failFiles = failFiles;
            this.okSheets = okSheets;
            this.writtenSheetNames = writtenSheetNames;
        }
    }

    private final AtomicBoolean cancelFlag = new AtomicBoolean(false);
    private final Map<String, ExcelOperate> fileMap;

    /**
     * @param fileMap
     *            View 持有的 fileMap
     */
    public DiffGdGenerateSupport(Map<String, ExcelOperate> fileMap) {
        this.fileMap = fileMap;
    }

    /** 请求取消。 */
    public void requestCancel() {
        cancelFlag.set(true);
    }

    /**
     * @return 是否已取消
     */
    public boolean isCancelled() {
        return cancelFlag.get();
    }

    /**
     * 依次生成目录内全部 xlsx 的 GD。
     *
     * @return 生成结果
     */
    public Outcome generateAll(File[] excelFiles, Log log, GdProgressContext batch, StatusSink statusSink) {
        cancelFlag.set(false);
        int totalFiles = excelFiles.length;
        batch.logWait("共 " + totalFiles + " 个 Excel 待生成 GD，请稍候…");
        int ok = 0;
        int fail = 0;
        int totalSheets = 0;
        Set<String> written = new LinkedHashSet<>();
        for (int i = 0; i < excelFiles.length; i++) {
            if (cancelFlag.get()) {
                batch.logWait("生成已取消（已成功 " + ok + " 个文件）");
                break;
            }
            File file = excelFiles[i];
            int fileNo = i + 1;
            if (statusSink != null) {
                statusSink.onStatus(GdProgressContext.formatStatus("生成 GD", fileNo, totalFiles, file.getName()));
            }
            GdProcessResult result = generateOne(file, fileNo, totalFiles, log);
            if (result.isSuccess()) {
                ok++;
                totalSheets += result.getWrittenSheetNames().size();
                written.addAll(result.getWrittenSheetNames());
            } else {
                fail++;
                batch.logError("生成失败: " + file.getName() + "（详见上方日志）");
            }
        }
        return new Outcome(ok, fail, totalSheets, Collections.unmodifiableList(new ArrayList<>(written)));
    }

    private GdProcessResult generateOne(File file, int fileIndex, int fileTotal, Log log) {
        GdProgressContext ctx = new GdProgressContext(fileIndex, fileTotal, file.getName(), log);
        ExcelOperate eo = fileMap.get(file.getName());
        if (eo == null) {
            eo = new ExcelOperate(file);
        }
        if (eo.probeNotSheetNames(log)) {
            ctx.logError("读取 Sheet 列表失败: " + file.getName());
            return GdProcessResult.fail(file.getName());
        }
        List<String> allSheetNames = new ArrayList<>();
        List<String> probed = eo.getProbedSheetNames();
        if (probed != null) {
            allSheetNames.addAll(probed);
        }
        GdBatchResult batch = XlsxGdParallelProcessor.process(file, allSheetNames, ctx, cancelFlag);
        if (batch.isSuccess()) {
            return GdProcessResult.success(batch.getFileName(), batch.getWrittenSheetNames());
        }
        return GdProcessResult.fail(batch.getFileName());
    }
}
