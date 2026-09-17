package com.gamer.data.map.ui.canvas;

import java.awt.Color;
import java.util.List;

import com.gamer.data.map.grid.MapData;
import com.gamer.data.map.level.LevelNodeBean;
import com.gamer.data.map.path.common.PathGridPoint;
import com.gamer.data.map.path.common.PathBlockReason;
import com.gamer.data.map.path.common.PathFailureDetail;
import com.gamer.data.map.path.common.PathWorldPoint;

/**
 * 地图画布绘制所需的快照数据。
 */
public final class MapCanvasPaintModel {

    static final int MARKER_NONE = -999;

    /** 地图格子颜色配置 */
    static final Color[] CELL_COLORS = {new Color(200, 255, 200), new Color(200, 220, 255),
        new Color(255, 230, 200), new Color(230, 200, 255), new Color(255, 255, 200), new Color(200, 255, 255),
        new Color(255, 210, 210), new Color(210, 255, 210), new Color(210, 210, 255), new Color(255, 220, 255)};

    MapData mapData;
    List<LevelNodeBean> levelNodes;
    int markerWorldX = MARKER_NONE;
    int markerWorldZ = MARKER_NONE;
    int distanceMarker1WorldX = MARKER_NONE;
    int distanceMarker1WorldZ = MARKER_NONE;
    int distanceMarker2WorldX = MARKER_NONE;
    int distanceMarker2WorldZ = MARKER_NONE;
    LevelNodeBean selectedLevelNode;
    List<PathGridPoint> pathTiles;
    /** 对比路径格子，通常用于 WorldServer 模拟结果 */
    List<PathGridPoint> comparePathTiles;
    List<PathGridPoint> searchedTiles;
    List<PathWorldPoint> worldPathPoints;
    /** 对比路径世界坐标点，通常用于 WorldServer 模拟结果 */
    List<PathWorldPoint> compareWorldPathPoints;
    /** 已搜索的世界坐标点（细坐标 A*，属搜索层） */
    List<PathWorldPoint> searchedWorldPoints;
    List<PathGridPoint> blockedTiles;
    PathGridPoint pathStartPoint;
    PathGridPoint pathEndPoint;
    PathWorldPoint pathStartWorldPoint;
    PathWorldPoint pathEndWorldPoint;
    PathBlockReason pathBlockReason = PathBlockReason.none();
    PathFailureDetail pathFailureDetail = PathFailureDetail.none();

    /** 是否绘制搜索展开层 */
    boolean showSearch = true;
    /** 是否绘制 Tool 路径层 */
    boolean showToolPath = true;
    /** 是否绘制 WorldServer 路径层 */
    boolean showWorldPath = true;
}
