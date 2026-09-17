package com.gamer.data.map.path.common;

import java.util.Collections;
import java.util.List;

/**
 * 寻路结果。
 */
public class PathResult {

    private final boolean success;//是否成功

    private final String message;//消息

    private final List<PathGridPoint> gridPath;//网格路径

    private final List<PathWorldPoint> worldPath;//世界路径

    private final List<PathGridPoint> searchedGrids;//搜索网格路径

    private final List<PathWorldPoint> searchedWorlds;//搜索世界路径

    private final PathBlockReason blockReason;//阻挡原因

    private final PathFailureDetail failureDetail;

    public PathResult(boolean success, String message, List<PathGridPoint> gridPath, List<PathWorldPoint> worldPath,
        List<PathGridPoint> searchedGrids, List<PathWorldPoint> searchedWorlds) {
        this(success, message, gridPath, worldPath, searchedGrids, searchedWorlds, null);
    }

    public PathResult(boolean success, String message, List<PathGridPoint> gridPath, List<PathWorldPoint> worldPath,
        List<PathGridPoint> searchedGrids, List<PathWorldPoint> searchedWorlds, PathBlockReason blockReason) {
        this(success, message, gridPath, worldPath, searchedGrids, searchedWorlds, blockReason, null);
    }

    public PathResult(boolean success, String message, List<PathGridPoint> gridPath, List<PathWorldPoint> worldPath,
        List<PathGridPoint> searchedGrids, List<PathWorldPoint> searchedWorlds, PathBlockReason blockReason,
        PathFailureDetail failureDetail) {
        this.success = success;
        this.message = message == null ? "" : message;
        this.gridPath = gridPath == null ? Collections.emptyList() : gridPath;
        this.worldPath = worldPath == null ? Collections.emptyList() : worldPath;
        this.searchedGrids = searchedGrids == null ? Collections.emptyList() : searchedGrids;
        this.searchedWorlds = searchedWorlds == null ? Collections.emptyList() : searchedWorlds;
        this.failureDetail = failureDetail == null ? PathFailureDetail.none() : failureDetail;
        this.blockReason = blockReason == null ? this.failureDetail.getPrimaryBlockReason() : blockReason;
    }

    public static PathResult fail(String message, List<PathGridPoint> searchedGrids,
        List<PathWorldPoint> searchedWorlds) {
        return new PathResult(false, message, Collections.emptyList(), Collections.emptyList(), searchedGrids,
            searchedWorlds);
    }

    public static PathResult fail(String message, List<PathGridPoint> searchedGrids, List<PathWorldPoint> searchedWorlds,
        PathBlockReason blockReason) {
        return new PathResult(false, message, Collections.emptyList(), Collections.emptyList(), searchedGrids,
            searchedWorlds, blockReason);
    }

    public static PathResult fail(String message, List<PathGridPoint> searchedGrids, List<PathWorldPoint> searchedWorlds,
        PathFailureDetail failureDetail) {
        return new PathResult(false, message, Collections.emptyList(), Collections.emptyList(), searchedGrids,
            searchedWorlds, null, failureDetail);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public List<PathGridPoint> getGridPath() {
        return gridPath;
    }

    public List<PathWorldPoint> getWorldPath() {
        return worldPath;
    }

    public List<PathGridPoint> getSearchedGrids() {
        return searchedGrids;
    }

    public List<PathWorldPoint> getSearchedWorlds() {
        return searchedWorlds;
    }

    public PathBlockReason getBlockReason() {
        return blockReason;
    }

    public PathFailureDetail getFailureDetail() {
        return failureDetail;
    }
}
