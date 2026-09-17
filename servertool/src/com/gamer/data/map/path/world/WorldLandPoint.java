package com.gamer.data.map.path.world;

/**
 * WorldServer land grid point.
 */
public final class WorldLandPoint {

    /** 格子 X 坐标。 */
    private final int landX;

    /** 格子 Z 坐标。 */
    private final int landY;

    public WorldLandPoint(int landX, int landY) {
        this.landX = landX;
        this.landY = landY;
    }

    public int getLandX() {
        return landX;
    }

    public int getLandY() {
        return landY;
    }
}
