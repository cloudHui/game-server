package com.gamer.data.map.path.display;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gamer.data.map.grid.MapRingUtil;
import com.gamer.data.map.level.LevelNodeBean;
import com.gamer.data.map.path.common.PathBlockReason;
import com.gamer.data.map.path.common.PathFailureDetail;
import com.gamer.data.map.path.common.PathGridPoint;
import com.gamer.data.map.path.common.PathObstacle;
import com.gamer.data.map.path.common.PathResult;
import com.gamer.data.map.path.common.PathWorldPoint;
import com.gamer.data.map.path.world.WorldMapData;
import com.gamer.data.map.path.world.WorldMapDisplayMode;

/**
 * 寻路调试界面的显示坐标适配器。
 * 算法始终使用各自真实坐标：Tool 使用 Tool 地图坐标，World 使用 World 原始 land 坐标。
 * 本类只负责把这些真实坐标转换成当前画布视角下的显示坐标，避免显示层和算法层互相污染。
 */
public final class CoordinateAdapter {

    private CoordinateAdapter() {}

    /**
     * 将当前画布显示的 Z 坐标转换成 Tool 算法需要的 Z 坐标。
     *
     * @param displayWorldZ
     *            画布显示坐标 Z
     * @param height
     *            地图高度，单位为格
     * @param displayMode
     *            当前画布视角
     * @return Tool 算法坐标 Z
     */
    public static int toToolWorldZ(int displayWorldZ, int height, WorldMapDisplayMode displayMode) {
        if (displayMode != WorldMapDisplayMode.WORLD_RAW) {
            return displayWorldZ;
        }
        return flipWorldZ(displayWorldZ, height);
    }

    /**
     * 将当前画布显示坐标转换成 WorldServer 原始 land 坐标。
     *
     * @param worldMapData
     *            World 地图数据
     * @param displayWorldX
     *            画布显示坐标 X
     * @param displayWorldZ
     *            画布显示坐标 Z
     * @param displayMode
     *            当前画布视角
     * @return WorldServer 原始 land 坐标
     */
    public static PathGridPoint toWorldLandPoint(WorldMapData worldMapData, int displayWorldX, int displayWorldZ,
        WorldMapDisplayMode displayMode) {
        if (worldMapData == null) {
            return null;
        }
        WorldMapDisplayMode actualMode = PathCoordinateProjector.normalize(displayMode);
        int landX = MapRingUtil.worldToTileX(displayWorldX, worldMapData.getWidth());
        int displayY = MapRingUtil.worldToTileZ(displayWorldZ, worldMapData.getHeight());
        return new PathGridPoint(landX, PathCoordinateProjector.toSourceY(displayY, worldMapData.getHeight(),
            actualMode));
    }

    /**
     * 将关卡节点转换到当前画布视角。Tool 视角无需复制；World 原始视角需要翻转 Z。
     *
     * @param nodes
     *            原始关卡节点
     * @param displayMode
     *            当前画布视角
     * @param mapHeight
     *            地图高度，单位为格
     * @return 用于画布显示的节点列表
     */
    public static List<LevelNodeBean> toDisplayLevelNodes(List<LevelNodeBean> nodes, WorldMapDisplayMode displayMode,
        int mapHeight) {
        if (displayMode != WorldMapDisplayMode.WORLD_RAW) {
            return nodes;
        }
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<LevelNodeBean> ret = new ArrayList<>();
        for (LevelNodeBean node : nodes) {
            LevelNodeBean copy = node.copy();
            copy.setZ(flipWorldZ(node.getZ(), mapHeight));
            ret.add(copy);
        }
        return ret;
    }

    /**
     * 将 Tool 路径结果转换到当前画布视角。World 视角下需要翻转所有格点、世界点和搜索点。
     *
     * @param result
     *            Tool 算法原始结果
     * @param displayMode
     *            当前画布视角
     * @param mapHeight
     *            地图高度，单位为格
     * @return 用于显示的路径结果
     */
    public static PathResult toDisplayToolResult(PathResult result, WorldMapDisplayMode displayMode, int mapHeight) {
        if (result == null || displayMode != WorldMapDisplayMode.WORLD_RAW) {
            return result;
        }
        return new PathResult(result.isSuccess(), result.getMessage(), flipGridPoints(result.getGridPath(), mapHeight),
            flipWorldPoints(result.getWorldPath(), mapHeight), flipGridPoints(result.getSearchedGrids(), mapHeight),
            flipWorldPoints(result.getSearchedWorlds(), mapHeight), toDisplayBlockReason(result.getBlockReason(),
                displayMode, mapHeight),
            toDisplayFailureDetail(result.getFailureDetail(), displayMode, mapHeight));
    }

