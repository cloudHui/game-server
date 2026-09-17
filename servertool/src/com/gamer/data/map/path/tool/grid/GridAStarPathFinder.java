package com.gamer.data.map.path.tool.grid;

import com.gamer.data.map.path.common.PathGridPoint;
import com.gamer.data.map.path.common.PathBlockReason;
import com.gamer.data.map.path.common.PathFailureDetail;
import com.gamer.data.map.path.common.PathWorldPoint;
import com.gamer.data.map.path.tool.common.PathAStarUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * 粗网格 8 方向 A*。
 */
public final class GridAStarPathFinder {

    private static final int INF = Integer.MAX_VALUE / 4;

    private GridAStarPathFinder() {}

    public static SearchResult findPath(GridPathGrid grid, int startCol, int startRow, int endCol, int endRow,
        boolean recordSearch) {
        return findPath(grid, startCol, startRow, endCol, endRow, recordSearch, false);
    }

    public static SearchResult findPath(GridPathGrid grid, int startCol, int startRow, int endCol, int endRow,
        boolean recordSearch, boolean allowCutCorner) {
        if (grid == null || !grid.isWalkable(startCol, startRow) || !grid.isWalkable(endCol, endRow)) {
            PathBlockReason reason = grid == null ? PathBlockReason.none()
                : (!grid.isWalkable(startCol, startRow) ? grid.diagnoseTile(startCol, startRow)
                    : grid.diagnoseTile(endCol, endRow));
            PathFailureDetail detail = grid == null || !reason.isPresent() ? PathFailureDetail.none()
                : createFailureDetail(!grid.isWalkable(startCol, startRow) ? startCol : endCol,
                    !grid.isWalkable(startCol, startRow) ? startRow : endRow, Collections.singletonList(reason));
            return new SearchResult(Collections.emptyList(), Collections.emptyList(), detail);
        }
        SearchData data = new SearchData(grid);
        PriorityQueue<Node> open = new PriorityQueue<>();
        int startIdx = data.toIndex(startCol, startRow);
        data.gScore[startIdx] = 0;
        int startH = PathAStarUtil.heuristic(startCol, startRow, endCol, endRow);
        open.add(new Node(startCol, startRow, startH, startH));
        while (!open.isEmpty()) {
            Node cur = open.poll();
            int curIdx = data.toIndex(cur.col, cur.row);
            if (data.closed[curIdx]) {
                continue;
            }
            data.closed[curIdx] = true;
            data.updateBest(cur.col, cur.row, cur.hScore);
            if (recordSearch) {
                data.searched.add(new PathGridPoint(cur.col, cur.row));
            }
            if (cur.col == endCol && cur.row == endRow) {
                return new SearchResult(buildPath(data, startIdx, curIdx), data.searched, PathFailureDetail.none());
            }
            expandNeighbors(grid, data, open, cur, endCol, endRow, allowCutCorner);
        }
        return new SearchResult(Collections.emptyList(), data.searched,
            buildFailureDetail(grid, data.bestCol, data.bestRow, endCol, endRow, allowCutCorner));
    }

    private static void expandNeighbors(GridPathGrid grid, SearchData data, PriorityQueue<Node> open, Node cur,
        int endCol, int endRow, boolean allowCutCorner) {
        int curIdx = data.toIndex(cur.col, cur.row);
        for (int i = 0; i < PathAStarUtil.DIR_X.length; i++) {
            int nextCol = cur.col + PathAStarUtil.DIR_X[i];
            int nextRow = cur.row + PathAStarUtil.DIR_Z[i];
            if (!canMove(grid, cur.col, cur.row, nextCol, nextRow, PathAStarUtil.DIR_X[i], PathAStarUtil.DIR_Z[i],
                allowCutCorner)) {
                continue;
            }
            int nextIdx = data.toIndex(nextCol, nextRow);
            if (data.closed[nextIdx]) {
                continue;
            }
            int nextG = data.gScore[curIdx] + PathAStarUtil.DIR_COST[i];
            if (nextG >= data.gScore[nextIdx]) {
                continue;
            }
            data.gScore[nextIdx] = nextG;
            data.parent[nextIdx] = curIdx;
            int nextH = PathAStarUtil.heuristic(nextCol, nextRow, endCol, endRow);
            open.add(new Node(nextCol, nextRow, nextG + nextH, nextH));
        }
    }

