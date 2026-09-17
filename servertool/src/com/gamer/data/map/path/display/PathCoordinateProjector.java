package com.gamer.data.map.path.display;

import com.gamer.data.map.grid.MapRingUtil;
import com.gamer.data.map.path.world.WorldMapDisplayMode;

/**
 * 寻路调试的坐标投影入口，集中处理 World 原始方向与 Tool 显示方向之间的转换。
 */
public final class PathCoordinateProjector {

    private PathCoordinateProjector() {}

    /**
     * 规范化显示模式。
     *
     * @param displayMode
     *            显示模式
     * @return 规范化后的显示模式
     */
    public static WorldMapDisplayMode normalize(WorldMapDisplayMode displayMode) {
        return displayMode == null ? WorldMapDisplayMode.TOOL_VIEW : displayMode;
    }

    /**
     * 转换为显示Y坐标。
     *
     * @param sourceY
     *            源Y坐标
     * @param height
     *            高度
     * @param displayMode
     *            显示模式
     * @return 显示Y坐标
     */
    public static int toDisplayY(int sourceY, int height, WorldMapDisplayMode displayMode) {
        return toSourceY(sourceY, height, displayMode);
    }

    /**
     * 转换为源Y坐标。
     *
     * @param displayY
     *            显示Y坐标
     * @param height
     *            高度
     * @param displayMode
     *            显示模式
     * @return 源Y坐标
     */
    public static int toSourceY(int displayY, int height, WorldMapDisplayMode displayMode) {
        if (normalize(displayMode) != WorldMapDisplayMode.TOOL_VIEW) {
            return displayY;
        }
        return height - 1 - displayY;
    }

    /**
     * 转换为显示Z坐标。
     *
     * @param sourceWorldZ
     *            源世界Z坐标
     * @param height
     *            高度
     * @param displayMode
     *            显示模式
     * @return 显示Z坐标
     */
    public static int toDisplayWorldZ(int sourceWorldZ, int height, WorldMapDisplayMode displayMode) {
        if (normalize(displayMode) != WorldMapDisplayMode.TOOL_VIEW) {
            return sourceWorldZ;
        }
        return height * MapRingUtil.WORLD_PIXEL_PER_CELL - 1 - sourceWorldZ;
    }

    /**
     * 转换为源世界Z坐标。
     *
     * @param displayWorldZ
     *            显示世界Z坐标
     * @param height
     *            高度
     * @param displayMode
     *            显示模式
     * @return 源世界Z坐标
     */
    public static int toSourceWorldZ(int displayWorldZ, int height, WorldMapDisplayMode displayMode) {
        return toDisplayWorldZ(displayWorldZ, height, displayMode);
    }
}
