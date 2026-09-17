package com.gamer.data.map.path.common;

import com.gamer.data.map.grid.MapRingUtil;

/**
 * 寻路格子点。
 */
public class PathGridPoint {

    private final int col;

    private final int row;

    public PathGridPoint(int col, int row) {
        this.col = col;
        this.row = row;
    }

    public int getCol() {
        return col;
    }

    public int getRow() {
        return row;
    }

    public int getWorldX() {
        return col * MapRingUtil.WORLD_PIXEL_PER_CELL + MapRingUtil.WORLD_PIXEL_PER_CELL / 2;
    }

    public int getWorldZ() {
        return row * MapRingUtil.WORLD_PIXEL_PER_CELL + MapRingUtil.WORLD_PIXEL_PER_CELL / 2;
    }
}
