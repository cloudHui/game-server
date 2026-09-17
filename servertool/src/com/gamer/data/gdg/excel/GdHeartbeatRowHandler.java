package com.gamer.data.gdg.excel;

import java.util.TreeMap;

import com.gamer.data.log.Log;

import com.gamer.data.gdg.progress.GdProgressContext;

/**
 * 流式读表心跳日志（大表防长时间无输出）。
 */
public class GdHeartbeatRowHandler implements GdStreamRowHandler {

    private static final int ROW_INTERVAL = 2000;
    private static final long TIME_INTERVAL_MS = 3000L;

    private final GdStreamRowHandler delegate;
    private final Log log;
    private final String prefix;
    private int processedRows;
    private long lastHeartbeatMs;

    public static GdStreamRowHandler wrap(GdStreamRowHandler delegate, Log log, String prefix) {
        if (log == null) {
            return delegate;
        }
        return new GdHeartbeatRowHandler(delegate, log, prefix);
    }

    private GdHeartbeatRowHandler(GdStreamRowHandler delegate, Log log, String prefix) {
        this.delegate = delegate;
        this.log = log;
        this.prefix = prefix;
        this.lastHeartbeatMs = System.currentTimeMillis();
    }

    @Override
    public void onFormulaCell(int rowNum0, int colIdx) {
        delegate.onFormulaCell(rowNum0, colIdx);
    }

    @Override
    public void onRowEnd(int rowNum0, TreeMap<Integer, String> colValues) {
        delegate.onRowEnd(rowNum0, colValues);
        processedRows++;
        long now = System.currentTimeMillis();
        if (processedRows % ROW_INTERVAL != 0 && now - lastHeartbeatMs < TIME_INTERVAL_MS) {
            return;
        }
        lastHeartbeatMs = now;
        GdProgressContext.logHeartbeat(log, prefix, rowNum0);
    }
}
