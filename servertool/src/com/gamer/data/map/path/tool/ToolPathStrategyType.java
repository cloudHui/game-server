package com.gamer.data.map.path.tool;

import com.gamer.data.map.path.common.PathFindingStrategy;
import com.gamer.data.map.path.tool.grid.GridPathStrategy;
import com.gamer.data.map.path.tool.hybrid.HybridPathStrategy;
import com.gamer.data.map.path.tool.point.PointPathStrategy;

/**
 * 寻路方式。
 */
public enum ToolPathStrategyType {

    GRID("粗网格", new GridPathStrategy()),
    POINT("细坐标", new PointPathStrategy()),
    HYBRID("混合校验", new HybridPathStrategy());

    private final String label;

    private final PathFindingStrategy strategy;

    ToolPathStrategyType(String label, PathFindingStrategy strategy) {
        this.label = label;
        this.strategy = strategy;
    }

    public PathFindingStrategy getStrategy() {
        return strategy;
    }

    @Override
    public String toString() {
        return label;
    }
}
