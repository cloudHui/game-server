package com.gamer.data.map.ui.canvas;

import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;

import com.gamer.data.map.grid.MapData;
import com.gamer.data.map.grid.MapRingUtil;
import com.gamer.data.map.level.LevelNodeBean;
import com.gamer.data.map.path.common.PathGridPoint;
import com.gamer.data.map.path.common.PathBlockReason;
import com.gamer.data.map.path.common.PathFailureDetail;
import com.gamer.data.map.path.common.PathWorldPoint;

/**
 * 地图画布：坐标变换、交互与绘制入口。
 */
public class MapViewerCanvas extends JPanel {

    /**
     * 地图区域点击回调（仅在未触发拖拽平移时触发）。
     */
    public interface MapClickListener {

        void onMapCanvasClicked(int pixelX, int pixelY);
    }

    /**
     * 右键弹出菜单回调。
     */
    public interface MapPopupListener {

        void onMapPopup(int pixelX, int pixelY);
    }

    private static final long serialVersionUID = 1L;
    private static final int NODE_HIT_RADIUS = 2;
    private static final int PAN_CLICK_THRESHOLD_PX = 5;

    private MapData mapData;
    private List<LevelNodeBean> levelNodes = new ArrayList<>();
    private final MapCanvasPaintModel paintModel = new MapCanvasPaintModel();
    private double zoomScale = 1.0D;

    private ComponentAdapter viewportResizeAdapter;
    private Runnable zoomStateListener;
    private Runnable viewportChangeListener;
    private MapClickListener mapClickListener;
    private MapPopupListener mapPopupListener;

    private int panPressCanvasX;
    private int panPressCanvasY;
    private int panStartViewX;
    private int panStartViewY;
    private boolean panGestureActive;
    private boolean panMovedBeyondThreshold;

