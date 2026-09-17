package com.gamer.data.map.path.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WorldServer 模拟结果和算法统计。
 */
public final class WorldPathSearchResult {

    /** 是否寻路成功。 */
    private final boolean success;

    /** 结果说明。 */
    private final String message;

    /** UI 转换前的 land 格路径。 */
    private final List<WorldLandPoint> landPath;

    /** UI 转换前的 land 格搜索展开点。 */
    private final List<WorldLandPoint> searchedLands;

    private final WorldLandPoint stuckLand;

    private final List<WorldLandPoint> blockingLands;

    /** 搜索状态估算内存，单位字节。 */
    private final long estimatedMemoryBytes;

    /** 展开节点数量。 */
    private final int expandedCount;

    /**
     * @param success
     *            是否寻路成功
     * @param message
     *            结果说明
     * @param landPath
     *            land 格路径
     * @param searchedLands
     *            land 格搜索展开点
     * @param estimatedMemoryBytes
     *            搜索状态估算内存，单位字节
     * @param expandedCount
     *            展开节点数量
     */
    public WorldPathSearchResult(boolean success, String message, List<WorldLandPoint> landPath,
        List<WorldLandPoint> searchedLands, long estimatedMemoryBytes, int expandedCount) {
        this(success, message, landPath, searchedLands, estimatedMemoryBytes, expandedCount, null,
            Collections.emptyList());
    }

    public WorldPathSearchResult(boolean success, String message, List<WorldLandPoint> landPath,
        List<WorldLandPoint> searchedLands, long estimatedMemoryBytes, int expandedCount, WorldLandPoint stuckLand,
        List<WorldLandPoint> blockingLands) {
        this.success = success;
        this.message = message;
        this.landPath = landPath;
        this.searchedLands = searchedLands;
        this.estimatedMemoryBytes = estimatedMemoryBytes;
        this.expandedCount = expandedCount;
        this.stuckLand = stuckLand;
        this.blockingLands = blockingLands == null ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(blockingLands));
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public List<WorldLandPoint> getLandPath() {
        return landPath;
    }

    public List<WorldLandPoint> getSearchedLands() {
        return searchedLands;
    }

    public long getEstimatedMemoryBytes() {
        return estimatedMemoryBytes;
    }

    public int getExpandedCount() {
        return expandedCount;
    }

    public WorldLandPoint getStuckLand() {
        return stuckLand;
    }

    public List<WorldLandPoint> getBlockingLands() {
        return blockingLands;
    }
}
