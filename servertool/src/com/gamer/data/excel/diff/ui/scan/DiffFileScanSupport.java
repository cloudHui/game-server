package com.gamer.data.excel.diff.ui.scan;

import static com.gamer.data.excel.ui.ViewUtils.formatFileSizeForLog;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import com.gamer.data.log.Log;
import com.gamer.data.gdg.excel.ExcelOperate;
import com.gamer.data.gdg.progress.GdProgressContext;

/**
 * 扫描上层 / 当前目录 xlsx 并登记可读文件（不打开 Workbook）。
 * <p>
 * 只做目录 IO 与 probeReadable；列表刷新由 View 在 EDT 完成。
 * </p>
 */
public final class DiffFileScanSupport {

    /** 列目录回调（与 AbstractViewFrameCore#listExcelFiles 同规则）。 */
    public interface ExcelLister {
        /**
         * @param dir
         *            目录
         * @return xlsx 数组，可为 null
         */
        File[] listExcelFiles(File dir);
    }

    /** 进度回传。 */
    public interface ProgressSink {
        /**
         * @param current
         *            当前
         * @param total
         *            总数
         * @param itemName
         *            项名
         */
        void onProgress(int current, int total, String itemName);
    }

    private final ExcelLister lister;

    /**
     * @param lister
     *            列目录实现
     */
    public DiffFileScanSupport(ExcelLister lister) {
        this.lister = lister;
    }

    /**
     * 扫描上层目录英文 xlsx（仅登记路径）。
     *
     * @param currDir
     *            当前目录
     * @param batch
     *            批次进度
     * @return 文件名 → File
     */
    public Map<String, File> scanParentDir(File currDir, GdProgressContext batch) {
        Map<String, File> map = new HashMap<>();
        File parentDir = currDir == null ? null : currDir.getParentFile();
        if (parentDir == null || !parentDir.exists()) {
            batch.logError("上层目录不存在");
            return map;
        }
        batch.logWait("开始扫描上层目录: " + parentDir.getAbsolutePath());
        File[] parentFiles = lister.listExcelFiles(parentDir);
        int parentTotal = parentFiles == null ? 0 : parentFiles.length;
        batch.logIndexedProgress(0, Math.max(parentTotal, 1), "上层目录 xlsx 共 " + parentTotal + " 个");
        if (parentFiles != null) {
            for (File file : parentFiles) {
                map.put(file.getName(), file);
            }
        }
        return map;
    }

    /**
     * 登记当前目录可读 xlsx。
     *
     * @param files
     *            文件数组
     * @param scanTotal
     *            扫描总数（进度分母）
     * @param batch
     *            批次进度
     * @param progressSink
     *            进度条回传，可为 null
     * @return 文件名 → ExcelOperate
     */
    public Map<String, ExcelOperate> registerReadable(File[] files, int scanTotal, GdProgressContext batch,
        ProgressSink progressSink) {
        Map<String, ExcelOperate> map = new HashMap<>();
        if (files == null) {
            return map;
        }
        Log log = batch.getLog();
        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            int fileNo = i + 1;
            if (progressSink != null) {
                progressSink.onProgress(fileNo, scanTotal, file.getName());
            }
            batch.logIndexedProgress(fileNo, scanTotal,
                "登记 " + file.getName() + " (" + formatFileSizeForLog(file.length()) + ")");
            ExcelOperate eo = new ExcelOperate(file);
            if (eo.probeReadable(log)) {
                map.put(file.getName(), eo);
            }
        }
        return map;
    }
}
