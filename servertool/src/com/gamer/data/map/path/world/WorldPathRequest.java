package com.gamer.data.map.path.world;

/**
 * WorldServer寻路请求。
 */
public final class WorldPathRequest {

    /** 真实WorldServer地图数据。 */
    private final WorldMapData mapData;

    /** 起点大陆X坐标。 */
    private final int startLandX;

    /** 起点大陆Y坐标。 */
    private final int startLandY;

    /** 终点大陆X坐标。 */
    private final int targetLandX;

    /** 终点大陆Y坐标。 */
    private final int targetLandY;

    /** 是否记录展开的网格用于调试显示。 */
    private final boolean recordSearch;

    /**
     * @param mapData
     *            真实WorldServer地图数据
     * @param startLandX
     *            起点大陆X坐标
     * @param startLandY
     *            起点大陆Y坐标
     * @param targetLandX
     *            终点大陆X坐标
     * @param targetLandY
     *            终点大陆Y坐标
     * @param recordSearch
     *            是否记录展开的网格用于调试显示
     */
    public WorldPathRequest(WorldMapData mapData, int startLandX, int startLandY, int targetLandX, int targetLandY,
        boolean recordSearch) {
        this.mapData = mapData;
        this.startLandX = startLandX;
        this.startLandY = startLandY;
        this.targetLandX = targetLandX;
        this.targetLandY = targetLandY;
        this.recordSearch = recordSearch;
    }

    public WorldMapData getMapData() {
        return mapData;
    }

    public int getStartLandX() {
        return startLandX;
    }

    public int getStartLandY() {
        return startLandY;
    }

    public int getTargetLandX() {
        return targetLandX;
    }

    public int getTargetLandY() {
        return targetLandY;
    }

    public boolean isRecordSearch() {
        return recordSearch;
    }
}
