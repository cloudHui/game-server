package com.gamer.data.map.ui.path;

import com.gamer.data.map.path.common.PathBlockReason;

/**
 * 寻路起终点校验结果。
 */
final class PathPointCheckResult {

    private final boolean valid;
    private final String message;
    private final int worldX;
    private final int worldZ;
    private final int col;
    private final int row;
    private final PathBlockReason blockReason;

    private PathPointCheckResult(boolean valid, String message, int worldX, int worldZ, int col, int row,
        PathBlockReason blockReason) {
        this.valid = valid;
        this.message = message == null ? "" : message;
        this.worldX = worldX;
        this.worldZ = worldZ;
        this.col = col;
        this.row = row;
        this.blockReason = blockReason == null ? PathBlockReason.none() : blockReason;
    }

    static PathPointCheckResult ok(int worldX, int worldZ, int col, int row) {
        return new PathPointCheckResult(true, "", worldX, worldZ, col, row, null);
    }

    static PathPointCheckResult fail(String message) {
        return fail(message, null);
    }

    static PathPointCheckResult fail(String message, PathBlockReason blockReason) {
        return new PathPointCheckResult(false, message, 0, 0, -1, -1, blockReason);
    }

    boolean isNotValid() {
        return !valid;
    }

    String getMessage() {
        return message;
    }

    int getWorldX() {
        return worldX;
    }

    int getWorldZ() {
        return worldZ;
    }

    int getCol() {
        return col;
    }

    int getRow() {
        return row;
    }

    PathBlockReason getBlockReason() {
        return blockReason;
    }
}
