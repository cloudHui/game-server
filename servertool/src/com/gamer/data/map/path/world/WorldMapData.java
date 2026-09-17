package com.gamer.data.map.path.world;

/**
 * 从真实地图 txt 文件加载出的 WorldServer 地图数据。
 */
public final class WorldMapData {

    /** 地图宽度，单位为 land 格。 */
    private final int width;

    /** 地图高度，单位为 land 格。 */
    private final int height;

    /** 可走状态，索引顺序为 landX、landY。 */
    private final boolean[][] walkable;

    /** 阻挡格数量，包含文件未填充到的空格。 */
    private int blockCount;

    /**
     * @param width
     *            地图宽度，单位为 land 格
     * @param height
     *            地图高度，单位为 land 格
     */
    public WorldMapData(int width, int height) {
        this.width = width;
        this.height = height;
        this.walkable = new boolean[width][height];
        this.blockCount = width * height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getBlockCount() {
        return blockCount;
    }

    /**
     * 设置单个 land 格的可走状态。
     *
     * @param landX
     *            land X
     * @param landY
     *            land Y
     * @param canWalk
     *            是否可走
     */
    public void setWalkable(int landX, int landY, boolean canWalk) {
        if (!isInBounds(landX, landY)) {
            return;
        }
        if (walkable[landX][landY] == canWalk) {
            return;
        }
        walkable[landX][landY] = canWalk;
        blockCount += canWalk ? -1 : 1;
    }

    /**
     * @param landX
     *            land X
     * @param landY
     *            land Y
     * @return 是否在地图范围内
     */
    public boolean isInBounds(int landX, int landY) {
        return landX >= 0 && landX < width && landY >= 0 && landY < height;
    }

    /**
     * @param landX
     *            land X
     * @param landY
     *            land Y
     * @return 是否在地图范围内且可走
     */
    public boolean isWalkable(int landX, int landY) {
        return isInBounds(landX, landY) && walkable[landX][landY];
    }

    /**
     * odd-r 六边形距离，与 WorldServer WorldMapInfo.hexDistance 保持一致。
     *
     * @param ax
     *            起点 land X
     * @param ay
     *            起点 land Y
     * @param bx
     *            终点 land X
     * @param by
     *            终点 land Y
     * @return 六边形步数距离
     */
    public static int hexDistance(int ax, int ay, int bx, int by) {
        int acx = ax - (ay - (ay & 1)) / 2;
        int acy = -acx - ay;
        int bcx = bx - (by - (by & 1)) / 2;
        int bcy = -bcx - by;
        return (Math.abs(acx - bcx) + Math.abs(acy - bcy) + Math.abs(ay - by)) / 2;
    }
}
