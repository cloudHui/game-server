package com.gamer.data.map.path.tool.grid;

import com.gamer.data.map.path.common.PathCommonUtil;
import com.gamer.data.map.path.common.PathFindingStrategy;
import com.gamer.data.map.path.common.PathGridPoint;
import com.gamer.data.map.path.common.PathRequest;
import com.gamer.data.map.path.common.PathResult;

import java.util.Collections;
import java.util.List;

/**
 * 粗网格寻路策略。
 */
public class GridPathStrategy implements PathFindingStrategy {

    @Override
    public PathResult findPath(PathRequest request) {
        GridPathGrid grid = resolveGrid(request);
        GridPathGrid.CheckResult start = grid.checkWorldPoint(request.getStartWorldX(), request.getStartWorldZ());
        if (start.isNotValid()) {
            return PathResult.fail(start.getMessage(), Collections.emptyList(), Collections.emptyList(),
                start.getBlockReason());
        }
        GridPathGrid.CheckResult end = grid.checkWorldPoint(request.getEndWorldX(), request.getEndWorldZ());
        if (end.isNotValid()) {
            return PathResult.fail(end.getMessage(), Collections.emptyList(), Collections.emptyList(),
                end.getBlockReason());
        }
        GridAStarPathFinder.SearchResult result = GridAStarPathFinder.findPath(grid, start.getCol(), start.getRow(),
            end.getCol(), end.getRow(), request.isRecordSearch(), request.isAllowCutCorner());
        List<PathGridPoint> path = result.getPath();
        if (path.isEmpty()) {
            return PathResult.fail("无路径", result.getSearched(), Collections.emptyList(), result.getFailureDetail());
        }
        return new PathResult(true, "成功", path, PathCommonUtil.toWorldPath(path, request.getStartWorldX(),
            request.getStartWorldZ(), request.getEndWorldX(), request.getEndWorldZ()), result.getSearched(),
            Collections.emptyList());
    }

    /**
     * 解析粗网格。
     *
     * @param request
     *            寻路请求
     * @return 粗网格
     */
    private GridPathGrid resolveGrid(PathRequest request) {
        if (request instanceof GridPathRequest) {
            GridPathGrid grid = ((GridPathRequest)request).getGrid();
            if (grid != null) {
                return grid;
            }
        }
        return new GridPathGrid(request.getMapData(), request.getObstacles(), request.getObstacleIndex());
    }
}
