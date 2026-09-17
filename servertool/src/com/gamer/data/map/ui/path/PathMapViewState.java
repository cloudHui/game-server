package com.gamer.data.map.ui.path;

import java.util.List;

import com.gamer.data.map.grid.MapData;
import com.gamer.data.map.level.LevelNodeBean;
import com.gamer.data.map.path.display.CoordinateAdapter;
import com.gamer.data.map.path.display.WorldMapPreviewAdapter;
import com.gamer.data.map.path.world.WorldMapData;
import com.gamer.data.map.path.world.WorldMapDisplayMode;
import com.gamer.data.map.ui.MapViewerSession;

/**
 * 寻路调试地图视图状态，明确区分 Tool 源地图与当前画布显示地图。
 */
public final class PathMapViewState {

    /** 明确加载的 Tool 源地图。 */
    private MapData toolMapData;

    /** 当前画布显示地图。 */
    private MapData displayMapData;

    /** 当前画布显示方向。 */
    private WorldMapDisplayMode displayMode = WorldMapDisplayMode.TOOL_VIEW;

    /** World 原始方向预览图，World 地图重新加载时失效。 */
    private MapData worldRawPreviewMapData;

    public void setToolMapData(MapData mapData) {
        if (mapData != null) {
            toolMapData = mapData;
        }
    }

    public void rememberToolMapData(MapData currentMapData) {
        if (!isWorldViewActive() && currentMapData != null) {
            toolMapData = currentMapData;
        }
    }

    public void clearWorldPreview() {
        worldRawPreviewMapData = null;
    }

    public MapData getToolMapData() {
        return toolMapData;
    }

    public MapData getToolSourceMapData(MapData fallbackMapData) {
        return toolMapData != null ? toolMapData : fallbackMapData;
    }

    public boolean isWorldViewActive() {
        return displayMode == WorldMapDisplayMode.WORLD_RAW;
    }

    public WorldMapDisplayMode getDisplayMode() {
        return displayMode;
    }

    public MapData resolveDisplayMapData(WorldMapDisplayMode mode, WorldMapData worldMapData, MapData fallbackMapData) {
        displayMode = mode == null ? WorldMapDisplayMode.TOOL_VIEW : mode;
        if (displayMode == WorldMapDisplayMode.WORLD_RAW) {
            displayMapData = resolveWorldRawPreviewMapData(worldMapData);
        } else {
            displayMapData = getToolSourceMapData(fallbackMapData);
        }
        return displayMapData;
    }

    public void applyToSession(MapViewerSession session, List<LevelNodeBean> levelNodes) {
        if (session == null || session.canvas == null || displayMapData == null) {
            return;
        }
        session.currentMapData = displayMapData;
        session.canvas.setMapData(displayMapData);
        session.canvas.setLevelNodes(CoordinateAdapter.toDisplayLevelNodes(levelNodes, displayMode,
            displayMapData.getHeight()));
    }

    public void restoreToolMapView(MapViewerSession session, List<LevelNodeBean> levelNodes) {
        if (session == null || session.canvas == null || toolMapData == null) {
            return;
        }
        displayMode = WorldMapDisplayMode.TOOL_VIEW;
        displayMapData = toolMapData;
        session.currentMapData = toolMapData;
        session.canvas.setMapData(toolMapData);
        session.canvas.setLevelNodes(levelNodes);
    }

    private MapData resolveWorldRawPreviewMapData(WorldMapData worldMapData) {
        if (worldMapData == null) {
            return null;
        }
        if (worldRawPreviewMapData == null) {
            worldRawPreviewMapData = WorldMapPreviewAdapter.toMapData(worldMapData, WorldMapDisplayMode.WORLD_RAW);
        }
        return worldRawPreviewMapData;
    }
}
