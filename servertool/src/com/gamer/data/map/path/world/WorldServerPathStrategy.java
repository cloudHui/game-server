package com.gamer.data.map.path.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;

/**
 * WorldServer 六边形 A* 模拟，寻路规则对齐 worldserver.map.astar.AStarPathFinder。
 */
public final class WorldServerPathStrategy {

    /** 最大展开点数，与 WorldServer AStarPathFinder 保持一致。 */
    private static final int MAX_EXPAND = 10000;

    /** odd-r 六边形偶数行邻居。 */
    private static final int[][] EVEN_ROW_OFFSETS = {{1, 0}, {-1, 0}, {0, -1}, {-1, -1}, {0, 1}, {-1, 1}};

    /** odd-r 六边形奇数行邻居。 */
    private static final int[][] ODD_ROW_OFFSETS = {{1, 0}, {-1, 0}, {1, -1}, {0, -1}, {1, 1}, {0, 1}};

    /**
     * 按 WorldServer 六边形格语义寻路。
     *
     * @param request
     *            World 寻路请求
     * @return 路径结果和统计信息
     */
    public WorldPathSearchResult findPath(WorldPathRequest request) {
        if (request == null || request.getMapData() == null) {
            return fail("WorldServer地图未加载", Collections.emptyList(), 0L, 0);
        }
        WorldMapData mapData = request.getMapData();
        long estimatedMemoryBytes = estimateSearchMemoryBytes(mapData.getWidth(), mapData.getHeight());
        int startX = request.getStartLandX();
        int startY = request.getStartLandY();
        int targetX = request.getTargetLandX();
        int targetY = request.getTargetLandY();
        if (!mapData.isWalkable(startX, startY)) {
            return fail("WorldServer起点不可走", Collections.emptyList(), estimatedMemoryBytes, 0);
        }
        String message = "成功";
        if (!mapData.isWalkable(targetX, targetY)) {
            WorldLandPoint near = findNearestWalkable(mapData, targetX, targetY);
            if (near == null) {
                return fail("WorldServer目标不可走，且附近无可走格", Collections.emptyList(),
                    estimatedMemoryBytes, 0);
            }
            targetX = near.getLandX();
            targetY = near.getLandY();
            message = "目标不可走，已就近到(" + targetX + "," + targetY + ")";
        }
        if (startX == targetX && startY == targetY) {
            List<WorldLandPoint> single = new ArrayList<>();
            single.add(new WorldLandPoint(startX, startY));
            return new WorldPathSearchResult(true, message, single, Collections.emptyList(),
                estimatedMemoryBytes, 0);
        }
        return doFindPath(mapData, startX, startY, targetX, targetY, request.isRecordSearch(), message,
            estimatedMemoryBytes);
    }

    /**
     * 执行WorldServer寻路。
     *
     * @param mapData
     *            WorldServer地图数据
     * @param startX
     *            起点大陆X坐标
     * @param startY
     *            起点大陆Y坐标
     * @param targetX
     *            终点大陆X坐标
     * @param targetY
     *            终点大陆Y坐标
     * @param recordSearch
     *            是否记录展开的网格用于调试显示
     * @param successMessage
     *            成功消息
     * @param estimatedMemoryBytes
     *            算法搜索结构估算内存，单位字节
     * @return 路径结果和统计信息
     */
    private WorldPathSearchResult doFindPath(WorldMapData mapData, int startX, int startY, int targetX, int targetY,
        boolean recordSearch, String successMessage, long estimatedMemoryBytes) {
        int width = mapData.getWidth();
        int height = mapData.getHeight();
        boolean[][] closed = new boolean[width][height];
        int[][] gCost = new int[width][height];
        int[][] parentX = new int[width][height];
        int[][] parentY = new int[width][height];
        initSearchArrays(width, height, gCost, parentX, parentY);
        PriorityQueue<int[]> open = new PriorityQueue<>(64, (a, b) -> {
            if (a[2] == b[2]) {
                return 0;
            }
            return a[2] < b[2] ? -1 : 1;
        });
        List<WorldLandPoint> searched = recordSearch ? new ArrayList<>()
            : Collections.emptyList();
        gCost[startX][startY] = 0;
        open.add(new int[] {startX, startY, heuristic(startX, startY, targetX, targetY)});
        int expand = 0;
        int bestX = startX;
        int bestY = startY;
        int bestH = heuristic(startX, startY, targetX, targetY);
        // 执行A*寻路
        while (!open.isEmpty()) {
            int[] current = open.poll();
            int currentX = current[0];
            int currentY = current[1];
            if (closed[currentX][currentY]) {
                continue;
            }
            closed[currentX][currentY] = true;
            int currentH = heuristic(currentX, currentY, targetX, targetY);
            if (currentH < bestH) {
                bestH = currentH;
                bestX = currentX;
                bestY = currentY;
            }
            if (recordSearch) {
                searched.add(new WorldLandPoint(currentX, currentY));
            }
            if (currentX == targetX && currentY == targetY) {
                List<WorldLandPoint> path = buildPath(parentX, parentY, targetX, targetY);
                return new WorldPathSearchResult(true, successMessage, path, searched, estimatedMemoryBytes, expand);
            }
            if (++expand > MAX_EXPAND) {
                return fail("WorldServer超过最大展开数: " + MAX_EXPAND, searched, estimatedMemoryBytes, expand,
                    new WorldLandPoint(bestX, bestY), findBlockingLands(mapData, bestX, bestY, targetX, targetY));
            }
            expandNeighbors(mapData, targetX, targetY, closed, gCost, parentX, parentY, open, currentX, currentY);
        }
        return fail("WorldServer无路径", searched, estimatedMemoryBytes, expand, new WorldLandPoint(bestX, bestY),
            findBlockingLands(mapData, bestX, bestY, targetX, targetY));
    }

