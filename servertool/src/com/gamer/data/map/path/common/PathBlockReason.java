package com.gamer.data.map.path.common;

import com.gamer.data.map.level.LevelNodeBean;

/**
 * Tool 寻路失败时的阻挡诊断信息。
 */
public final class PathBlockReason {

    public static final int TYPE_NONE = 0;//无阻挡

    public static final int TYPE_MAP = 1;//地图阻挡

    public static final int TYPE_OBSTACLE = 2;//物体阻挡

    public static final int TYPE_OUT_OF_BOUNDS = 3;//超出边界

    private final int type;//阻挡类型

    private final String message;//阻挡消息

    private final int worldX;//阻挡世界X坐标

    private final int worldZ;//阻挡世界Z坐标

    private final int col;//阻挡列

    private final int row;//阻挡行

    private final PathObstacle obstacle;//阻挡物体

    private final int radius;//阻挡半径

    private PathBlockReason(int type, String message, int worldX, int worldZ, int col, int row, PathObstacle obstacle,
        int radius) {
        this.type = type;
        this.message = message == null ? "" : message;
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.col = col;
        this.row = row;
        this.obstacle = obstacle;
        this.radius = radius;
    }

    public static PathBlockReason none() {
        return new PathBlockReason(TYPE_NONE, "", -1, -1, -1, -1, null, 0);
    }

    public static PathBlockReason map(String message, int worldX, int worldZ, int col, int row) {
        return new PathBlockReason(TYPE_MAP, message, worldX, worldZ, col, row, null, 0);
    }

    public static PathBlockReason outOfBounds(String message, int worldX, int worldZ) {
        return new PathBlockReason(TYPE_OUT_OF_BOUNDS, message, worldX, worldZ, -1, -1, null, 0);
    }

    /**
     * 创建物体阻挡原因。
     *
     * @param message
     *            阻挡消息
     * @param worldX
     *            阻挡世界X坐标
     * @param worldZ
     *            阻挡世界Z坐标
     * @param col
     *            阻挡列
     * @param row
     *            阻挡行
     * @param obstacle
     *            阻挡物体
     * @return 物体阻挡原因
     */
    public static PathBlockReason obstacle(String message, int worldX, int worldZ, int col, int row,
        PathObstacle obstacle) {
        return new PathBlockReason(TYPE_OBSTACLE, message, worldX, worldZ, col, row, obstacle,
            PathObstacle.AVOID_RADIUS);
    }

    public boolean isPresent() {
        return type != TYPE_NONE;
    }

    public int getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public int getWorldX() {
        return worldX;
    }

    public int getWorldZ() {
        return worldZ;
    }

    public int getCol() {
        return col;
    }

    public int getRow() {
        return row;
    }

    public PathObstacle getObstacle() {
        return obstacle;
    }

    public int getRadius() {
        return radius;
    }

    public String toDisplayText() {
        if (!isPresent()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(message);
        if (col >= 0 && row >= 0) {
            builder.append(" 格=(").append(col).append(',').append(row).append(')');
        }
        if (worldX >= 0 && worldZ >= 0) {
            builder.append(" 世界=(").append(worldX).append(',').append(worldZ).append(')');
        }
        if (obstacle != null) {
            LevelNodeBean node = obstacle.getNode();
            builder.append(" 节点=(").append(node.getX()).append(',').append(node.getZ()).append(')');
            builder.append(" 半径=").append(radius);
        }
        return builder.toString();
    }
}
