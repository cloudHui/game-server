package com.gamer.data.map.path.compare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gamer.data.map.path.common.PathRequest;
import com.gamer.data.map.path.common.PathResult;
import com.gamer.data.map.path.display.WorldPathRenderAdapter;
import com.gamer.data.map.path.tool.ToolPathStrategyType;
import com.gamer.data.map.path.world.WorldMapData;
import com.gamer.data.map.path.world.WorldMapDisplayMode;
import com.gamer.data.map.path.world.WorldPathRequest;
import com.gamer.data.map.path.world.WorldPathSearchResult;
import com.gamer.data.map.path.world.WorldServerPathStrategy;

/**
 * 运行选定的寻路模拟并捕获可比较的指标。
 */
public final class PathSimulationRunner {

    private PathSimulationRunner() {}

    /**
     * 运行一个或两个寻路模拟。
     *
     * @param mode
     *            运行模式
     * @param toolType
     *            ServerTool寻路策略类型
     * @param toolRequest
     *            ServerTool策略请求
     * @param worldMapData
     *            加载的WorldServer地图数据
     * @param startLandX
     *            起点大陆X坐标
     * @param startLandY
     *            起点大陆Y坐标
     * @param targetLandX
     *            终点大陆X坐标
     * @param targetLandY
     *            终点大陆Y坐标
     * @param recordSearch
     *            是否记录搜索展开点
     * @return 有序报告列表
     */
    public static List<PathSimulationReport> run(PathSimulationMode mode, ToolPathStrategyType toolType,
        final PathRequest toolRequest, final WorldMapData worldMapData, final int startLandX, final int startLandY,
        final int targetLandX, final int targetLandY, final boolean recordSearch,
        final WorldMapDisplayMode displayMode) {
        PathSimulationMode actualMode = mode == null ? PathSimulationMode.TOOL_ONLY : mode;
        List<PathSimulationReport> reports = new ArrayList<>();
        if (actualMode.isToolEnabled()) {
            reports.add(runTool(toolType, toolRequest));
        }
        if (actualMode.isWorldEnabled()) {
            reports.add(runWorld(worldMapData, startLandX, startLandY, targetLandX, targetLandY, recordSearch,
                displayMode));
        }
        return reports;
    }

    /**
     * 运行工具模拟。
     *
     * @param toolType
     *            ServerTool寻路策略类型
     * @param request
     *            ServerTool策略请求
     * @return 模拟报告
     */
    private static PathSimulationReport runTool(final ToolPathStrategyType toolType, final PathRequest request) {
        final ToolPathStrategyType actualType = toolType == null ? ToolPathStrategyType.GRID : toolType;
        return runMeasured(PathSimulationKind.TOOL, "工具模拟-" + actualType, () -> {
            PathResult result = actualType.getStrategy().findPath(request);
            return new SimulationTaskResult(result, -1L);
        });
    }

    /**
     * 运行WorldServer模拟。
     *
     * @param worldMapData
     *            WorldServer地图数据
     * @param startLandX
     *            起点大陆X坐标
     * @param startLandY
     *            起点大陆Y坐标
     * @param targetLandX
     *            终点大陆X坐标
     * @param targetLandY
     *            终点大陆Y坐标
     * @param recordSearch
     *            是否记录搜索展开点
     * @param displayMode
     *            显示模式
     * @return 模拟报告
     */
    private static PathSimulationReport runWorld(final WorldMapData worldMapData, final int startLandX,
        final int startLandY,
        final int targetLandX, final int targetLandY, final boolean recordSearch,
        final WorldMapDisplayMode displayMode) {
        return runMeasured(PathSimulationKind.WORLD, "WorldServer-AStar", () -> {
            WorldPathRequest request = new WorldPathRequest(worldMapData, startLandX, startLandY, targetLandX,
                targetLandY, recordSearch);
            WorldPathSearchResult result = new WorldServerPathStrategy().findPath(request);
            int mapHeight = worldMapData == null ? 0 : worldMapData.getHeight();
            PathResult pathResult = WorldPathRenderAdapter.toPathResult(result, mapHeight, displayMode);
            return new SimulationTaskResult(pathResult, result.getEstimatedMemoryBytes(),
                result.getExpandedCount());
        });
    }

    /**
     * 运行带统计采样的模拟任务。
     *
     * @param kind
     *            模拟类型
     * @param name
     *            名称
     * @param task
     *            模拟任务
     * @return 模拟报告
     */
    private static PathSimulationReport runMeasured(PathSimulationKind kind, String name, SimulationTask task) {
        long startNanos = System.nanoTime();
        SimulationTaskResult taskResult;
        try {
            taskResult = task.run();
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            PathResult fail = PathResult.fail("计算失败: " + message, Collections.emptyList(),
                Collections.emptyList());
            taskResult = new SimulationTaskResult(fail, -1L);
        }
        long elapsedNanos = System.nanoTime() - startNanos;
        return new PathSimulationReport(kind, name, taskResult.getPathResult(), elapsedNanos,
            taskResult.getEstimatedMemoryBytes(), taskResult.getExpandedCount());
    }

    /**
     * 带统计采样的模拟任务。
     */
    private interface SimulationTask {

        SimulationTaskResult run() throws Exception;
    }

    /**
     * 附加耗时统计前的原始任务结果。
     */
    private static final class SimulationTaskResult {

        private final PathResult pathResult;//路径结果

        private final long estimatedMemoryBytes;//搜索结构估算内存，单位字节

        private final int expandedCount;//展开节点数量

        SimulationTaskResult(PathResult pathResult, long estimatedMemoryBytes) {
            this(pathResult, estimatedMemoryBytes, -1);
        }

        SimulationTaskResult(PathResult pathResult, long estimatedMemoryBytes, int expandedCount) {
            this.pathResult = pathResult;
            this.estimatedMemoryBytes = estimatedMemoryBytes;
            this.expandedCount = expandedCount;
        }

        PathResult getPathResult() {
            return pathResult;
        }

        long getEstimatedMemoryBytes() {
            return estimatedMemoryBytes;
        }

        int getExpandedCount() {
            return expandedCount;
        }
    }
}