    private static PathFailureDetail buildFailureDetail(GridPathGrid grid, int col, int row, int endCol, int endRow,
        boolean allowCutCorner) {
        List<PathBlockReason> reasons = new ArrayList<>();
        int currentH = PathAStarUtil.heuristic(col, row, endCol, endRow);
        for (int i = 0; i < PathAStarUtil.DIR_X.length; i++) {
            int dc = PathAStarUtil.DIR_X[i];
            int dr = PathAStarUtil.DIR_Z[i];
            int nextCol = col + dc;
            int nextRow = row + dr;
            if (PathAStarUtil.heuristic(nextCol, nextRow, endCol, endRow) >= currentH) {
                continue;
            }
            if (!grid.isWalkable(nextCol, nextRow)) {
                PathFailureDetail.addUniqueReason(reasons, grid.diagnoseTile(nextCol, nextRow));
            } else if (!allowCutCorner && dc != 0 && dr != 0) {
                if (!grid.isWalkable(col + dc, row)) {
                    PathFailureDetail.addUniqueReason(reasons, grid.diagnoseTile(col + dc, row));
                }
                if (!grid.isWalkable(col, row + dr)) {
                    PathFailureDetail.addUniqueReason(reasons, grid.diagnoseTile(col, row + dr));
                }
            }
        }
        return createFailureDetail(col, row, reasons);
    }

    private static PathFailureDetail createFailureDetail(int col, int row, List<PathBlockReason> reasons) {
        PathGridPoint stuckGrid = new PathGridPoint(col, row);
        return new PathFailureDetail(stuckGrid, new PathWorldPoint(stuckGrid.getWorldX(), stuckGrid.getWorldZ()),
            reasons);
    }

    private static boolean canMove(GridPathGrid grid, int col, int row, int nextCol, int nextRow, int dc, int dr,
        boolean allowCutCorner) {
        if (!grid.isWalkable(nextCol, nextRow)) {
            return false;
        }
        if (!allowCutCorner && dc != 0 && dr != 0) {
            return grid.isWalkable(col + dc, row) && grid.isWalkable(col, row + dr);
        }
        return true;
    }

    private static List<PathGridPoint> buildPath(SearchData data, int startIdx, int endIdx) {
        List<PathGridPoint> ret = new ArrayList<>();
        int idx = endIdx;
        while (idx >= 0) {
            ret.add(new PathGridPoint(data.toCol(idx), data.toRow(idx)));
            if (idx == startIdx) {
                break;
            }
            idx = data.parent[idx];
        }
        Collections.reverse(ret);
        return ret;
    }

    public static class SearchResult {

        private final List<PathGridPoint> path;

        private final List<PathGridPoint> searched;

        private final PathFailureDetail failureDetail;

        SearchResult(List<PathGridPoint> path, List<PathGridPoint> searched, PathFailureDetail failureDetail) {
            this.path = path == null ? Collections.emptyList() : path;
            this.searched = searched == null ? Collections.emptyList() : searched;
            this.failureDetail = failureDetail == null ? PathFailureDetail.none() : failureDetail;
        }

        public List<PathGridPoint> getPath() {
            return path;
        }

        public List<PathGridPoint> getSearched() {
            return searched;
        }

        public PathFailureDetail getFailureDetail() {
            return failureDetail;
        }
    }

    private static class SearchData {

        private final int width;

        private final int[] gScore;

        private final int[] parent;

        private final boolean[] closed;

        private final List<PathGridPoint> searched = new ArrayList<>();

        private int bestCol;

        private int bestRow;

        private int bestH = INF;

        SearchData(GridPathGrid grid) {
            this.width = grid.getWidth();
            int size = grid.getWidth() * grid.getHeight();
            this.gScore = new int[size];
            this.parent = new int[size];
            this.closed = new boolean[size];
            Arrays.fill(gScore, INF);
            Arrays.fill(parent, -1);
        }

        int toIndex(int col, int row) {
            return row * width + col;
        }

        int toCol(int index) {
            return index % width;
        }

        int toRow(int index) {
            return index / width;
        }

        void updateBest(int col, int row, int hScore) {
            if (hScore < bestH) {
                bestH = hScore;
                bestCol = col;
                bestRow = row;
            }
        }
    }

    private static class Node implements Comparable<Node> {

        private final int col;

        private final int row;

        private final int fScore;

        private final int hScore;

        Node(int col, int row, int fScore, int hScore) {
            this.col = col;
            this.row = row;
            this.fScore = fScore;
            this.hScore = hScore;
        }

        @Override
        public int compareTo(Node other) {
            int fCompare = Integer.compare(this.fScore, other.fScore);
            return fCompare != 0 ? fCompare : Integer.compare(this.hScore, other.hScore);
        }
    }
}
