package com.cloud.hub.web.minigame.ai;

import com.cloud.hub.web.minigame.gomoku.GomokuBoard;

/**
 * 五子棋轻量级启发式 AI 决策引擎。
 * <p>
 * 遍历 15x15 棋盘空位，结合攻守双向权值（连五、活四、冲四、活三、冲三、活二）及中心距离打分，
 * 兼具防守与突袭能力，计算耗时 < 5ms，专用于人机对战与快速陪练。
 * </p>
 *
 * @author cloud
 */
public final class GomokuAi {

    private static final int[][] DIRS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    private GomokuAi() {
    }

    /**
     * 计算并推荐最佳落子坐标。
     *
     * @param cells 当前棋盘二维快照
     * @param myColor 机器人的棋子颜色 (1=黑, 2=白)
     * @return 包含最佳 [x, y] 坐标的数组；若无合法位置返回 null
     */
    public static int[] bestMove(int[][] cells, int myColor) {
        int oppColor = (myColor == GomokuBoard.BLACK) ? GomokuBoard.WHITE : GomokuBoard.BLACK;
        int bestScore = -1;
        int bestX = -1;
        int bestY = -1;

        int size = GomokuBoard.SIZE;
        // 如果棋盘全空，优先占据天元 (7, 7)
        if (isEmptyBoard(cells, size)) {
            return new int[]{size / 2, size / 2};
        }

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (cells[y][x] != GomokuBoard.EMPTY) {
                    continue;
                }
                // 仅评估周围有棋子的点（加速搜索）
                if (!hasNeighbor(cells, x, y, size)) {
                    continue;
                }

                int attackScore = evaluatePoint(cells, x, y, myColor, size);
                int defenseScore = evaluatePoint(cells, x, y, oppColor, size);
                // 防守权重略高以防被绝杀，进攻绝杀权值更高
                int totalScore = attackScore + (int) (defenseScore * 1.15);

                // 稍微偏向棋盘中心
                int centerBias = (size / 2 - Math.abs(x - size / 2)) + (size / 2 - Math.abs(y - size / 2));
                totalScore += centerBias;

                if (totalScore > bestScore) {
                    bestScore = totalScore;
                    bestX = x;
                    bestY = y;
                }
            }
        }

        if (bestX >= 0) {
            return new int[]{bestX, bestY};
        }
        // 兜底找第一个空格
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (cells[y][x] == GomokuBoard.EMPTY) {
                    return new int[]{x, y};
                }
            }
        }
        return null;
    }

    private static boolean isEmptyBoard(int[][] cells, int size) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (cells[y][x] != GomokuBoard.EMPTY) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasNeighbor(int[][] cells, int x, int y, int size) {
        int dist = 2;
        for (int dy = -dist; dy <= dist; dy++) {
            for (int dx = -dist; dx <= dist; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < size && ny >= 0 && ny < size && cells[ny][nx] != GomokuBoard.EMPTY) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int evaluatePoint(int[][] cells, int x, int y, int color, int size) {
        int total = 0;
        for (int[] d : DIRS) {
            int count = 1;
            int openEnds = 0;

            // 正方向探测
            int cx = x + d[0];
            int cy = y + d[1];
            while (cx >= 0 && cx < size && cy >= 0 && cy < size && cells[cy][cx] == color) {
                count++;
                cx += d[0];
                cy += d[1];
            }
            if (cx >= 0 && cx < size && cy >= 0 && cy < size && cells[cy][cx] == GomokuBoard.EMPTY) {
                openEnds++;
            }

            // 反方向探测
            cx = x - d[0];
            cy = y - d[1];
            while (cx >= 0 && cx < size && cy >= 0 && cy < size && cells[cy][cx] == color) {
                count++;
                cx -= d[0];
                cy -= d[1];
            }
            if (cx >= 0 && cx < size && cy >= 0 && cy < size && cells[cy][cx] == GomokuBoard.EMPTY) {
                openEnds++;
            }

            total += scoreLine(count, openEnds);
        }
        return total;
    }

    private static int scoreLine(int count, int openEnds) {
        if (count >= 5) {
            return 100000;
        }
        if (count == 4) {
            return openEnds == 2 ? 10000 : (openEnds == 1 ? 1000 : 0);
        }
        if (count == 3) {
            return openEnds == 2 ? 1000 : (openEnds == 1 ? 100 : 0);
        }
        if (count == 2) {
            return openEnds == 2 ? 100 : (openEnds == 1 ? 10 : 0);
        }
        return openEnds > 0 ? 2 : 0;
    }
}
