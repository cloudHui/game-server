package com.gamer.data.map.path.tool.point;

import com.gamer.data.map.grid.MapData;
import com.gamer.data.map.grid.MapRingUtil;
import com.gamer.data.map.path.common.PathCommonUtil;
import com.gamer.data.map.path.common.PathFindingStrategy;
import com.gamer.data.map.path.common.PathFailureDetail;
import com.gamer.data.map.path.common.PathGridPoint;
import com.gamer.data.map.path.common.PathObstacle;
import com.gamer.data.map.path.common.PathObstacleIndex;
import com.gamer.data.map.path.common.PathBlockReason;
import com.gamer.data.map.path.common.PathRequest;
import com.gamer.data.map.path.common.PathResult;
import com.gamer.data.map.path.common.PathWorldPoint;
import com.gamer.data.map.path.tool.common.PathAStarUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * 世界整数坐标寻路策略。
 *
 * <p>
 * 使用「稠密原生数组 + long[] 最小堆」承载搜索态，内存约为每像素 5~6 字节，较装箱 HashMap 降低一个量级； 混合调用时可传入粗格路线，仅在其周边走廊内搜索，走廊内无解自动回退整图全搜，保证不漏路。
 * </p>
 */
public class PointPathStrategy implements PathFindingStrategy {

    /** 未到达格的 g 值哨兵。 */
    private static final int INF = Integer.MAX_VALUE / 4;

    /** 走廊半宽（世界像素）：粗格相邻中心间距约 14 像素，取更大值使相邻圆盘连续覆盖并留出绕障余量。 */
    private static final int CORRIDOR_RADIUS = 30;

    @Override
    public PathResult findPath(PathRequest request) {
        return findPath(request, null);
    }

    /**
     * 带走廊提示的寻路。
     *
     * @param request
     *            寻路请求
     * @param corridorPath
     *            粗格路线（世界坐标）；非空时先在其周边走廊内搜索，失败再整图回退；为空时直接整图搜索
     * @return 寻路结果
     */
    public PathResult findPath(PathRequest request, List<PathWorldPoint> corridorPath) {
        MapData mapData = request.getMapData();
        List<PathObstacle> obstacles = request.getObstacles();
        PathObstacleIndex obstacleIndex = request.getObstacleIndex();
        // 起终点安全校验（与固定障碍安全半径一致）
        if (!PathCommonUtil.isSafeWorldPoint(mapData, obstacles, obstacleIndex, request.getStartWorldX(),
            request.getStartWorldZ())) {
            return PathResult.fail("起点不是安全坐标", Collections.emptyList(), Collections.emptyList(),
                PathCommonUtil.diagnoseWorldPoint(mapData, obstacles, obstacleIndex, request.getStartWorldX(),
                    request.getStartWorldZ()));
        }
        if (!PathCommonUtil.isSafeWorldPoint(mapData, obstacles, obstacleIndex, request.getEndWorldX(),
            request.getEndWorldZ())) {
            return PathResult.fail("终点不是安全坐标", Collections.emptyList(), Collections.emptyList(),
                PathCommonUtil.diagnoseWorldPoint(mapData, obstacles, obstacleIndex, request.getEndWorldX(),
                    request.getEndWorldZ()));
        }
        int worldWidth = mapData.getWidth() * MapRingUtil.WORLD_PIXEL_PER_CELL;
        int worldHeight = mapData.getHeight() * MapRingUtil.WORLD_PIXEL_PER_CELL;
        SearchData data = new SearchData(worldWidth, worldHeight);
        SearchResult result;
        if (corridorPath != null && !corridorPath.isEmpty()) {
            // 走廊约束搜索
            data.markCorridor(corridorPath);
            result = search(mapData, obstacles, obstacleIndex, data, request, true);
            if (result.path.isEmpty()) {
                // 走廊内无解：清空搜索态后整图回退，保证不漏路
                data.reset();
                result = search(mapData, obstacles, obstacleIndex, data, request, false);
            }
        } else {
            result = search(mapData, obstacles, obstacleIndex, data, request, false);
        }
        if (result.path.isEmpty()) {
            return PathResult.fail("无路径", Collections.emptyList(), result.searched, result.failureDetail);
        }
        return new PathResult(true, "成功", PathCommonUtil.toGridPath(mapData, result.path), result.path,
            Collections.emptyList(), result.searched);
    }

