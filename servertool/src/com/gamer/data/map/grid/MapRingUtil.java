package com.gamer.data.map.grid;

/**
 * 格子/世界坐标换算。地图坐标与世界坐标均以左下角为原点 (0,0)。
 * 世界坐标 1 格 = 10 像素：格子 (col,row) 对应世界 (col*10, row*10)。
 */
public final class MapRingUtil {

    /** 世界坐标每格像素数，与 MapInfo.PIXEL_SIZE 一致 */
    public static final int WORLD_PIXEL_PER_CELL = 10;

    public static final String MAP = "Common/Tools/Bin/map/level";

    private MapRingUtil() {}

    /**
     * 世界坐标转格子坐标。世界 1 格 = 10 像素。
     */
    public static int worldToTileX(int worldX, int width) {
        int t = worldX / WORLD_PIXEL_PER_CELL;
        return t < 0 ? 0 : (t >= width ? width - 1 : t);
    }

    public static int worldToTileZ(int worldZ, int width) {
        return worldToTileX(worldZ, width);
    }
}
