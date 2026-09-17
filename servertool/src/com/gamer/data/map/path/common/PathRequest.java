package com.gamer.data.map.path.common;

import com.gamer.data.map.grid.MapData;
import com.gamer.data.map.level.LevelNodeBean;

import java.util.Collections;
import java.util.List;

/**
 * 寻路请求。
 */
public class PathRequest {

    private final MapData mapData;//地图数据

    private final List<PathObstacle> obstacles;//物体阻挡列表

    private final PathObstacleIndex obstacleIndex;//物体阻挡索引

    private final int startWorldX;//起点世界X坐标

    private final int startWorldZ;//起点世界Z坐标

    private final int endWorldX;//终点世界X坐标

    private final int endWorldZ;//终点世界Z坐标

    private final boolean recordSearch;//是否记录搜索展开点

    private final boolean allowCutCorner;//是否允许斜向切角（穿角）

    /**
     * 创建寻路请求，并从关卡节点构建障碍索引。
     */
    public PathRequest(MapData mapData, List<LevelNodeBean> levelNodes, int startWorldX, int startWorldZ, int endWorldX,
        int endWorldZ, boolean recordSearch) {
        this(mapData, levelNodes, startWorldX, startWorldZ, endWorldX, endWorldZ, recordSearch, false);
    }

    /**
     * 创建寻路请求，并从关卡节点构建障碍索引。
     *
     * @param allowCutCorner
     *            是否允许斜向切角
     */
    public PathRequest(MapData mapData, List<LevelNodeBean> levelNodes, int startWorldX, int startWorldZ, int endWorldX,
        int endWorldZ, boolean recordSearch, boolean allowCutCorner) {
        this(mapData, levelNodes,
            PathCommonUtil.buildObstacles(levelNodes == null ? Collections.emptyList() : levelNodes), null, startWorldX,
            startWorldZ, endWorldX, endWorldZ, recordSearch, allowCutCorner);
    }

    /**
     * 创建寻路请求，并复用外部已经构建好的障碍索引。
     */
    public PathRequest(MapData mapData, List<LevelNodeBean> levelNodes, List<PathObstacle> obstacles,
        PathObstacleIndex obstacleIndex, int startWorldX, int startWorldZ, int endWorldX, int endWorldZ,
        boolean recordSearch) {
        this(mapData, levelNodes, obstacles, obstacleIndex, startWorldX, startWorldZ, endWorldX, endWorldZ,
            recordSearch, false);
    }

    /**
     * 创建寻路请求，并复用外部已经构建好的障碍索引。
     *
     * @param allowCutCorner
     *            是否允许斜向切角
     */
    public PathRequest(MapData mapData, List<LevelNodeBean> levelNodes, List<PathObstacle> obstacles,
        PathObstacleIndex obstacleIndex, int startWorldX, int startWorldZ, int endWorldX, int endWorldZ,
        boolean recordSearch, boolean allowCutCorner) {
        this.mapData = mapData;
        List<LevelNodeBean> levelNodes1 = levelNodes == null ? Collections.emptyList() : levelNodes;
        this.obstacles = obstacles == null ? PathCommonUtil.buildObstacles(levelNodes1) : obstacles;
        this.obstacleIndex = obstacleIndex == null ? new PathObstacleIndex(this.obstacles) : obstacleIndex;
        this.startWorldX = startWorldX;
        this.startWorldZ = startWorldZ;
        this.endWorldX = endWorldX;
        this.endWorldZ = endWorldZ;
        this.recordSearch = recordSearch;
        this.allowCutCorner = allowCutCorner;
    }

    public MapData getMapData() {
        return mapData;
    }

    public List<PathObstacle> getObstacles() {
        return obstacles;
    }

    public PathObstacleIndex getObstacleIndex() {
        return obstacleIndex;
    }

    public int getStartWorldX() {
        return startWorldX;
    }

    public int getStartWorldZ() {
        return startWorldZ;
    }

    public int getEndWorldX() {
        return endWorldX;
    }

    public int getEndWorldZ() {
        return endWorldZ;
    }

    public boolean isRecordSearch() {
        return recordSearch;
    }

    public boolean isAllowCutCorner() {
        return allowCutCorner;
    }
}