    /**
     * 8 方向 A* 主循环。
     *
     * @param useCorridor
     *            是否仅允许进入走廊内格子
     */
    private static SearchResult search(MapData mapData, List<PathObstacle> obstacles, PathObstacleIndex obstacleIndex,
        SearchData data, PathRequest request, boolean useCorridor) {
        int startX = request.getStartWorldX();
        int startZ = request.getStartWorldZ();
        int endX = request.getEndWorldX();
        int endZ = request.getEndWorldZ();
        boolean record = request.isRecordSearch();
        int startIdx = data.index(startX, startZ);
        data.gScore[startIdx] = 0;
        int startH = PathAStarUtil.heuristic(startX, startZ, endX, endZ);
        data.open.push(pack(startH, startIdx));
        while (!data.open.isEmpty()) {
            long top = data.open.pop();
            
            int curIdx = (int)(top & 0xffffffffL);
            if (data.closed.get(curIdx)) {
                continue;
            }
            data.closed.set(curIdx);
            int curX = data.toX(curIdx);
            int curZ = data.toZ(curIdx);
            data.updateBest(curX, curZ, PathAStarUtil.heuristic(curX, curZ, endX, endZ));
            if (record) {
                data.searched.add(new PathWorldPoint(curX, curZ));
            }
            if (curX == endX && curZ == endZ) {
                return new SearchResult(buildPath(data, startIdx, curIdx), data.searched);
            }
            expandNeighbors(mapData, obstacles, obstacleIndex, data, curIdx, curX, curZ, endX, endZ, useCorridor,
                request.isAllowCutCorner());
        }
        return new SearchResult(Collections.emptyList(), data.searched,
            buildFailureDetail(mapData, obstacles, obstacleIndex, data.bestX, data.bestZ, endX, endZ,
                request.isAllowCutCorner()));
    }

    /**
     * 扩展邻居节点。
     */
    private static void expandNeighbors(MapData mapData, List<PathObstacle> obstacles, PathObstacleIndex obstacleIndex,
        SearchData data, int curIdx, int curX, int curZ, int endX, int endZ, boolean useCorridor,
        boolean allowCutCorner) {
        for (int i = 0; i < PathAStarUtil.DIR_X.length; i++) {
            int nextX = curX + PathAStarUtil.DIR_X[i];
            int nextZ = curZ + PathAStarUtil.DIR_Z[i];
            if (!data.inBounds(nextX, nextZ)) {
                continue;
            }
            int nextIdx = data.index(nextX, nextZ);
            if (useCorridor && !data.allowed.get(nextIdx)) {
                continue;
            }
            if (data.closed.get(nextIdx)) {
                continue;
            }
            if (!canMove(mapData, obstacles, obstacleIndex, curX, curZ, nextX, nextZ, PathAStarUtil.DIR_X[i],
                PathAStarUtil.DIR_Z[i], allowCutCorner)) {
                continue;
            }
            int nextG = data.gScore[curIdx] + PathAStarUtil.DIR_COST[i];
            if (nextG >= data.gScore[nextIdx]) {
                continue;
            }
            data.gScore[nextIdx] = nextG;
            data.dir[nextIdx] = (byte)i;
            int nextH = PathAStarUtil.heuristic(nextX, nextZ, endX, endZ);
            data.open.push(pack(nextG + nextH, nextIdx));
        }
    }

