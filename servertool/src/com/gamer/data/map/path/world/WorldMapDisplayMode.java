package com.gamer.data.map.path.world;

import com.gamer.data.map.path.display.PathCoordinateProjector;

/**
 * servertool 显示 WorldServer 地图时使用的方向。
 */
public enum WorldMapDisplayMode {

    TOOL_VIEW("跟随Tool显示"),
    WORLD_RAW("World原始方向");

    private final String label;

    WorldMapDisplayMode(String label) {
        this.label = label;
    }

    /**
     * 将当前显示行号转换回 WorldServer 原始 land 行号。
     *
     * @param displayY
     *            当前画布视角下的行号
     * @param height
     *            地图高度，单位为格
     * @return WorldServer 原始 land 行号
     */
    public int toWorldY(int displayY, int height) {
        return PathCoordinateProjector.toSourceY(displayY, height, this);
    }

    @Override
    public String toString() {
        return label;
    }
}
