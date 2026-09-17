package com.gamer.data.map.path.display;

import com.gamer.data.map.grid.MapData;
import com.gamer.data.map.path.world.WorldMapData;
import com.gamer.data.map.path.world.WorldMapDisplayMode;

/**
 * 将 WorldServer 地图数据转换为现有方格画布模型。
 */
public final class WorldMapPreviewAdapter {

    private WorldMapPreviewAdapter() {}

    /**
     * @param worldMapData
     *            WorldServer 地图数据
     * @param displayMode
     *            画布显示方向
     * @return 画布地图数据，1 表示可走，0 表示阻挡
     */
    public static MapData toMapData(WorldMapData worldMapData, WorldMapDisplayMode displayMode) {
        if (worldMapData == null) {
            return null;
        }
        WorldMapDisplayMode actualMode = PathCoordinateProjector.normalize(displayMode);
        MapData mapData = new MapData();
        int width = worldMapData.getWidth();
        int height = worldMapData.getHeight();
        int[][] map = new int[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int displayY = PathCoordinateProjector.toDisplayY(y, height, actualMode);
                map[x][displayY] = worldMapData.isWalkable(x, y) ? 1 : 0;
            }
        }
        mapData.setWidth(width);
        mapData.setHeight(height);
        mapData.setMap(map);
        return mapData;
    }
}
