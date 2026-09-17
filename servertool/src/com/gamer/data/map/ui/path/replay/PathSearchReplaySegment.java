package com.gamer.data.map.ui.path.replay;

import java.util.Collections;
import java.util.List;

import com.gamer.data.map.path.common.PathGridPoint;
import com.gamer.data.map.path.common.PathWorldPoint;

/**
 * 一段有序的搜索回放数据（粗格或世界坐标其一）。
 */
public final class PathSearchReplaySegment {

    /** 回放阶段类型 */
    private final PathSearchReplayStage stage;

    /** 格子点（按出队/关闭顺序），仅粗格阶段使用 */
    private final List<PathGridPoint> gridPoints;

    /** 世界坐标点（按出队/关闭顺序），仅世界坐标阶段使用 */
    private final List<PathWorldPoint> worldPoints;

    /** 下一个待发布的点索引 */
    private int cursor;

    private PathSearchReplaySegment(PathSearchReplayStage stage, List<PathGridPoint> gridPoints,
        List<PathWorldPoint> worldPoints) {
        this.stage = stage;
        this.gridPoints = gridPoints == null ? Collections.emptyList() : gridPoints;
        this.worldPoints = worldPoints == null ? Collections.emptyList() : worldPoints;
    }

    /**
     * 创建粗格回放段。
     *
     * @param gridPoints
     *            已搜索的格子点
     * @return 回放段
     */
    public static PathSearchReplaySegment gridSearch(List<PathGridPoint> gridPoints) {
        return new PathSearchReplaySegment(PathSearchReplayStage.GRID_SEARCH, gridPoints, null);
    }

    /**
     * 创建世界坐标回放段。
     *
     * @param worldPoints
     *            已搜索的世界坐标点
     * @return 回放段
     */
    public static PathSearchReplaySegment pointSearch(List<PathWorldPoint> worldPoints) {
        return new PathSearchReplaySegment(PathSearchReplayStage.POINT_SEARCH, null, worldPoints);
    }

    /**
     * 将下一批点追加到可见回放列表。
     *
     * @param visibleGridPoints  可见格子点目标列表
     * @param visibleWorldPoints 可见世界坐标点目标列表
     * @param batchSize          本批最多追加的点数
     */
    public void appendNext(List<PathGridPoint> visibleGridPoints, List<PathWorldPoint> visibleWorldPoints,
                           int batchSize) {
        if (batchSize <= 0 || isDone()) {
            return;
        }
        int appended = 0;
        // 按阶段类型把光标处的点追加到对应可见列表，直到达到批量上限或本段发布完毕
        while (appended < batchSize && !isDone()) {
            if (stage == PathSearchReplayStage.GRID_SEARCH) {
                visibleGridPoints.add(gridPoints.get(cursor));
            } else {
                visibleWorldPoints.add(worldPoints.get(cursor));
            }
            cursor++;
            appended++;
        }
    }

    /**
     * @return 本段是否已全部发布
     */
    public boolean isDone() {
        return cursor >= getTotalCount();
    }

    /**
     * @return 本段总点数
     */
    public int getTotalCount() {
        return stage == PathSearchReplayStage.GRID_SEARCH ? gridPoints.size() : worldPoints.size();
    }
}
