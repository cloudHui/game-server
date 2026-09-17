package com.gamer.data.map.path.tool.common;

/**
 * A* 通用方向和距离辅助类。
 */
public final class PathAStarUtil {

    /** 方向 X 数组。 */
    public static final int[] DIR_X = {1, -1, 0, 0, 1, 1, -1, -1};

    /** 方向 Z 数组。 */
    public static final int[] DIR_Z = {0, 0, 1, -1, 1, -1, 1, -1};
    /** 方向成本数组。 */

    public static final int[] DIR_COST = {10, 10, 10, 10, 14, 14, 14, 14};

    private PathAStarUtil() {}

    /**
     * 启发式函数。
     *
     * @param x
     *            起点 X 坐标
     * @param z
     *            起点 Z 坐标
     * @param endX
     *            终点 X 坐标
     * @param endZ
     *            终点 Z 坐标
     * @return 启发式函数
     */
    public static int heuristic(int x, int z, int endX, int endZ) {
        int dx = Math.abs(x - endX);
        int dz = Math.abs(z - endZ);
        int min = Math.min(dx, dz);
        int max = Math.max(dx, dz);
        return min * 14 + (max - min) * 10;
    }
}
