package com.gamer.data.map.path.tool.grid;

import java.util.List;

import com.gamer.data.map.grid.MapData;
import com.gamer.data.map.level.LevelNodeBean;
import com.gamer.data.map.path.common.PathCommonUtil;
import com.gamer.data.map.path.common.PathObstacle;
import com.gamer.data.map.path.common.PathObstacleIndex;

/**
 * Tool 粗网格构建入口，统一生成障碍列表、障碍索引和可走网格。
 */
public final class GridPathGridFactory {

    private GridPathGridFactory() {}

    /**
     * 根据地图和关卡节点创建粗网格。
     *
     * @param mapData
     *            Tool 地图数据
     * @param levelNodes
     *            关卡节点
     * @return 粗网格
     */
    public static GridPathGrid create(MapData mapData, List<LevelNodeBean> levelNodes) {
        List<PathObstacle> obstacles = PathCommonUtil.buildObstacles(levelNodes);
        return new GridPathGrid(mapData, obstacles, new PathObstacleIndex(obstacles));
    }
}