    private static PathFailureDetail toDisplayFailureDetail(PathFailureDetail detail,
        WorldMapDisplayMode displayMode, int height) {
        if (detail == null || !detail.isPresent()) {
            return PathFailureDetail.none();
        }
        PathGridPoint stuckGrid = detail.getStuckGrid();
        if (stuckGrid != null && displayMode == WorldMapDisplayMode.WORLD_RAW) {
            stuckGrid = new PathGridPoint(stuckGrid.getCol(), height - 1 - stuckGrid.getRow());
        }
        PathWorldPoint stuckWorld = detail.getStuckWorld();
        if (stuckWorld != null && displayMode == WorldMapDisplayMode.WORLD_RAW) {
            stuckWorld = new PathWorldPoint(stuckWorld.getWorldX(), flipWorldZ(stuckWorld.getWorldZ(), height));
        }
        List<PathBlockReason> reasons = new ArrayList<>();
        for (PathBlockReason reason : detail.getBlockReasons()) {
            reasons.add(toDisplayBlockReason(reason, displayMode, height));
        }
        return new PathFailureDetail(stuckGrid, stuckWorld, reasons);
    }

    /**
     * 将 Tool 格点列表转换到当前画布视角。
     *
     * @param points
     *            Tool 原始格点
     * @param displayMode
     *            当前画布视角
     * @param mapHeight
     *            地图高度，单位为格
     * @return 用于显示的格点
     */
    public static List<PathGridPoint> toDisplayGridPoints(List<PathGridPoint> points, WorldMapDisplayMode displayMode,
        int mapHeight) {
        if (displayMode != WorldMapDisplayMode.WORLD_RAW) {
            return points;
        }
        return flipGridPoints(points, mapHeight);
    }

    private static int flipWorldZ(int worldZ, int height) {
        return PathCoordinateProjector.toDisplayWorldZ(worldZ, height, WorldMapDisplayMode.TOOL_VIEW);
    }

    private static List<PathGridPoint> flipGridPoints(List<PathGridPoint> points, int height) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        List<PathGridPoint> ret = new ArrayList<>();
        for (PathGridPoint point : points) {
            ret.add(new PathGridPoint(point.getCol(), height - 1 - point.getRow()));
        }
        return ret;
    }

    private static List<PathWorldPoint> flipWorldPoints(List<PathWorldPoint> points, int height) {
        if (points == null || points.isEmpty()) {
            return Collections.emptyList();
        }
        List<PathWorldPoint> ret = new ArrayList<>();
        for (PathWorldPoint point : points) {
            ret.add(new PathWorldPoint(point.getWorldX(), flipWorldZ(point.getWorldZ(), height)));
        }
        return ret;
    }

    public static PathBlockReason toDisplayBlockReason(PathBlockReason reason, WorldMapDisplayMode displayMode,
        int height) {
        if (displayMode != WorldMapDisplayMode.WORLD_RAW) {
            return reason == null ? PathBlockReason.none() : reason;
        }
        if (reason == null || !reason.isPresent()) {
            return PathBlockReason.none();
        }
        int worldZ = reason.getWorldZ() < 0 ? reason.getWorldZ() : flipWorldZ(reason.getWorldZ(), height);
        int row = reason.getRow() < 0 ? reason.getRow() : height - 1 - reason.getRow();
        if (reason.getObstacle() != null) {
            LevelNodeBean node = reason.getObstacle().getNode().copy();
            node.setZ(flipWorldZ(node.getZ(), height));
            return PathBlockReason.obstacle(reason.getMessage(), reason.getWorldX(), worldZ, reason.getCol(), row,
                new PathObstacle(node));
        }
        if (reason.getType() == PathBlockReason.TYPE_OUT_OF_BOUNDS) {
            return PathBlockReason.outOfBounds(reason.getMessage(), reason.getWorldX(), worldZ);
        }
        return PathBlockReason.map(reason.getMessage(), reason.getWorldX(), worldZ, reason.getCol(), row);
    }
}