    /**
     * 构建失败原因。
     *
     * @param mapData
     *            地图数据
     * @param obstacles
     *            障碍物
     * @param obstacleIndex
     *            障碍物索引
     * @param x
     *            起点 X 坐标
     * @param z
     *            起点 Z 坐标
     * @param endX
     *            终点 X 坐标
     * @param endZ
     *            终点 Z 坐标
     * @return 失败原因
     */
    private static PathFailureDetail buildFailureDetail(MapData mapData, List<PathObstacle> obstacles,
        PathObstacleIndex obstacleIndex, int x, int z, int endX, int endZ, boolean allowCutCorner) {
        List<PathBlockReason> reasons = new ArrayList<>();
        int currentH = PathAStarUtil.heuristic(x, z, endX, endZ);
        for (int i = 0; i < PathAStarUtil.DIR_X.length; i++) {
            int dx = PathAStarUtil.DIR_X[i];
            int dz = PathAStarUtil.DIR_Z[i];
            int nextX = x + dx;
            int nextZ = z + dz;
            if (PathAStarUtil.heuristic(nextX, nextZ, endX, endZ) >= currentH) {
                continue;
            }
            if (!PathCommonUtil.isSafeWorldPoint(mapData, obstacles, obstacleIndex, nextX, nextZ)) {
                PathFailureDetail.addUniqueReason(reasons,
                    PathCommonUtil.diagnoseWorldPoint(mapData, obstacles, obstacleIndex, nextX, nextZ));
            } else if (!allowCutCorner && dx != 0 && dz != 0) {
                if (!PathCommonUtil.isSafeWorldPoint(mapData, obstacles, obstacleIndex, x + dx, z)) {
                    PathFailureDetail.addUniqueReason(reasons,
                        PathCommonUtil.diagnoseWorldPoint(mapData, obstacles, obstacleIndex, x + dx, z));
                }
                if (!PathCommonUtil.isSafeWorldPoint(mapData, obstacles, obstacleIndex, x, z + dz)) {
                    PathFailureDetail.addUniqueReason(reasons,
                        PathCommonUtil.diagnoseWorldPoint(mapData, obstacles, obstacleIndex, x, z + dz));
                }
            }
        }
        PathWorldPoint stuckWorld = new PathWorldPoint(x, z);
        List<PathWorldPoint> stuckWorlds = Collections.singletonList(stuckWorld);
        List<PathGridPoint> stuckGrids = PathCommonUtil.toGridPath(mapData, stuckWorlds);
        PathGridPoint stuckGrid = stuckGrids.isEmpty() ? null : stuckGrids.get(0);
        return new PathFailureDetail(stuckGrid, stuckWorld, reasons);
    }

    /**
     * 判断是否可以移动；未勾选切角时斜向两侧正交格也须安全。
     */
    private static boolean canMove(MapData mapData, List<PathObstacle> obstacles, PathObstacleIndex obstacleIndex,
        int x, int z, int nextX, int nextZ, int dx, int dz, boolean allowCutCorner) {
        if (!PathCommonUtil.isSafeWorldPoint(mapData, obstacles, obstacleIndex, nextX, nextZ)) {
            return false;
        }
        if (!allowCutCorner && dx != 0 && dz != 0) {
            return PathCommonUtil.isSafeWorldPoint(mapData, obstacles, obstacleIndex, x + dx, z)
                && PathCommonUtil.isSafeWorldPoint(mapData, obstacles, obstacleIndex, x, z + dz);
        }
        return true;
    }

    /**
     * 从终点沿记录的方向回溯到起点。
     *
     * @param data
     *            搜索数据
     * @param startIdx
     *            起点索引
     * @param endIdx
     *            终点索引
     * @return 路径
     */
    private static List<PathWorldPoint> buildPath(SearchData data, int startIdx, int endIdx) {
        List<PathWorldPoint> ret = new ArrayList<>();
        int idx = endIdx;
        while (true) {
            ret.add(new PathWorldPoint(data.toX(idx), data.toZ(idx)));
            if (idx == startIdx) {
                break;
            }
            byte d = data.dir[idx];
            if (d < 0) {
                break;
            }
            int prevX = data.toX(idx) - PathAStarUtil.DIR_X[d];
            int prevZ = data.toZ(idx) - PathAStarUtil.DIR_Z[d];
            idx = data.index(prevX, prevZ);
        }
        Collections.reverse(ret);
        return ret;
    }

    /**
     * 将 f 值与格子下标打包为单个 long（f 在高 32 位，最小堆按 f 升序）。
     *
     * @param fScore
     *            f 值
     * @param index
     *            格子下标
     * @return 打包后的 long
     */
    private static long pack(int fScore, int index) {
        return ((long)fScore << 32) | (index & 0xffffffffL);
    }

    /**
     * 搜索结果。
     */
    private static class SearchResult {

        private final List<PathWorldPoint> path;// 路径

        private final List<PathWorldPoint> searched;// 搜索展开点

        private final PathFailureDetail failureDetail;// 失败原因

        SearchResult(List<PathWorldPoint> path, List<PathWorldPoint> searched) {
            this(path, searched, PathFailureDetail.none());
        }

