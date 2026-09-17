package com.gamer.data.map.path.tool.hybrid;

import com.gamer.data.map.path.common.PathCommonUtil;
import com.gamer.data.map.path.common.PathFindingStrategy;
import com.gamer.data.map.path.common.PathFailureDetail;
import com.gamer.data.map.path.common.PathObstacle;
import com.gamer.data.map.path.common.PathRequest;
import com.gamer.data.map.path.common.PathResult;
import com.gamer.data.map.path.common.PathWorldPoint;
import com.gamer.data.map.path.tool.grid.GridPathStrategy;
import com.gamer.data.map.path.tool.point.PointPathStrategy;

import java.util.List;

/**
 * 粗格导航 + 连续安全校验策略。
 */
public class HybridPathStrategy implements PathFindingStrategy {

    private final GridPathStrategy gridStrategy = new GridPathStrategy();

    private final PointPathStrategy pointStrategy = new PointPathStrategy();

    @Override
    public PathResult findPath(PathRequest request) {
        PathResult gridResult = gridStrategy.findPath(request);
        if (!gridResult.isSuccess()) {
            PathResult pointResult = pointStrategy.findPath(request);
            if (pointResult.isSuccess()) {
                return new PathResult(true, "粗格无路径，已使用细坐标路径", pointResult.getGridPath(),
                    pointResult.getWorldPath(), gridResult.getSearchedGrids(), pointResult.getSearchedWorlds());
            }
            return PathResult.fail("粗格与细坐标均无路径", gridResult.getSearchedGrids(),
                pointResult.getSearchedWorlds(), selectFailureDetail(pointResult, gridResult));
        }
        List<PathObstacle> obstacles = request.getObstacles();
        if (isWorldPathSafe(request, obstacles, gridResult.getWorldPath())) {
            return new PathResult(true, "粗格路径通过连续校验", gridResult.getGridPath(), gridResult.getWorldPath(),
                gridResult.getSearchedGrids(), gridResult.getSearchedWorlds());
        }
        // 粗格路径碰撞：以粗格路线为走廊做细坐标搜索，走廊内无解会在策略内部自动整图回退
        PathResult pointResult = pointStrategy.findPath(request, gridResult.getWorldPath());
        if (pointResult.isSuccess()) {
            return new PathResult(true, "粗格路径碰撞，已使用细坐标路径", pointResult.getGridPath(),
                pointResult.getWorldPath(), gridResult.getSearchedGrids(), pointResult.getSearchedWorlds());
        }
        return PathResult.fail("粗格路径碰撞，细坐标也无路径", gridResult.getSearchedGrids(),
            pointResult.getSearchedWorlds(), selectFailureDetail(pointResult, gridResult));
    }

    /**
     * 选择失败原因。
     *
     * @param primary
     *            主要结果
     * @param fallback
     *            备用结果
     * @return 失败原因
     */
    private static PathFailureDetail selectFailureDetail(PathResult primary, PathResult fallback) {
        if (primary != null && primary.getFailureDetail().isPresent()) {
            return primary.getFailureDetail();
        }
        return fallback == null ? PathFailureDetail.none() : fallback.getFailureDetail();
    }

    private static boolean isWorldPathSafe(PathRequest request, List<PathObstacle> obstacles,
        List<PathWorldPoint> worldPath) {
        if (worldPath == null || worldPath.isEmpty()) {
            return false;
        }
        PathWorldPoint prev = new PathWorldPoint(request.getStartWorldX(), request.getStartWorldZ());
        for (PathWorldPoint cur : worldPath) {
            if (!PathCommonUtil.isSafeSegment(request.getMapData(), obstacles, request.getObstacleIndex(), prev, cur)) {
                return false;
            }
            prev = cur;
        }
        PathWorldPoint end = new PathWorldPoint(request.getEndWorldX(), request.getEndWorldZ());
        return PathCommonUtil.isSafeSegment(request.getMapData(), obstacles, request.getObstacleIndex(), prev, end);
    }
}
