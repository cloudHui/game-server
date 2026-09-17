package com.gamer.data.map.path.tool.grid;

import com.gamer.data.map.grid.MapData;
import com.gamer.data.map.grid.MapRingUtil;
import com.gamer.data.map.path.common.PathCommonUtil;
import com.gamer.data.map.path.common.PathBlockReason;
import com.gamer.data.map.path.common.PathGridPoint;
import com.gamer.data.map.path.common.PathObstacle;
import com.gamer.data.map.path.common.PathObstacleIndex;

import java.util.ArrayList;
import java.util.List;

/**
 * 粗网格寻路图。障碍按格子中心是否落入安全半径来阻挡。
 */
public class GridPathGrid {

    /** 可走状态。 */
    private static final byte WALKABLE = 1;

    /** 阻挡状态。 */

    private static final byte BLOCKED = 0;

    /** 地图数据。 */
    private final MapData mapData;

    /** 行走网格。 */
    private final byte[] walkGrid;

    /** 障碍物列表。 */
    private final List<PathObstacle> obstacles;

    /** 障碍物索引。 */
    private final PathObstacleIndex obstacleIndex;

    /** 阻挡格子列表。 */
    private final List<PathGridPoint> blockedTiles = new ArrayList<>();

    /** 阻挡物所有者列表。 */
    private final PathObstacle[] blockOwners;

    /**
     * 构造函数。
     *
     * @param mapData
     *            地图数据
     * @param obstacles
     *            障碍物列表
     * @param obstacleIndex
     *            障碍物索引
     */
    public GridPathGrid(MapData mapData, List<PathObstacle> obstacles, PathObstacleIndex obstacleIndex) {
        this.mapData = mapData;
        this.walkGrid = new byte[mapData.getWidth() * mapData.getHeight()];
        this.blockOwners = new PathObstacle[walkGrid.length];
        this.obstacles = obstacles;
        this.obstacleIndex = obstacleIndex;
        fillBaseWalkGrid();
        applyObstacles();
    }

    public int getWidth() {
        return mapData.getWidth();
    }

    public int getHeight() {
        return mapData.getHeight();
    }

    public List<PathObstacle> getObstacles() {
        return obstacles;
    }

    public PathObstacleIndex getObstacleIndex() {
        return obstacleIndex;
    }

    public List<PathGridPoint> getBlockedTiles() {
        return blockedTiles;
    }

    public boolean isWalkable(int col, int row) {
        return isInBounds(col, row) && walkGrid[toIndex(col, row)] == WALKABLE;
    }

    /**
     * 诊断格子阻挡原因。
     *
     * @param col
     *            格子 X 坐标
     * @param row
     *            格子 Z 坐标
     * @return 阻挡原因
     */
    public PathBlockReason diagnoseTile(int col, int row) {
        int worldX = col * MapRingUtil.WORLD_PIXEL_PER_CELL + MapRingUtil.WORLD_PIXEL_PER_CELL / 2;
        int worldZ = row * MapRingUtil.WORLD_PIXEL_PER_CELL + MapRingUtil.WORLD_PIXEL_PER_CELL / 2;
        if (!isInBounds(col, row)) {
            return PathBlockReason.outOfBounds("越界", worldX, worldZ);
        }
        PathObstacle owner = blockOwners[toIndex(col, row)];
        if (owner != null) {
            return PathBlockReason.obstacle("被固定节点安全半径阻挡", worldX, worldZ, col, row, owner);
        }
        if (mapData.getMap()[col][row] == 0) {
            return PathBlockReason.map("地图格不可走", worldX, worldZ, col, row);
        }
        return PathBlockReason.none();
    }

    /**
     * 诊断世界点阻挡原因。
     *
     * @param worldX
     *            世界 X 坐标
     * @param worldZ
     *            世界 Z 坐标
     * @return 阻挡原因
     */
    public PathBlockReason diagnoseWorldPoint(int worldX, int worldZ) {
        if (PathCommonUtil.isNotWorldInBounds(mapData, worldX, worldZ)) {
            return PathBlockReason.outOfBounds("点不在地图范围内", worldX, worldZ);
        }
        int col = MapRingUtil.worldToTileX(worldX, getWidth());
        int row = MapRingUtil.worldToTileZ(worldZ, getHeight());
        PathBlockReason tileReason = diagnoseTile(col, row);
        if (tileReason.isPresent()) {
            return tileReason;
        }
        PathObstacle obstacle = obstacleIndex == null ? null : obstacleIndex.findObstacleWithin(worldX, worldZ,
            PathObstacle.AVOID_RADIUS);
        if (obstacle != null) {
            return PathBlockReason.obstacle("被固定节点安全半径阻挡", worldX, worldZ, col, row, obstacle);
        }
        return PathBlockReason.none();
    }

    /**
     * 判断格子是否在地图范围内。
     *
     * @param col
     *            格子 X 坐标
     * @param row
     *            格子 Z 坐标
     * @return 是否在地图范围内
     */
    public boolean isInBounds(int col, int row) {
        return col >= 0 && row >= 0 && col < getWidth() && row < getHeight();
    }