    public MapViewerCanvas() {
        setBackground(Color.WHITE);
        addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (panGestureActive) {
                    return;
                }
                LevelNodeBean node = getLevelNodeAt(e.getX(), e.getY());
                setToolTipText(node == null ? null : node.toString());
            }
        });
        installPanAndClickHandler();
        installPopupHandler();
    }

    public void setZoomStateListener(Runnable listener) {
        this.zoomStateListener = listener;
    }

    public void setViewportChangeListener(Runnable listener) {
        this.viewportChangeListener = listener;
    }

    public MapData getMapData() {
        return mapData;
    }

    public Rectangle getMapContentRect() {
        if (mapData == null || mapData.getMap() == null) {
            return null;
        }
        MapViewContext ctx = buildViewContext();
        int w = (int)Math.round(ctx.mapRightX - ctx.mapLeftX);
        int h = (int)Math.round(ctx.mapBottomY - ctx.mapTopY);
        if (w <= 0 || h <= 0) {
            return null;
        }
        return new Rectangle((int)Math.round(ctx.mapLeftX), (int)Math.round(ctx.mapTopY), w, h);
    }

    public void scrollViewToCanvasCenter(int canvasX, int canvasY) {
        Container parent = getParent();
        if (!(parent instanceof JViewport)) {
            return;
        }
        JViewport vp = (JViewport)parent;
        Dimension ext = vp.getExtentSize();
        int nx = canvasX - ext.width / 2;
        int ny = canvasY - ext.height / 2;
        vp.setViewPosition(MapViewTransform.clampViewPosition(vp, nx, ny));
        fireViewportChanged();
    }

    public static Color resolveCellDisplayColor(int val) {
        return MapCanvasPainter.resolveCellDisplayColor(val);
    }

    public void setMapClickListener(MapClickListener listener) {
        this.mapClickListener = listener;
    }

    public void setMapPopupListener(MapPopupListener listener) {
        this.mapPopupListener = listener;
    }

    public boolean canBigger() {
        if (mapData == null || mapData.getMap() == null) {
            return false;
        }
        return zoomScale < resolveMaxZoomScale() - 1e-6D;
    }

    public boolean canSmaller() {
        return zoomScale > 1.0D;
    }

    public boolean canReset() {
        return Math.abs(zoomScale - 1.0D) > 0.01d;
    }

    public void setMapData(MapData data) {
        this.mapData = data;
        paintModel.mapData = data;
        syncCanvasSizeToMap();
        repaint();
    }

    public void setLevelNodes(List<LevelNodeBean> nodes) {
        this.levelNodes = nodes != null ? nodes : new ArrayList<>();
        paintModel.levelNodes = this.levelNodes;
        paintModel.selectedLevelNode = null;
        repaint();
    }

    public void setMarkerPoint(int worldX, int worldZ) {
        paintModel.markerWorldX = worldX;
        paintModel.markerWorldZ = worldZ;
        repaint();
    }

    public void clearMarkerPoint() {
        setMarkerPoint(MapCanvasPaintModel.MARKER_NONE, MapCanvasPaintModel.MARKER_NONE);
    }

    public void setDistanceMarkers(int worldX1, int worldZ1, int worldX2, int worldZ2) {
        paintModel.distanceMarker1WorldX = worldX1;
        paintModel.distanceMarker1WorldZ = worldZ1;
        paintModel.distanceMarker2WorldX = worldX2;
        paintModel.distanceMarker2WorldZ = worldZ2;
        repaint();
    }

    public void setDistanceMarkersFromUi(int worldX1, int worldZ1, int worldX2, int worldZ2) {
        if (worldX1 == -1 && worldZ1 == -1) {
            paintModel.distanceMarker1WorldX = MapCanvasPaintModel.MARKER_NONE;
            paintModel.distanceMarker1WorldZ = MapCanvasPaintModel.MARKER_NONE;
        } else {
            paintModel.distanceMarker1WorldX = worldX1;
            paintModel.distanceMarker1WorldZ = worldZ1;
        }
        if (worldX2 == -1 && worldZ2 == -1) {
            paintModel.distanceMarker2WorldX = MapCanvasPaintModel.MARKER_NONE;
            paintModel.distanceMarker2WorldZ = MapCanvasPaintModel.MARKER_NONE;
        } else {
            paintModel.distanceMarker2WorldX = worldX2;
            paintModel.distanceMarker2WorldZ = worldZ2;
        }
        repaint();
    }

    public void clearDistanceMarkers() {
        setDistanceMarkers(MapCanvasPaintModel.MARKER_NONE, MapCanvasPaintModel.MARKER_NONE,
            MapCanvasPaintModel.MARKER_NONE, MapCanvasPaintModel.MARKER_NONE);
    }

    /**
     * 更新寻路调试数据（仅含粗格搜索点，世界搜索点置空）。
     *
     * @param start
     *            起点格
     * @param end
     *            终点格
     * @param startWorld
     *            起点世界坐标
     * @param endWorld
     *            终点世界坐标
     * @param path
     *            最终格子路径
     * @param worldPath
     *            最终世界坐标路径
     * @param searched
     *            当前可见的已搜索格子点
     */
    public void setPathDebugData(PathGridPoint start, PathGridPoint end, PathWorldPoint startWorld,
        PathWorldPoint endWorld, List<PathGridPoint> path, List<PathWorldPoint> worldPath,
        List<PathGridPoint> searched) {
        setPathDebugData(start, end, startWorld, endWorld, path, worldPath, searched, null);
    }

    /**
     * 更新寻路调试数据（同时含粗格搜索点与细坐标世界搜索点）。
     *
     * @param start
     *            起点格
     * @param end
     *            终点格
     * @param startWorld
     *            起点世界坐标
     * @param endWorld
     *            终点世界坐标
     * @param path
     *            最终格子路径
     * @param worldPath
     *            最终世界坐标路径
     * @param searched
     *            当前可见的已搜索格子点
     * @param searchedWorlds
     *            当前可见的已搜索世界坐标点
     */
    public void setPathDebugData(PathGridPoint start, PathGridPoint end, PathWorldPoint startWorld,
        PathWorldPoint endWorld, List<PathGridPoint> path, List<PathWorldPoint> worldPath,
        List<PathGridPoint> searched, List<PathWorldPoint> searchedWorlds) {
        paintModel.pathStartPoint = start;
        paintModel.pathEndPoint = end;
        paintModel.pathStartWorldPoint = startWorld;
        paintModel.pathEndWorldPoint = endWorld;
        paintModel.pathTiles = path == null ? new ArrayList<>() : path;
        paintModel.worldPathPoints = worldPath == null ? new ArrayList<>() : worldPath;
        paintModel.searchedTiles = searched == null ? new ArrayList<>() : searched;
        paintModel.searchedWorldPoints = searchedWorlds == null ? new ArrayList<>() : searchedWorlds;
        repaint();
    }

    /**
     * 更新对比路径数据，不影响当前主路径和搜索回放数据。
     *
     * @param path
     *            对比格子路径
     * @param worldPath
     *            对比世界坐标路径
     */
    public void setComparePathDebugData(List<PathGridPoint> path, List<PathWorldPoint> worldPath) {
        paintModel.comparePathTiles = path == null ? new ArrayList<>() : path;
        paintModel.compareWorldPathPoints = worldPath == null ? new ArrayList<>() : worldPath;
        repaint();
    }

    public void setBlockedTiles(List<PathGridPoint> blockedTiles) {
        paintModel.blockedTiles = blockedTiles == null ? new ArrayList<>() : blockedTiles;
        repaint();
    }

    public void setPathBlockReason(PathBlockReason blockReason) {
        paintModel.pathBlockReason = blockReason == null ? PathBlockReason.none() : blockReason;
        repaint();
    }

    public void setPathFailureDetail(PathFailureDetail failureDetail) {
        paintModel.pathFailureDetail = failureDetail == null ? PathFailureDetail.none() : failureDetail;
        repaint();
    }

    public void clearPathDebugData() {
        setPathDebugData(null, null, null, null, null, null, null);
        setComparePathDebugData(null, null);
        setPathBlockReason(null);
        setPathFailureDetail(null);
    }

    /**
     * 切换寻路调试图层显隐（搜索 / Tool / World）。
     */
    public void setPathLayerVisible(boolean showSearch, boolean showToolPath, boolean showWorldPath) {
        paintModel.showSearch = showSearch;
        paintModel.showToolPath = showToolPath;
        paintModel.showWorldPath = showWorldPath;
        repaint();
    }

    public void setSelectedLevelNode(LevelNodeBean node) {
        paintModel.selectedLevelNode = node;
        repaint();
    }

    public void setZoomScale(double zoomScale) {
        if (Math.abs(zoomScale - this.zoomScale) < 0.001D) {
            return;
        }
        this.zoomScale = Math.max(zoomScale, 1.0D);
        if (mapData != null && mapData.getMap() != null) {
            double maxZoom = resolveMaxZoomScale();
            if (this.zoomScale > maxZoom) {
                this.zoomScale = maxZoom;
            }
        }
        syncCanvasSizeToMap();
        repaint();
        fireViewportChanged();
    }

    public void zoomIn() {
        if (mapData == null || mapData.getMap() == null) {
            return;
        }
        double maxZoom = resolveMaxZoomScale();
        if (maxZoom <= 1.0D + 1e-6D) {
            return;
        }
        double stepFactor = Math.pow(maxZoom, 1.0D / (double)MapViewTransform.ZOOM_STEP_COUNT);
        setZoomScale(Math.min(zoomScale * stepFactor, maxZoom));
    }

    public void zoomOut() {
        if (mapData == null || mapData.getMap() == null) {
            return;
        }
        if (zoomScale <= 1.0D + 1e-6D) {
            return;
        }
        double maxZoom = resolveMaxZoomScale();
        double stepFactor = Math.pow(Math.max(maxZoom, 1.0D), 1.0D / (double)MapViewTransform.ZOOM_STEP_COUNT);
        setZoomScale(Math.max(zoomScale / stepFactor, 1.0D));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (mapData == null || mapData.getMap() == null) {
            g.drawString("请先选择地图文件", 20, 30);
            return;
        }
        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        MapViewContext ctx = buildViewContext();
        MapCanvasPainter.paintMapContent(g2, paintModel, ctx, getWidth(), getHeight());
    }

    public int[] pixelToWorld(int px, int py) {
        if (mapData == null) {
            return null;
        }
        MapViewContext ctx = buildViewContext();
        if (px < ctx.mapLeftX || py < ctx.mapTopY || px > ctx.mapRightX || py > ctx.mapBottomY) {
            return null;
        }
        double fx = (px - ctx.mapLeftX) / ctx.cellSize;
        double fz = (ctx.mapBottomY - py) / ctx.cellSize;
        int worldX = (int)Math.round(fx * MapRingUtil.WORLD_PIXEL_PER_CELL);
        int worldZ = (int)Math.round(fz * MapRingUtil.WORLD_PIXEL_PER_CELL);
        return new int[] {worldX, worldZ};
    }

    public boolean isWorldInPaintedLevel(int worldX, int worldZ) {
        if (mapData == null || mapData.getMap() == null) {
            return false;
        }
        int w = mapData.getWidth();
        int h = mapData.getHeight();
        int col = worldX / MapRingUtil.WORLD_PIXEL_PER_CELL;
        int row = worldZ / MapRingUtil.WORLD_PIXEL_PER_CELL;
        if (col < 0 || col >= w || row < 0 || row >= h) {
            return false;
        }
        return mapData.getMap()[col][row] != 0;
    }

    public LevelNodeBean getLevelNodeAt(int px, int py) {
        if (mapData == null) {
            return null;
        }
        MapViewContext ctx = buildViewContext();
        LevelNodeBean hit = null;
        double minDist2 = Double.MAX_VALUE;
        for (LevelNodeBean node : levelNodes) {
            double tx = (double)node.getX() / MapRingUtil.WORLD_PIXEL_PER_CELL;
            double tz = (double)node.getZ() / MapRingUtil.WORLD_PIXEL_PER_CELL;
            double cx = ctx.mapLeftX + tx * ctx.cellSize;
            double cy = ctx.mapBottomY - tz * ctx.cellSize;
            double dx = px - cx;
            double dy = py - cy;
            double hitPx = Math.max(NODE_HIT_RADIUS, ctx.cellSize * 0.12D);
            double dist2 = dx * dx + dy * dy;
            if (dist2 <= hitPx * hitPx && dist2 < minDist2) {
                minDist2 = dist2;
                hit = node;
            }
        }
        return hit;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Container p = getParent();
        if (p instanceof JViewport) {
            viewportResizeAdapter = new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    syncCanvasSizeToMap();
                }
            };
            p.addComponentListener(viewportResizeAdapter);
            syncCanvasSizeToMap();
        }
    }

    @Override
    public void removeNotify() {
        Container p = getParent();
        if (p instanceof JViewport && viewportResizeAdapter != null) {
            p.removeComponentListener(viewportResizeAdapter);
            viewportResizeAdapter = null;
        }
        super.removeNotify();
    }

    private MapViewContext buildViewContext() {
        Dimension vp = MapViewTransform.resolveViewportSize(this);
        return MapViewTransform.createContext(mapData.getWidth(), mapData.getHeight(), zoomScale, getHeight(), vp);
    }

    private double resolveMaxZoomScale() {
        Dimension vp = MapViewTransform.resolveViewportSize(this);
        return MapViewTransform.resolveMaxZoomScale(vp.width, vp.height, mapData.getWidth(), mapData.getHeight());
    }

    private void syncCanvasSizeToMap() {
        if (mapData == null || mapData.getMap() == null) {
            Dimension d = new Dimension(400, 300);
            Dimension cur = getPreferredSize();
            if (cur == null || cur.width != d.width || cur.height != d.height) {
                setPreferredSize(d);
                revalidate();
            }
            fireZoomStateChanged();
            return;
        }
        Dimension vp = MapViewTransform.resolveViewportSize(this);
        int cols = mapData.getWidth();
        int rows = mapData.getHeight();
        double cellSize = MapViewTransform.computeCellSize(vp.width, vp.height, cols, rows, zoomScale);
        int mapW = (int)Math.ceil(cols * cellSize);
        int mapH = (int)Math.ceil(rows * cellSize);
        int margin = MapViewTransform.MAP_VIEW_MARGIN_PX;
        // 画布至少铺满视口，避免视口宽于内容时出现右侧空白条带
        int prefW = Math.max(vp.width, mapW + margin * 2);
        int prefH = Math.max(vp.height, mapH + margin * 2);
        Dimension pref = new Dimension(prefW, prefH);
        Dimension cur = getPreferredSize();
        if (cur == null || cur.width != pref.width || cur.height != pref.height) {
            setPreferredSize(pref);
            if (getParent() != null) {
                revalidate();
            }
        }
        fireZoomStateChanged();
    }

    private void fireZoomStateChanged() {
        if (zoomStateListener != null) {
            zoomStateListener.run();
        }
    }

    private void fireViewportChanged() {
        if (viewportChangeListener != null) {
            viewportChangeListener.run();
        }
    }

    private void installPanAndClickHandler() {
        MouseInputAdapter panAdapter = new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                panPressCanvasX = e.getX();
                panPressCanvasY = e.getY();
                panGestureActive = false;
                panMovedBeyondThreshold = false;
                Container parent = getParent();
                if (parent instanceof JViewport) {
                    Point p = ((JViewport)parent).getViewPosition();
                    panStartViewX = p.x;
                    panStartViewY = p.y;
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) == 0) {
                    return;
                }
                int dx = e.getX() - panPressCanvasX;
                int dy = e.getY() - panPressCanvasY;
                if (dx * dx + dy * dy >= PAN_CLICK_THRESHOLD_PX * PAN_CLICK_THRESHOLD_PX) {
                    panMovedBeyondThreshold = true;
                }
                if (!panGestureActive) {
                    if (!panMovedBeyondThreshold) {
                        return;
                    }
                    panGestureActive = true;
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
                Container parent = getParent();
                if (!(parent instanceof JViewport)) {
                    return;
                }
                JViewport vp = (JViewport)parent;
                int nx = panStartViewX - (e.getX() - panPressCanvasX);
                int ny = panStartViewY - (e.getY() - panPressCanvasY);
                vp.setViewPosition(MapViewTransform.clampViewPosition(vp, nx, ny));
                fireViewportChanged();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                if (panGestureActive) {
                    setCursor(Cursor.getDefaultCursor());
                }
                if (!panGestureActive && !panMovedBeyondThreshold && mapClickListener != null) {
                    mapClickListener.onMapCanvasClicked(e.getX(), e.getY());
                }
                panGestureActive = false;
                panMovedBeyondThreshold = false;
            }
        };
        addMouseListener(panAdapter);
        addMouseMotionListener(panAdapter);
    }

    private void installPopupHandler() {
        MouseAdapter popupAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                tryFirePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                tryFirePopup(e);
            }

            private void tryFirePopup(MouseEvent e) {
                if (!e.isPopupTrigger() || !SwingUtilities.isRightMouseButton(e)) {
                    return;
                }
                if (mapPopupListener != null) {
                    mapPopupListener.onMapPopup(e.getX(), e.getY());
                }
            }
        };
        addMouseListener(popupAdapter);
    }
}
