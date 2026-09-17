package com.gamer.data.map.path.tool.grid;

import java.util.List;

import com.gamer.data.map.grid.MapData;
import com.gamer.data.map.level.LevelNodeBean;
import com.gamer.data.map.path.common.PathRequest;

/**
 * 携带已构建粗网格的 Tool 寻路请求，避免 Grid 策略重复构建网格。
 */
public final class GridPathRequest extends PathRequest {

    /** UI 校验阶段已构建好的粗网格。 */
    private final GridPathGrid grid;

    /**
     * @param allowCutCorner
     *            是否允许斜向切角
     */
    public GridPathRequest(MapData mapData, List<LevelNodeBean> levelNodes, GridPathGrid grid, int startWorldX,
        int startWorldZ, int endWorldX, int endWorldZ, boolean recordSearch, boolean allowCutCorner) {
        super(mapData, levelNodes, grid == null ? null : grid.getObstacles(),
            grid == null ? null : grid.getObstacleIndex(), startWorldX, startWorldZ, endWorldX, endWorldZ,
            recordSearch, allowCutCorner);
        this.grid = grid;
    }

    public GridPathGrid getGrid() {
        return grid;
    }
}