    /**
     * 检查世界点是否可走。
     *
     * @param worldX
     *            世界 X 坐标
     * @param worldZ
     *            世界 Z 坐标
     * @return 检查结果
     */
    public CheckResult checkWorldPoint(int worldX, int worldZ) {
        if (PathCommonUtil.isNotWorldInBounds(mapData, worldX, worldZ)) {
            return CheckResult.fail("点不在地图范围内", diagnoseWorldPoint(worldX, worldZ));
        }
        int col = MapRingUtil.worldToTileX(worldX, getWidth());
        int row = MapRingUtil.worldToTileZ(worldZ, getHeight());
        if (!isWalkable(col, row)) {
            PathBlockReason reason = diagnoseTile(col, row);
            if (reason.getObstacle() != null) {
                return CheckResult.fail(reason.getObstacle().toTileHitText(), reason);
            }
            return CheckResult.fail("该点不可走，格子=(" + col + "," + row + ")", reason);
        }
        return CheckResult.ok(col, row);
    }

    /**
     * 填充基础行走网格。
     */
    private void fillBaseWalkGrid() {
        int[][] map = mapData.getMap();
        for (int col = 0; col < getWidth(); col++) {
            for (int row = 0; row < getHeight(); row++) {
                walkGrid[toIndex(col, row)] = map[col][row] == 0 ? BLOCKED : WALKABLE;
            }
        }
    }

    /**
     * 应用障碍物。
     */
    private void applyObstacles() {
        for (int col = 0; col < getWidth(); col++) {
            for (int row = 0; row < getHeight(); row++) {
                if (mapData.getMap()[col][row] == 0) {
                    continue;
                }
                PathObstacle owner = findBlockOwner(col, row);
                if (owner != null) {
                    markBlockedTile(col, row, owner);
                }
            }
        }
    }

    /**
     * 查找阻挡物所有者。
     *
     * @param col
     *            格子 X 坐标
     * @param row
     *            格子 Z 坐标
     * @return 阻挡物所有者
     */
    private PathObstacle findBlockOwner(int col, int row) {
        int avoid = PathObstacle.AVOID_RADIUS;
        int cell = MapRingUtil.WORLD_PIXEL_PER_CELL;
        int minX = col * cell;
        int maxX = minX + cell - 1;
        int minZ = row * cell;
        int maxZ = minZ + cell - 1;
        int centerX = minX + cell / 2;
        int centerZ = minZ + cell / 2;
        int nearRadius = avoid + cell;
        if (obstacleIndex != null) {
            List<PathObstacle> nearObstacles = obstacleIndex.getNearObstacles(centerX, centerZ, nearRadius);
            return findTileBlockOwner(nearObstacles, minX, maxX, minZ, maxZ, avoid);
        }
        return findTileBlockOwner(obstacles, minX, maxX, minZ, maxZ, avoid);
    }

    /**
     * 查找阻挡物所有者。
     *
     * @param nearObstacles
     *            近处障碍物列表
     * @param minX
     *            最小 X 坐标
     * @param maxX
     *            最大 X 坐标
     * @param minZ
     *            最小 Z 坐标
     * @param maxZ
     *            最大 Z 坐标
     * @param avoid
     *            避免半径
     * @return 阻挡物所有者
     */
    private PathObstacle findTileBlockOwner(List<PathObstacle> nearObstacles, int minX, int maxX, int minZ, int maxZ,
        int avoid) {
        int avoidSq = avoid * avoid;
        for (PathObstacle obstacle : nearObstacles) {
            int dx = distanceToRange(obstacle.getNode().getX(), minX, maxX);
            int dz = distanceToRange(obstacle.getNode().getZ(), minZ, maxZ);
            if (dx * dx + dz * dz <= avoidSq) {
                return obstacle;
            }
        }
        return null;
    }

    /**
     * 计算距离范围。
     *
     * @param value
     *            值
     * @param min
     *            最小值
     * @param max
     *            最大值
     * @return 距离范围
     */
    private int distanceToRange(int value, int min, int max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0;
    }

    /**
     * 标记阻挡格子。
     *
     * @param col
     *            格子 X 坐标
     * @param row
     *            格子 Z 坐标
     * @param obstacle
     *            障碍物
     */
    private void markBlockedTile(int col, int row, PathObstacle obstacle) {
        int index = toIndex(col, row);
        if (blockOwners[index] == null) {
            blockedTiles.add(new PathGridPoint(col, row));
            blockOwners[index] = obstacle;
        }
        walkGrid[index] = BLOCKED;
    }

    private int toIndex(int col, int row) {
        return row * getWidth() + col;
    }

    /**
     * 检查结果。
     */
    public static class CheckResult {

        private final boolean valid;// 是否有效

        private final String message;// 消息

        private final int col;// 格子 X 坐标

        private final int row;// 格子 Z 坐标

        private final PathBlockReason blockReason;// 阻挡原因

        private CheckResult(boolean valid, String message, int col, int row, PathBlockReason blockReason) {
            this.valid = valid;
            this.message = message;
            this.col = col;
            this.row = row;
            this.blockReason = blockReason == null ? PathBlockReason.none() : blockReason;
        }

        public static CheckResult ok(int col, int row) {
            return new CheckResult(true, "", col, row, null);
        }

        public static CheckResult fail(String message) {
            return fail(message, null);
        }

        public static CheckResult fail(String message, PathBlockReason blockReason) {
            return new CheckResult(false, message, -1, -1, blockReason);
        }

        public boolean isNotValid() {
            return !valid;
        }

        public String getMessage() {
            return message;
        }

        public int getCol() {
            return col;
        }

        public int getRow() {
            return row;
        }

        public PathBlockReason getBlockReason() {
            return blockReason;
        }
    }
}