        SearchResult(List<PathWorldPoint> path, List<PathWorldPoint> searched, PathFailureDetail failureDetail) {
            this.path = path == null ? Collections.emptyList() : path;
            this.searched = searched == null ? Collections.emptyList() : searched;
            this.failureDetail = failureDetail == null ? PathFailureDetail.none() : failureDetail;
        }
    }

    /**
     * 稠密搜索态：g 值、来向、closed/走廊位图与开放堆，整图尺寸一次分配。
     */
    private static class SearchData {

        private final int width;// 宽度

        private final int height;// 高度

        private final int[] gScore;// 每格累计代价，未到达为 INF。

        private final byte[] dir;// 每格到达来向（PathAStarUtil 方向下标），-1 表示未设置。

        private final BitSet closed;// 已确定最短代价的格。

        private final BitSet allowed;// 走廊内可搜索的格（仅 useCorridor 时生效）。

        private final List<PathWorldPoint> searched = new ArrayList<>();// 搜索展开点

        private int bestX;// 最佳 X 坐标

        private int bestZ;// 最佳 Z 坐标

        private int bestH = INF;// 最佳 H 值

        private final LongMinHeap open = new LongMinHeap();// 开放堆

        SearchData(int width, int height) {
            this.width = width;
            this.height = height;
            int size = width * height;
            this.gScore = new int[size];
            this.dir = new byte[size];
            this.closed = new BitSet(size);
            this.allowed = new BitSet(size);
            Arrays.fill(gScore, INF);
            Arrays.fill(dir, (byte)-1);
        }

        /**
         * 清空搜索态以便整图回退复用同一份数组（保留 allowed，回退时不再引用）。
         */
        void reset() {
            Arrays.fill(gScore, INF);
            Arrays.fill(dir, (byte)-1);
            closed.clear();
            searched.clear();
            open.clear();
            bestH = INF;
        }

        /**
         * 在粗格路线各点周边标记走廊：相邻点间距小于半宽，圆盘（此处用方块近似）自然连续。
         */
        void markCorridor(List<PathWorldPoint> corridorPath) {
            for (PathWorldPoint point : corridorPath) {
                int cx = point.getWorldX();
                int cz = point.getWorldZ();
                int minX = Math.max(0, cx - PointPathStrategy.CORRIDOR_RADIUS);
                int maxX = Math.min(width - 1, cx + PointPathStrategy.CORRIDOR_RADIUS);
                int minZ = Math.max(0, cz - PointPathStrategy.CORRIDOR_RADIUS);
                int maxZ = Math.min(height - 1, cz + PointPathStrategy.CORRIDOR_RADIUS);
                for (int z = minZ; z <= maxZ; z++) {
                    int base = z * width;
                    for (int x = minX; x <= maxX; x++) {
                        allowed.set(base + x);
                    }
                }
            }
        }

        int index(int x, int z) {
            return z * width + x;
        }

        int toX(int index) {
            return index % width;
        }

        int toZ(int index) {
            return index / width;
        }

        boolean inBounds(int x, int z) {
            return x >= 0 && z >= 0 && x < width && z < height;
        }

        void updateBest(int x, int z, int hScore) {
            if (hScore < bestH) {
                bestH = hScore;
                bestX = x;
                bestZ = z;
            }
        }
    }

    /**
     * 存放打包后 long 的最小二叉堆，避免 PriorityQueue 的对象装箱与重复入队对象开销。
     */
    private static final class LongMinHeap {

        private long[] data = new long[64];

        private int size;

        boolean isEmpty() {
            return size == 0;
        }

        void clear() {
            size = 0;
        }

        void push(long value) {
            if (size == data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size] = value;
            siftUp(size);
            size++;
        }

        long pop() {
            long top = data[0];
            size--;
            data[0] = data[size];
            siftDown(0);
            return top;
        }

        private void siftUp(int index) {
            long value = data[index];
            while (index > 0) {
                int parent = (index - 1) >>> 1;
                if (data[parent] <= value) {
                    break;
                }
                data[index] = data[parent];
                index = parent;
            }
            data[index] = value;
        }

        private void siftDown(int index) {
            long value = data[index];
            int half = size >>> 1;
            while (index < half) {
                int child = (index << 1) + 1;
                int right = child + 1;
                if (right < size && data[right] < data[child]) {
                    child = right;
                }
                if (data[child] >= value) {
                    break;
                }
                data[index] = data[child];
                index = child;
            }
            data[index] = value;
        }
    }
}