    private static List<WorldLandPoint> findBlockingLands(WorldMapData mapData, int x, int y, int targetX,
        int targetY) {
        List<WorldLandPoint> blockers = new ArrayList<>();
        int currentH = heuristic(x, y, targetX, targetY);
        int[][] offsets = y % 2 == 0 ? EVEN_ROW_OFFSETS : ODD_ROW_OFFSETS;
        for (int[] offset : offsets) {
            int nextX = x + offset[0];
            int nextY = y + offset[1];
            if (heuristic(nextX, nextY, targetX, targetY) < currentH
                && (!mapData.isInBounds(nextX, nextY) || !mapData.isWalkable(nextX, nextY))) {
                blockers.add(new WorldLandPoint(nextX, nextY));
            }
        }
        return blockers;
    }

    /**
     * 初始化搜索数组。
     *
     * @param width
     *            WorldServer地图宽度
     * @param height
     *            WorldServer地图高度
     * @param gCost
     *            代价数组
     * @param parentX
     *            父节点X坐标数组
     * @param parentY
     *            父节点Y坐标数组
     */
    private static void initSearchArrays(int width, int height, int[][] gCost, int[][] parentX, int[][] parentY) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                gCost[x][y] = Integer.MAX_VALUE;
                parentX[x][y] = -1;
                parentY[x][y] = -1;
            }
        }
    }

    /**
     * 扩展邻居节点。
     *
     * @param mapData
     *            WorldServer地图数据
     * @param targetX
     *            终点大陆X坐标
     * @param targetY
     *            终点大陆Y坐标
     * @param closed
     *            关闭列表
     * @param gCost
     *            代价数组
     * @param parentX
     *            父节点X坐标数组
     * @param parentY
     *            父节点Y坐标数组
     * @param open
     *            开放列表
     * @param currentX
     *            当前节点X坐标
     * @param currentY
     *            当前节点Y坐标
     */
    private static void expandNeighbors(WorldMapData mapData, int targetX, int targetY, boolean[][] closed,
        int[][] gCost, int[][] parentX, int[][] parentY, PriorityQueue<int[]> open, int currentX, int currentY) {
        int[][] offsets = currentY % 2 == 0 ? EVEN_ROW_OFFSETS : ODD_ROW_OFFSETS;
        int currentG = gCost[currentX][currentY];
        for (int[] offset : offsets) {
            int nextX = currentX + offset[0];
            int nextY = currentY + offset[1];
            if (!mapData.isInBounds(nextX, nextY)) {
                continue;
            }
            if (closed[nextX][nextY] || !mapData.isWalkable(nextX, nextY)) {
                continue;
            }
            int nextG = currentG + 1;
            if (nextG >= gCost[nextX][nextY]) {
                continue;
            }
            gCost[nextX][nextY] = nextG;
            parentX[nextX][nextY] = currentX;
            parentY[nextX][nextY] = currentY;
            open.add(new int[] {nextX, nextY, nextG + heuristic(nextX, nextY, targetX, targetY)});
        }
    }

    /**
     * 启发式函数。
     *
     * @param x
     *            当前节点X坐标
     * @param y
     *            当前节点Y坐标
     * @param targetX
     *            终点大陆X坐标
     * @param targetY
     *            终点大陆Y坐标
     * @return 启发式值
     */
    private static int heuristic(int x, int y, int targetX, int targetY) {
        return WorldMapData.hexDistance(x, y, targetX, targetY);
    }

    /**
     * 构建路径。
     *
     * @param parentX
     *            父节点X坐标数组
     * @param parentY
     *            父节点Y坐标数组
     * @param endX
     *            终点X坐标
     * @param endY
     *            终点Y坐标
     * @return 路径
     */
    private static List<WorldLandPoint> buildPath(int[][] parentX, int[][] parentY, int endX, int endY) {
        LinkedList<WorldLandPoint> path = new LinkedList<>();
        int currentX = endX;
        int currentY = endY;
        while (currentX != -1) {
            path.addFirst(new WorldLandPoint(currentX, currentY));
            int previousX = parentX[currentX][currentY];
            int previousY = parentY[currentX][currentY];
            currentX = previousX;
            currentY = previousY;
        }
        return new ArrayList<>(path);
    }

    /**
     * 寻找最近的可走格。
     *
     * @param mapData
     *            WorldServer地图数据
     * @param landX
     *            大陆X坐标
     * @param landY
     *            大陆Y坐标
     * @return 最近的可走格
     */
    private static WorldLandPoint findNearestWalkable(WorldMapData mapData, int landX, int landY) {
        if (!mapData.isInBounds(landX, landY)) {
            return null;
        }
        boolean[][] visited = new boolean[mapData.getWidth()][mapData.getHeight()];
        Queue<WorldLandPoint> queue = new LinkedList<>();
        queue.add(new WorldLandPoint(landX, landY));
        visited[landX][landY] = true;
        int expand = 0;
        int maxExpand = mapData.getWidth() * mapData.getHeight();
        while (!queue.isEmpty()) {
            WorldLandPoint point = queue.poll();
            if (mapData.isWalkable(point.getLandX(), point.getLandY())) {
                return point;
            }
            if (++expand > maxExpand) {
                return null;
            }
            addNeighbors(mapData, visited, queue, point.getLandX(), point.getLandY());
        }
        return null;
    }

    /**
     * 添加邻居节点。
     *
     * @param mapData
     *            WorldServer地图数据
     * @param visited
     *            访问标记数组
     * @param queue
     *            队列
     * @param landX
     *            大陆X坐标
     * @param landY
     *            大陆Y坐标
     */
    private static void addNeighbors(WorldMapData mapData, boolean[][] visited, Queue<WorldLandPoint> queue, int landX,
        int landY) {
        int[][] offsets = landY % 2 == 0 ? EVEN_ROW_OFFSETS : ODD_ROW_OFFSETS;
        for (int[] offset : offsets) {
            int nextX = landX + offset[0];
            int nextY = landY + offset[1];
            if (!mapData.isInBounds(nextX, nextY) || visited[nextX][nextY]) {
                continue;
            }
            visited[nextX][nextY] = true;
            queue.add(new WorldLandPoint(nextX, nextY));
        }
    }

    /**
     * 失败结果。
     *
     * @param message
     *            消息
     * @param searched
     *            展开的网格
     * @param estimatedMemoryBytes
     *            算法搜索结构估算内存，单位字节
     * @param expandedCount
     *            展开节点数量
     * @return 路径结果和统计信息
     */
    private static WorldPathSearchResult fail(String message, List<WorldLandPoint> searched, long estimatedMemoryBytes,
        int expandedCount) {
        return new WorldPathSearchResult(false, message, Collections.emptyList(), searched,
            estimatedMemoryBytes, expandedCount);
    }

    private static WorldPathSearchResult fail(String message, List<WorldLandPoint> searched, long estimatedMemoryBytes,
        int expandedCount, WorldLandPoint stuckLand, List<WorldLandPoint> blockingLands) {
        return new WorldPathSearchResult(false, message, Collections.emptyList(), searched, estimatedMemoryBytes,
            expandedCount, stuckLand, blockingLands);
    }

    /**
     * 估算搜索内存。
     *
     * @param width
     *            WorldServer地图宽度
     * @param height
     *            WorldServer地图高度
     * @return 估算内存，单位字节
     */
    private static long estimateSearchMemoryBytes(int width, int height) {
        long cells = (long)width * (long)height;
        return cells * 13L;
    }
}
