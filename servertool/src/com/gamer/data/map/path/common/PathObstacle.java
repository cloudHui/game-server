package com.gamer.data.map.path.common;

import com.gamer.data.map.level.LevelNodeBean;
import com.gamer.data.map.level.NodeType;

/**
 * 寻路固定障碍点。
 */
public class PathObstacle {

    /** 物体半径 */
    public static final int OBJECT_RADIUS = 1;

    /** 单位半径 */
    public static final int UNIT_RADIUS = 1;

    /** 避免半径 */
    public static final int AVOID_RADIUS = OBJECT_RADIUS + UNIT_RADIUS;

    private final LevelNodeBean node;

    private final String typeName;

    public PathObstacle(LevelNodeBean node) {
        this.node = node;
        this.typeName = NodeType.getTypeName(node.getType());
    }

    public LevelNodeBean getNode() {
        return node;
    }

    public String toTileHitText() {
        return "该点所在格被" + typeName + " 阻挡 DataId=" + node.getDataId() + " 坐标=(" + node.getX() + ","
            + node.getZ() + ")";
    }
}
