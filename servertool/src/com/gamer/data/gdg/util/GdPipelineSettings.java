package com.gamer.data.gdg.util;

/**
 * GD 流水线参数（可通过 JVM 系统属性覆盖）。
 */
public final class GdPipelineSettings {

    /** 每批处理的数据行数（校验通过即写入，并刷新进度） */
    public static final String PROP_BATCH_ROWS = "gow.gd.batchRows";
    /** Sheet 并行线程数，0 表示与合法 Sheet 数相同 */
    public static final String PROP_PARALLELISM = "gow.gd.parallelism";

    /** 默认每批行数 */
    public static final int DEFAULT_BATCH_ROWS = 20;
    /** 0 = 按 Sheet 数开线程 */
    public static final int DEFAULT_PARALLELISM = 0;
    /** 并行上限，避免超大表文件过多线程 */
    public static final int MAX_PARALLELISM = 16;

    private GdPipelineSettings() {
    }

    /**
     * @return 每批数据行数（至少 1）
     */
    public static int getBatchRows() {
        return parsePositiveInt(System.getProperty(PROP_BATCH_ROWS), DEFAULT_BATCH_ROWS);
    }

    /**
     * @param sheetCount 合法 Sheet 数量
     * @return 并行线程数
     */
    public static int resolveParallelism(int sheetCount) {
        int configured = parsePositiveInt(System.getProperty(PROP_PARALLELISM), DEFAULT_PARALLELISM);
        if (sheetCount <= 0) {
            return 1;
        }
        if (configured <= 0) {
            return Math.min(sheetCount, MAX_PARALLELISM);
        }
        return Math.min(Math.min(configured, sheetCount), MAX_PARALLELISM);
    }

    private static int parsePositiveInt(String raw, int defaultValue) {
        if (raw == null || raw.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : defaultValue;
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
