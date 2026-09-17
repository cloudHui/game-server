package com.gamer.data.gdg.progress;

import com.gamer.data.gdg.util.GdPipelineSettings;

/**
 * 单 Sheet 流式进度：仅统计有效数据行；读/校验/写同步递增。
 */
public class GdSheetProgressReporter {

    private static final long FLUSH_INTERVAL_MS = 400L;

    private final String sheetName;
    private final GdMultiLineProgressLog lineLog;
    private final Runnable fileSummaryRefresh;
    private final int batchRows;
    private int rowsRead;
    private int rowsValidated;
    private int rowsWritten;
    private int totalRows = -1;
    private int rowsSinceBatch;
    private long lastFlushMs;

    /**
     * @param sheetName
     *            Sheet 名
     * @param lineLog
     *            多行进度日志（可为 null）
     * @param fileSummaryRefresh
     *            刷新文件总进度（可为 null）
     * @param batchRows
     *            每批数据行刷新间隔（至少 1）
     */
    public GdSheetProgressReporter(String sheetName, GdMultiLineProgressLog lineLog, Runnable fileSummaryRefresh,
        int batchRows) {
        this.sheetName = sheetName;
        this.lineLog = lineLog;
        this.fileSummaryRefresh = fileSummaryRefresh;
        this.batchRows = batchRows > 0 ? batchRows : GdPipelineSettings.DEFAULT_BATCH_ROWS;
        this.lastFlushMs = 0L;
        if (lineLog != null) {
            lineLog.updateSheetMetrics(sheetName, 0, 0, 0, -1);
        }
    }

    /**
     * 完成一行有效数据行的校验并写入 GD（单遍流水线）。
     */
    public void onDataRowCommitted() {
        rowsRead++;
        rowsValidated++;
        rowsWritten++;
        bumpBatchFlush();
    }

    private void bumpBatchFlush() {
        rowsSinceBatch++;
        boolean force = rowsSinceBatch >= batchRows;
        if (force) {
            rowsSinceBatch = 0;
        }
        flushIfNeeded(force);
    }

    /**
     * 流水线结束后设置数据行总数。
     */
    public void setTotalRows(int total) {
        totalRows = total;
        flushIfNeeded(true);
    }

    public void flushNow() {
        flushIfNeeded(true);
    }

    private void flushIfNeeded(boolean force) {
        if (lineLog == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (!force && now - lastFlushMs < FLUSH_INTERVAL_MS) {
            return;
        }
        lastFlushMs = now;
        lineLog.updateSheetMetrics(sheetName, rowsRead, rowsValidated, rowsWritten, totalRows);
        if (fileSummaryRefresh != null) {
            fileSummaryRefresh.run();
        }
    }
}
