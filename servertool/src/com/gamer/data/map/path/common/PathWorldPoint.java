package com.gamer.data.map.path.common;

/**
 * 寻路世界坐标点。
 */
public class PathWorldPoint {

    private final int worldX;

    private final int worldZ;

    public PathWorldPoint(int worldX, int worldZ) {
        this.worldX = worldX;
        this.worldZ = worldZ;
    }

    public int getWorldX() {
        return worldX;
    }

    public int getWorldZ() {
        return worldZ;
    }
}
