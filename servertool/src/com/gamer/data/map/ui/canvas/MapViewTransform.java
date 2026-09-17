package com.gamer.data.map.ui.canvas;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

import javax.swing.JComponent;
import javax.swing.JViewport;

/**
 * 地图画布坐标变换与缩放计算。
 */
public final class MapViewTransform {

    /** 地图与视口边缘留白（像素） */
    public static final int MAP_VIEW_MARGIN_PX = 40;

    /** 单格最大边长（像素） */
    public static final int MAX_CELL_SIZE_PX = 48;

    /** 地图总边长相对视口短边的最大倍数 */
    public static final int MAX_VIEWPORT_ZOOM_FACTOR = 15;

    /** 从默认缩放到最大缩放所需的放大次数 */
    public static final int ZOOM_STEP_COUNT = 5;

    private MapViewTransform() {}

    /**
     * 将视口滚动位置限制在合法范围内。
     */
    public static Point clampViewPosition(JViewport vp, int x, int y) {
        java.awt.Component view = vp.getView();
        if (view == null) {
            return new Point(0, 0);
        }
        Dimension extent = vp.getExtentSize();
        int vw = view.getWidth();
        int vh = view.getHeight();
        int maxX = Math.max(0, vw - extent.width);
        int maxY = Math.max(0, vh - extent.height);
        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
        if (x > maxX) {
            x = maxX;
        }
        if (y > maxY) {
            y = maxY;
        }
        return new Point(x, y);
    }

    /**
     * 解析用于计算缩放的视口尺寸。
     */
    public static Dimension resolveViewportSize(JComponent canvas) {
        Rectangle vr = canvas.getVisibleRect();
        int vw = vr.width;
        int vh = vr.height;
        if (vw <= 0 || vh <= 0) {
            Container p = canvas.getParent();
            if (p instanceof JViewport) {
                Dimension ext = ((JViewport)p).getExtentSize();
                vw = Math.max(1, ext.width);
                vh = Math.max(1, ext.height);
            } else {
                vw = Math.max(1, canvas.getWidth());
                vh = Math.max(1, canvas.getHeight());
            }
        }
        if (vw == 1 && vh == 1) {
            vw = 400;
            vh = 300;
        }
        return new Dimension(vw, vh);
    }

    /**
     * 根据视口与地图尺寸计算最大缩放系数。
     */
    public static double resolveMaxZoomScale(int vw, int vh, int cols, int rows) {
        double viewportMin = Math.min(vw, (double)vh);
        if (viewportMin <= 0D) {
            return 1.0D;
        }
        int maxDim = Math.max(cols, rows);
        double cellCapSquareSize = MAX_CELL_SIZE_PX * maxDim;
        double viewportCapSquareSize = viewportMin * MAX_VIEWPORT_ZOOM_FACTOR;
        double capSquareSize = Math.min(cellCapSquareSize, viewportCapSquareSize);
        double maxZoom = capSquareSize / viewportMin;
        return Math.max(1.0D, maxZoom);
    }

    /**
     * 根据视口、地图尺寸与缩放系数计算单元格边长（像素）。
     */
    public static double computeCellSize(int vw, int vh, int cols, int rows, double zoom) {
        double viewportMin = Math.min(vw, (double)vh);
        double squareSize = viewportMin * zoom;
        squareSize = Math.max(100D, squareSize);
        int maxDim = Math.max(cols, rows);
        double cellCapSquareSize = MAX_CELL_SIZE_PX * maxDim;
        double viewportCapSquareSize = viewportMin * MAX_VIEWPORT_ZOOM_FACTOR;
        squareSize = Math.min(squareSize, Math.min(cellCapSquareSize, viewportCapSquareSize));
        return squareSize / (double)maxDim;
    }

    /**
     * 世界坐标转画布像素（节点中心）。
     */
    public static int[] worldToPixel(int worldX, int worldZ, MapViewContext ctx) {
        double tx = (double)worldX / com.gamer.data.map.grid.MapRingUtil.WORLD_PIXEL_PER_CELL;
        double tz = (double)worldZ / com.gamer.data.map.grid.MapRingUtil.WORLD_PIXEL_PER_CELL;
        int cx = (int)Math.round(ctx.mapLeftX + tx * ctx.cellSize);
        int cy = (int)Math.round(ctx.mapBottomY - tz * ctx.cellSize);
        return new int[] {cx, cy};
    }

    /**
     * 构建渲染上下文。
     */
    public static MapViewContext createContext(int cols, int rows, double zoomScale, int canvasHeight,
        Dimension viewportSize) {
        double cellSize = computeCellSize(viewportSize.width, viewportSize.height, cols, rows, zoomScale);
        double mapWidth = cols * cellSize;
        double mapHeight = rows * cellSize;
        int ch = Math.max(1, canvasHeight);
        // 地图左贴视口（留 margin），纵向居中；右侧空白为纯白背景，不绘制网格
        double mapLeftX = MAP_VIEW_MARGIN_PX;
        double mapTopY = ((double)ch - mapHeight) / 2D;
        return new MapViewContext(cellSize, mapLeftX, mapTopY, mapLeftX + mapWidth, mapTopY + mapHeight, cols, rows);
    }
}
