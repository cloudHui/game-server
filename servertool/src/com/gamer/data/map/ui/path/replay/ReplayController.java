package com.gamer.data.map.ui.path.replay;

import java.util.ArrayList;
import java.util.List;

import javax.swing.Timer;

import com.gamer.data.map.path.common.PathGridPoint;
import com.gamer.data.map.path.common.PathResult;
import com.gamer.data.map.path.common.PathWorldPoint;
import com.gamer.data.map.ui.canvas.MapViewerCanvas;

/**
 * 寻路完成后，在地图画布上逐帧回放记录的 A* 搜索点。
 */
public final class ReplayController {

    /** Swing 定时器帧间隔（毫秒） */
    private static final int FRAME_DELAY_MS = 20;

    /** 默认每帧发布点数 */
    public static final int DEFAULT_BATCH_SIZE = 100;

    /** 短路径保底约多少帧，避免点数小于每帧量时一闪结束 */
    private static final int SHORT_REPLAY_FRAMES = 20;

    /** 当前回放定时器 */
    private Timer replayTimer;

    /** 接收可见回放数据的画布 */
    private MapViewerCanvas canvas;

    /** 回放起点格 */
    private PathGridPoint start;

    /** 回放终点格 */
    private PathGridPoint end;

    /** 回放起点世界坐标 */
    private PathWorldPoint startWorld;

    /** 回放终点世界坐标 */
    private PathWorldPoint endWorld;

    /** 回放结束后展示的最终寻路结果 */
    private PathResult result;

    /** 按展示顺序排列的回放段 */
    private List<PathSearchReplaySegment> segments;

    /** 已发布到画布的格子搜索点 */
    private List<PathGridPoint> visibleGridPoints;

    /** 已发布到画布的世界坐标搜索点 */
    private List<PathWorldPoint> visibleWorldPoints;

    /** 当前回放段索引 */
    private int segmentIndex;

    /** 每帧发布点数 */
    private int batchSize = DEFAULT_BATCH_SIZE;

    /** 回放结束回调 */
    private ReplayFinishListener finishListener;

    /**
     * 回放展示完最终路径或失败态之后触发的回调。
     */
    public interface ReplayFinishListener {

        /**
         * 回放结束时在 Swing 事件分发线程回调。
         *
         * @param result
         *            回放的寻路结果
         */
        void onReplayFinished(PathResult result);
    }

    /**
     * 开始一次新的搜索回放，开始前先停止上一次回放。
     *
     * @param canvas
     *            地图画布
     * @param start
     *            起点格
     * @param end
     *            终点格
     * @param startWorld
     *            起点世界坐标
     * @param endWorld
     *            终点世界坐标
     * @param result
     *            已算出的寻路结果
     * @param batchSize
     *            每帧发布点数；非法时回退默认值
     * @param finishListener
     *            回放结束监听器
     */
    public void start(MapViewerCanvas canvas, PathGridPoint start, PathGridPoint end, PathWorldPoint startWorld,
        PathWorldPoint endWorld, PathResult result, int batchSize, ReplayFinishListener finishListener) {
        stop();
        this.canvas = canvas;
        this.start = start;
        this.end = end;
        this.startWorld = startWorld;
        this.endWorld = endWorld;
        this.result = result;
        this.finishListener = finishListener;
        this.segments = buildSegments(result);
        this.visibleGridPoints = new ArrayList<>();
        this.visibleWorldPoints = new ArrayList<>();
        this.segmentIndex = 0;
        this.batchSize = resolveBatchSize(batchSize, countSearchPoints(result));
        updateCanvas(false);
        if (segments.isEmpty()) {
            finishReplay();
            return;
        }
        replayTimer = new Timer(FRAME_DELAY_MS, e -> advanceFrame());
        replayTimer.start();
    }

    /**
     * 停止当前回放，且不触发结束回调。
     */
    public void stop() {
        if (replayTimer != null) {
            replayTimer.stop();
            replayTimer = null;
        }
        finishListener = null;
    }

    /**
     * 推进一帧：追加一批点；全部完成则结束回放（末帧只刷一次最终态）。
     */
    private void advanceFrame() {
        while (segmentIndex < segments.size()) {
            PathSearchReplaySegment segment = segments.get(segmentIndex);
            if (segment.isDone()) {
                segmentIndex++;
                continue;
            }
            segment.appendNext(visibleGridPoints, visibleWorldPoints, batchSize);
            if (segment.isDone()) {
                segmentIndex++;
            }
            break;
        }
        if (segmentIndex >= segments.size()) {
            finishReplay();
            return;
        }
        updateCanvas(false);
    }

    /**
     * 结束回放：停止定时器，画出最终路径，并触发结束回调（回调只触发一次）。
     */
    private void finishReplay() {
        if (replayTimer != null) {
            replayTimer.stop();
            replayTimer = null;
        }
        updateCanvas(true);
        ReplayFinishListener listener = finishListener;
        PathResult replayedResult = result;
        finishListener = null;
        if (listener != null) {
            listener.onReplayFinished(replayedResult);
        }
    }

    /**
     * 把当前可见搜索点（及可选的最终路径）推送到画布。
     *
     * @param showFinalPath
     *            是否展示最终路径（回放结束时为 true）
     */
    private void updateCanvas(boolean showFinalPath) {
        if (canvas == null) {
            return;
        }
        List<PathGridPoint> gridPath = null;
        List<PathWorldPoint> worldPath = null;
        if (showFinalPath && result != null && result.isSuccess()) {
            gridPath = result.getGridPath();
            worldPath = result.getWorldPath();
        }
        canvas.setPathDebugData(start, end, startWorld, endWorld, gridPath, worldPath, visibleGridPoints,
            visibleWorldPoints);
    }

    /**
     * 按结果构建回放段：先粗格搜索段，再世界坐标搜索段（各自非空才加入）。
     */
    private static List<PathSearchReplaySegment> buildSegments(PathResult result) {
        List<PathSearchReplaySegment> ret = new ArrayList<>();
        if (result == null) {
            return ret;
        }
        if (!result.getSearchedGrids().isEmpty()) {
            ret.add(PathSearchReplaySegment.gridSearch(result.getSearchedGrids()));
        }
        if (!result.getSearchedWorlds().isEmpty()) {
            ret.add(PathSearchReplaySegment.pointSearch(result.getSearchedWorlds()));
        }
        return ret;
    }

    private static int countSearchPoints(PathResult result) {
        if (result == null) {
            return 0;
        }
        return result.getSearchedGrids().size() + result.getSearchedWorlds().size();
    }

    /**
     * 解析每帧点数；非法回退默认；总点数不超过每帧量时压到约 {@link #SHORT_REPLAY_FRAMES} 帧。
     */
    private static int resolveBatchSize(int configured, int totalPoints) {
        int batch = configured > 0 ? configured : DEFAULT_BATCH_SIZE;
        if (totalPoints > 0 && totalPoints <= batch) {
            return Math.max(1, (totalPoints + SHORT_REPLAY_FRAMES - 1) / SHORT_REPLAY_FRAMES);
        }
        return batch;
    }
}
