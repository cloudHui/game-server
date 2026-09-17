package com.gamer.data.map.path.common;

import com.gamer.data.map.grid.MapRingUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 按世界坐标格分桶的障碍索引。
 */
public class PathObstacleIndex {

    private final Map<Long, List<PathObstacle>> buckets = new HashMap<>();

    public PathObstacleIndex(List<PathObstacle> obstacles) {
        if (obstacles == null) {
            return;
        }
        for (PathObstacle obstacle : obstacles) {
            int col = obstacle.getNode().getX() / MapRingUtil.WORLD_PIXEL_PER_CELL;
            int row = obstacle.getNode().getZ() / MapRingUtil.WORLD_PIXEL_PER_CELL;
            long key = toKey(col, row);
            List<PathObstacle> bucket = buckets.computeIfAbsent(key, k -> new ArrayList<>());
            bucket.add(obstacle);
        }
    }

    /**
     * 获取附近物体阻挡列表。
     *
     * @param worldX
     *            世界X坐标
     * @param worldZ
     *            世界Z坐标
     * @param radius
     *            半径
     * @return 附近物体阻挡列表
     */
    public List<PathObstacle> getNearObstacles(int worldX, int worldZ, int radius) {
        List<PathObstacle> ret = new ArrayList<>();
        int minCol = (worldX - radius) / MapRingUtil.WORLD_PIXEL_PER_CELL;
        int maxCol = (worldX + radius) / MapRingUtil.WORLD_PIXEL_PER_CELL;
        int minRow = (worldZ - radius) / MapRingUtil.WORLD_PIXEL_PER_CELL;
        int maxRow = (worldZ + radius) / MapRingUtil.WORLD_PIXEL_PER_CELL;
        for (int col = minCol; col <= maxCol; col++) {
            for (int row = minRow; row <= maxRow; row++) {
                List<PathObstacle> bucket = buckets.get(toKey(col, row));
                if (bucket != null) {
                    ret.addAll(bucket);
                }
            }
        }
        return ret;
    }

    /**
     * 查找附近物体阻挡。
     *
     * @param worldX
     *            世界X坐标
     * @param worldZ
     *            世界Z坐标
     * @param radius
     *            半径
     * @return 附近物体阻挡
     */
    public PathObstacle findObstacleWithin(int worldX, int worldZ, int radius) {
        int minCol = (worldX - radius) / MapRingUtil.WORLD_PIXEL_PER_CELL;
        int maxCol = (worldX + radius) / MapRingUtil.WORLD_PIXEL_PER_CELL;
        int minRow = (worldZ - radius) / MapRingUtil.WORLD_PIXEL_PER_CELL;
        int maxRow = (worldZ + radius) / MapRingUtil.WORLD_PIXEL_PER_CELL;
        int radiusSq = radius * radius;
        for (int col = minCol; col <= maxCol; col++) {
            for (int row = minRow; row <= maxRow; row++) {
                List<PathObstacle> bucket = buckets.get(toKey(col, row));
                if (bucket == null) {
                    continue;
                }
                for (PathObstacle obstacle : bucket) {
                    int dx = worldX - obstacle.getNode().getX();
                    int dz = worldZ - obstacle.getNode().getZ();
                    if (dx * dx + dz * dz <= radiusSq) {
                        return obstacle;
                    }
                }
            }
        }
        return null;
    }

    private static long toKey(int col, int row) {
        return (((long)row) << 32) ^ (col & 0xffffffffL);
    }
}
