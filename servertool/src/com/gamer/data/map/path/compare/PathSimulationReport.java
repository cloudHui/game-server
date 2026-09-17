package com.gamer.data.map.path.compare;

import com.gamer.data.map.path.common.PathResult;

/**
 * 单次寻路模拟结果，包含耗时、内存和算法统计。
 */
public final class PathSimulationReport {

    /** 模拟类型。 */
    private final PathSimulationKind kind;

    /** 日志显示名称。 */
    private final String name;

    /** 路径结果。 */
    private final PathResult pathResult;

    /** 运行耗时，单位纳秒。 */
    private final long elapsedNanos;

    /** 算法搜索结构估算内存，单位字节；-1 表示不可用。 */
    private final long estimatedMemoryBytes;

    /** 展开节点数量；-1 表示不可用。 */
    private final int expandedCount;

    /**
     * @param kind
     *            模拟类型
     * @param name
     *            日志显示名称
     * @param pathResult
     *            路径结果
     * @param elapsedNanos
     *            运行耗时，单位纳秒
     * @param estimatedMemoryBytes
     *            算法搜索结构估算内存，单位字节；-1 表示不可用
     * @param expandedCount
     *            展开节点数量；-1 表示不可用
     */
    public PathSimulationReport(PathSimulationKind kind, String name, PathResult pathResult, long elapsedNanos,
        long estimatedMemoryBytes, int expandedCount) {
        this.kind = kind;
        this.name = name;
        this.pathResult = pathResult;
        this.elapsedNanos = elapsedNanos;
        this.estimatedMemoryBytes = estimatedMemoryBytes;
        this.expandedCount = expandedCount;
    }

    public PathSimulationKind getKind() {
        return kind;
    }

    public String getName() {
        return name;
    }

    public PathResult getPathResult() {
        return pathResult;
    }

    /**
     * 保留本次模拟的耗时、堆变化、搜索内存估算等统计，只替换用于界面显示的路径结果。
     *
     * @param displayPathResult
     *            已按当前显示坐标系转换后的路径结果
     * @return 统计字段不变、路径结果替换后的报告
     */
    public PathSimulationReport withPathResult(PathResult displayPathResult) {
        return new PathSimulationReport(kind, name, displayPathResult, elapsedNanos, estimatedMemoryBytes,
            expandedCount);
    }

    public boolean isSuccess() {
        return pathResult != null && pathResult.isSuccess();
    }

    public long getElapsedMillis() {
        return elapsedNanos / 1000000L;
    }

    public long getEstimatedMemoryBytes() {
        return estimatedMemoryBytes;
    }

    public int getExpandedCount() {
        return expandedCount;
    }

    public int getSearchCount() {
        if (pathResult == null) {
            return 0;
        }
        return pathResult.getSearchedGrids().size() + pathResult.getSearchedWorlds().size();
    }

    public String getMessage() {
        return pathResult == null ? "" : pathResult.getMessage();
    }

    /**
     * @param bytes
     *            byte count
     * @return compact memory text
     */
    public static String formatMemory(long bytes) {
        long abs = Math.abs(bytes);
        if (abs >= 1024L * 1024L) {
            return bytes / 1024L / 1024L + " MB";
        }
        if (abs >= 1024L) {
            return bytes / 1024L + " KB";
        }
        return bytes + " B";
    }
}
