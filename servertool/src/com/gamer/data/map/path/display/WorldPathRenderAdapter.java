package com.gamer.data.map.path.display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gamer.data.map.grid.MapRingUtil;
import com.gamer.data.map.path.common.PathBlockReason;
import com.gamer.data.map.path.common.PathFailureDetail;
import com.gamer.data.map.path.common.PathGridPoint;
import com.gamer.data.map.path.common.PathResult;
import com.gamer.data.map.path.common.PathWorldPoint;
import com.gamer.data.map.path.world.WorldLandPoint;
import com.gamer.data.map.path.world.WorldMapDisplayMode;
import com.gamer.data.map.path.world.WorldPathSearchResult;

/**
 * 将 WorldServer land 格结果转换为调试画布可直接绘制的路径 DTO。
 */
public final class WorldPathRenderAdapter {

    private WorldPathRenderAdapter() {}

    /**
     * 将 WorldServer 原始 land 路径转换为指定画布视角下的路径结果。
     *
     * @param success
     *            是否寻路成功
     * @param message
     *            结果说明
     * @param path
     *            WorldServer 原始 land 路径
     * @param searched
     *            WorldServer 原始 land 搜索展开点
     * @param mapHeight
     *            地图高度，单位为格
     * @param displayMode
     *            画布显示方向
     * @return 可绘制的路径结果
     */
    public static PathResult toPathResult(boolean success, String message, List<WorldLandPoint> path,
        List<WorldLandPoint> searched, int mapHeight, WorldMapDisplayMode displayMode) {
        WorldMapDisplayMode actualMode = PathCoordinateProjector.normalize(displayMode);
        List<PathGridPoint> gridPath = toGridPath(path, mapHeight, actualMode);
        List<PathWorldPoint> worldPath = toHexDisplayPath(path, mapHeight, actualMode);
        List<PathGridPoint> searchedGrids = toGridPath(searched, mapHeight, actualMode);
        if (success) {
            return new PathResult(true, message, gridPath, worldPath, searchedGrids,
                Collections.emptyList());
        }
        return PathResult.fail(message, searchedGrids, Collections.emptyList());
    }

    public static PathResult toPathResult(WorldPathSearchResult result, int mapHeight,
        WorldMapDisplayMode displayMode) {
        PathResult base = toPathResult(result.isSuccess(), result.getMessage(), result.getLandPath(),
            result.getSearchedLands(), mapHeight, displayMode);
        if (result.isSuccess() || result.getStuckLand() == null) {
            return base;
        }
        WorldMapDisplayMode actualMode = PathCoordinateProjector.normalize(displayMode);
        PathGridPoint stuckGrid = toGridPoint(result.getStuckLand(), mapHeight, actualMode);
        PathWorldPoint stuckWorld = toHexDisplayPoint(result.getStuckLand(), mapHeight, actualMode);
        List<PathBlockReason> reasons = new ArrayList<>();
        for (WorldLandPoint blocker : result.getBlockingLands()) {
            PathGridPoint grid = toGridPoint(blocker, mapHeight, actualMode);
            PathWorldPoint world = toHexDisplayPoint(blocker, mapHeight, actualMode);
            reasons.add(PathBlockReason.map("WorldServer阻挡格", world.getWorldX(), world.getWorldZ(), grid.getCol(),
                grid.getRow()));
        }
        return PathResult.fail(result.getMessage(), base.getSearchedGrids(), Collections.emptyList(),
            new PathFailureDetail(stuckGrid, stuckWorld, reasons));
    }

    private static List<PathGridPoint> toGridPath(List<WorldLandPoint> points, int mapHeight,
        WorldMapDisplayMode displayMode) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        List<PathGridPoint> ret = new ArrayList<>();
        for (WorldLandPoint point : points) {
            ret.add(toGridPoint(point, mapHeight, displayMode));
        }
        return ret;
    }

    private static PathGridPoint toGridPoint(WorldLandPoint point, int mapHeight,
        WorldMapDisplayMode displayMode) {
        return new PathGridPoint(point.getLandX(), PathCoordinateProjector.toDisplayY(point.getLandY(), mapHeight,
            displayMode));
    }

    private static List<PathWorldPoint> toHexDisplayPath(List<WorldLandPoint> points, int mapHeight,
        WorldMapDisplayMode displayMode) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        List<PathWorldPoint> ret = new ArrayList<>();
        for (WorldLandPoint point : points) {
            ret.add(toHexDisplayPoint(point, mapHeight, displayMode));
        }
        return ret;
    }

    /**
     * odd-r 六边形奇数显示行向右偏半格，避免 World 路径被画成纯方格中心线。
     */
    private static PathWorldPoint toHexDisplayPoint(WorldLandPoint point, int mapHeight,
        WorldMapDisplayMode displayMode) {
        int half = MapRingUtil.WORLD_PIXEL_PER_CELL / 2;
        int displayY = PathCoordinateProjector.toDisplayY(point.getLandY(), mapHeight, displayMode);
        int offsetX = (displayY & 1) == 1 ? half : 0;
        int worldX = point.getLandX() * MapRingUtil.WORLD_PIXEL_PER_CELL + half + offsetX;
        int worldZ = displayY * MapRingUtil.WORLD_PIXEL_PER_CELL + half;
        return new PathWorldPoint(worldX, worldZ);
    }
}
