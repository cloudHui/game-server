package com.gamer.data.map.path.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.gamer.data.map.grid.MapData;
import com.gamer.data.map.grid.MapRingUtil;
import com.gamer.data.map.level.LevelNodeBean;
import com.gamer.data.map.level.NodeType;

/**
 * 寻路公共工具。
 */
public final class PathCommonUtil {

    private PathCommonUtil() {}

    /**
     * 构建物体阻挡列表。
     *
     * @param levelNodes
     *            等级节点列表
     * @return 物体阻挡列表
     */
    public static List<PathObstacle> buildObstacles(List<LevelNodeBean> levelNodes) {
        if (levelNodes == null || levelNodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<PathObstacle> ret = new ArrayList<>();
        for (LevelNodeBean node : levelNodes) {
            if (node != null && isBlockNodeType(node.getType())) {
                ret.add(new PathObstacle(node));
            }
        }
        return ret;
    }

    /**
     * 判断节点类型是否为阻挡类型。
     *
     * @param type
     *            节点类型
     * @return 是否为阻挡类型
     */
    public static boolean isBlockNodeType(int type) {
        return type == NodeType.ENEMY.getId() || type == NodeType.LEVEL_BUILDING.getId()
            || type == NodeType.NPC.getId() || type == NodeType.RESOURCE.getId() || type == NodeType.REWARD.getId()
            || type == NodeType.EVENT.getId();
    }

    /**
     * 判断世界点是否不在地图范围内。
     *
     * @param mapData
     *            地图数据
     * @param worldX
     *            世界X坐标
     * @param worldZ
     *            世界Z坐标
     * @return 是否不在地图范围内
     */
    public static boolean isNotWorldInBounds(MapData mapData, int worldX, int worldZ) {
        return mapData == null || mapData.getMap() == null || worldX < 0 || worldZ < 0
            || worldX >= mapData.getWidth() * MapRingUtil.WORLD_PIXEL_PER_CELL
            || worldZ >= mapData.getHeight() * MapRingUtil.WORLD_PIXEL_PER_CELL;
    }

    /**
     * 判断世界点是否不可走。
     *
     * @param mapData
     *            地图数据
     * @param worldX
     *            世界X坐标
     * @param worldZ
     *            世界Z坐标
     * @return 是否不可走
     */
    public static boolean isNotBaseWorldWalkable(MapData mapData, int worldX, int worldZ) {
        if (isNotWorldInBounds(mapData, worldX, worldZ)) {
            return true;
        }
        int col = MapRingUtil.worldToTileX(worldX, mapData.getWidth());
        int row = MapRingUtil.worldToTileZ(worldZ, mapData.getHeight());
        return mapData.getMap()[col][row] == 0;
    }

    /**
     * 判断世界点是否安全。
     *
     * @param mapData
     *            地图数据
     * @param obstacles
     *            物体阻挡列表
     * @param index
     *            物体阻挡索引
     * @param worldX
     *            世界X坐标
     * @param worldZ
     *            世界Z坐标
     * @return 是否安全
     */
    public static boolean isSafeWorldPoint(MapData mapData, List<PathObstacle> obstacles, PathObstacleIndex index,
        int worldX, int worldZ) {
        if (isNotBaseWorldWalkable(mapData, worldX, worldZ)) {
            return false;
        }
        int avoid = PathObstacle.AVOID_RADIUS;
        int avoidSq = avoid * avoid;
        if (index != null) {
            return index.findObstacleWithin(worldX, worldZ, avoid) == null;
        }
        List<PathObstacle> nearObstacles = obstacles == null ? Collections.emptyList() : obstacles;
        for (PathObstacle obstacle : nearObstacles) {
            int dx = worldX - obstacle.getNode().getX();
            int dz = worldZ - obstacle.getNode().getZ();
            if (dx * dx + dz * dz <= avoidSq) {
                return false;
            }
        }
        return true;
    }

    /**
     * 诊断世界点阻挡原因。
     *
     * @param mapData
     *            地图数据
     * @param obstacles
     *            物体阻挡列表
     * @param index
     *            物体阻挡索引
     * @param worldX
     *            世界X坐标
     * @param worldZ
     *            世界Z坐标
     * @return 阻挡原因
     */
    public static PathBlockReason diagnoseWorldPoint(MapData mapData, List<PathObstacle> obstacles,
        PathObstacleIndex index, int worldX, int worldZ) {
        if (isNotWorldInBounds(mapData, worldX, worldZ)) {
            return PathBlockReason.outOfBounds("点不在地图范围内", worldX, worldZ);
        }
        int col = MapRingUtil.worldToTileX(worldX, mapData.getWidth());
        int row = MapRingUtil.worldToTileZ(worldZ, mapData.getHeight());
        if (mapData.getMap()[col][row] == 0) {
            return PathBlockReason.map("地图格不可走", worldX, worldZ, col, row);
        }
        PathObstacle obstacle = index == null ? findObstacleWithin(obstacles, worldX, worldZ)
            : index.findObstacleWithin(worldX, worldZ, PathObstacle.AVOID_RADIUS);
        if (obstacle != null) {
            return PathBlockReason.obstacle("被固定节点安全半径阻挡", worldX, worldZ, col, row, obstacle);
        }
        return PathBlockReason.none();
    }

    /**
     * 查找物体阻挡。
     *
     * @param obstacles 物体阻挡列表
     * @param worldX    世界X坐标
     * @param worldZ    世界Z坐标
     * @return 物体阻挡
     */
    private static PathObstacle findObstacleWithin(List<PathObstacle> obstacles, int worldX, int worldZ) {
        if (obstacles == null || obstacles.isEmpty()) {
            return null;
        }
        int radiusSq = PathObstacle.AVOID_RADIUS * PathObstacle.AVOID_RADIUS;
        for (PathObstacle obstacle : obstacles) {
            int dx = worldX - obstacle.getNode().getX();
            int dz = worldZ - obstacle.getNode().getZ();
            if (dx * dx + dz * dz <= radiusSq) {
                return obstacle;
            }
        }
        return null;
    }

    /**
     * 判断线段是否安全。
     *
     * @param mapData
     *            地图数据
     * @param obstacles
     *            物体阻挡列表
     * @param index
     *            物体阻挡索引
     * @param from
     *            起点
     * @param to
     *            终点
     * @return 是否安全
     */
    public static boolean isSafeSegment(MapData mapData, List<PathObstacle> obstacles, PathObstacleIndex index,
        PathWorldPoint from, PathWorldPoint to) {
        int dx = to.getWorldX() - from.getWorldX();
        int dz = to.getWorldZ() - from.getWorldZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        if (steps <= 0) {
            return isSafeWorldPoint(mapData, obstacles, index, from.getWorldX(), from.getWorldZ());
        }
        for (int i = 0; i <= steps; i++) {
            int x = from.getWorldX() + dx * i / steps;
            int z = from.getWorldZ() + dz * i / steps;
            if (!isSafeWorldPoint(mapData, obstacles, index, x, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 转换网格路径为世界路径。
     *
     * @param gridPath
     *            网格路径
     * @param startWorldX
     *            起点世界X坐标
     * @param startWorldZ
     *            起点世界Z坐标
     * @param endWorldX
     *            终点世界X坐标
     * @param endWorldZ
     *            终点世界Z坐标
     * @return 世界路径
     */
    public static List<PathWorldPoint> toWorldPath(List<PathGridPoint> gridPath, int startWorldX, int startWorldZ,
        int endWorldX, int endWorldZ) {
        List<PathWorldPoint> ret = new ArrayList<>();
        ret.add(new PathWorldPoint(startWorldX, startWorldZ));
        if (gridPath != null) {
            for (PathGridPoint point : gridPath) {
                appendWorldPoint(ret, point.getWorldX(), point.getWorldZ());
            }
        }
        appendWorldPoint(ret, endWorldX, endWorldZ);
        return ret;
    }

    /**
     * 添加世界点。
     *
     * @param points
     *            世界点列表
     * @param worldX
     *            世界X坐标
     * @param worldZ
     *            世界Z坐标
     */
    private static void appendWorldPoint(List<PathWorldPoint> points, int worldX, int worldZ) {
        if (!points.isEmpty()) {
            PathWorldPoint last = points.get(points.size() - 1);
            if (last.getWorldX() == worldX && last.getWorldZ() == worldZ) {
                return;
            }
        }
        points.add(new PathWorldPoint(worldX, worldZ));
    }

    /**
     * 转换世界路径为网格路径。
     *
     * @param mapData
     *            地图数据
     * @param worldPath
     *            世界路径
     * @return 网格路径
     */
    public static List<PathGridPoint> toGridPath(MapData mapData, List<PathWorldPoint> worldPath) {
        if (mapData == null || worldPath == null || worldPath.isEmpty()) {
            return Collections.emptyList();
        }
        List<PathGridPoint> ret = new ArrayList<>();
        int lastCol = -1;
        int lastRow = -1;
        for (PathWorldPoint point : worldPath) {
            int col = MapRingUtil.worldToTileX(point.getWorldX(), mapData.getWidth());
            int row = MapRingUtil.worldToTileZ(point.getWorldZ(), mapData.getHeight());
            if (col != lastCol || row != lastRow) {
                ret.add(new PathGridPoint(col, row));
                lastCol = col;
                lastRow = row;
            }
        }
        return ret;
    }
}
