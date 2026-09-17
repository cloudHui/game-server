package com.gamer.data.map.ui.canvas;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.border.LineBorder;
import javax.swing.event.ChangeListener;
import javax.swing.event.MouseInputAdapter;

import com.gamer.data.map.grid.MapData;

/**
 * 主地图右上角小地图：缩略图缓存绘制，视口框随滚动/缩放刷新；点击将主视口中心移到对应位置。
 */
public class MapMiniMapPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static final int PANEL_WIDTH_PX = 200;
    private static final int TITLE_HEIGHT_PX = 16;
    private static final int THUMB_AREA_HEIGHT_PX = 140;
    private static final Color PANEL_BG = Color.WHITE;
    private static final Color PANEL_BORDER = new Color(80, 80, 80);
    private static final Color VIEWPORT_RECT_STROKE = new Color(220, 40, 40);

    private MapViewerCanvas canvas;
    private JScrollPane mapScroll;
    private BufferedImage thumbnail;
    private double thumbMapLeftX;
    private double thumbMapTopY;
    private int thumbMapWidthPx;
    private int thumbMapHeightPx;
    private int cachedMapCols;
    private int cachedMapRows;
    private int cachedThumbAreaH;
    private int cachedThumbAreaW;
    private MiniMapNavigateListener navigateListener;

    private final ChangeListener viewportChangeListener = e -> repaint();

    /**
     * 小地图导航完成回调。
     */
    public interface MiniMapNavigateListener {

        void onMiniMapNavigated(int canvasX, int canvasY);
    }

    public MapMiniMapPanel() {
        setOpaque(true);
        setBackground(PANEL_BG);
        setBorder(new LineBorder(PANEL_BORDER, 1));
        Insets borderIns = getInsets();
        int outerH = borderIns.top + TITLE_HEIGHT_PX + THUMB_AREA_HEIGHT_PX + borderIns.bottom;
        setPreferredSize(new Dimension(PANEL_WIDTH_PX, outerH));
        setMinimumSize(getPreferredSize());
        installClickHandler();
    }

    public void setNavigateListener(MiniMapNavigateListener listener) {
        this.navigateListener = listener;
    }

    public void bind(MapViewerCanvas canvas, JScrollPane scroll) {
        this.canvas = canvas;
        this.mapScroll = scroll;
        if (mapScroll != null) {
            mapScroll.getViewport().addChangeListener(viewportChangeListener);
        }
        if (canvas != null) {
            canvas.setViewportChangeListener(this::repaint);
        }
        invalidateThumbnail();
    }

    public void onMapChanged() {
        invalidateThumbnail();
    }

    public void onMainViewChanged() {
        repaint();
    }

    @Override
    protected void processMouseEvent(MouseEvent e) {
        e.consume();
        super.processMouseEvent(e);
    }

    @Override
    protected void processMouseMotionEvent(MouseEvent e) {
        e.consume();
        super.processMouseMotionEvent(e);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Insets ins = getInsets();
        Graphics2D g2 = (Graphics2D)g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Color.BLACK);
            g2.drawString("小地图", ins.left + 4, ins.top + 12);
            if (canvas == null) {
                return;
            }
            ensureThumbnail();
            if (thumbnail == null) {
                return;
            }
            Rectangle thumbArea = resolveThumbContentBounds();
            g2.drawImage(thumbnail, thumbArea.x, thumbArea.y, thumbArea.width, thumbArea.height, null);
            if (shouldDrawViewportRect()) {
                drawViewportRect(g2, thumbArea.x, thumbArea.y);
            }
        } finally {
            g2.dispose();
        }
    }

    private void installClickHandler() {
        MouseInputAdapter clickAdapter = new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) {
                    handleMiniMapClick(e.getX(), e.getY());
                }
            }
        };
        addMouseListener(clickAdapter);
    }

    private void invalidateThumbnail() {
        thumbnail = null;
        cachedMapCols = 0;
        cachedMapRows = 0;
        cachedThumbAreaW = 0;
        cachedThumbAreaH = 0;
        repaint();
    }

    private Rectangle resolveThumbContentBounds() {
        Insets ins = getInsets();
        int x = ins.left;
        int y = ins.top + TITLE_HEIGHT_PX;
        int w = getWidth() - ins.left - ins.right;
        int h = getHeight() - y - ins.bottom;
        if (w <= 0) {
            w = PANEL_WIDTH_PX - ins.left - ins.right;
        }
        if (h <= 0) {
            h = THUMB_AREA_HEIGHT_PX;
        }
        return new Rectangle(x, y, w, h);
    }

    private void ensureThumbnail() {
        MapData data = canvas.getMapData();
        if (data == null || data.getMap() == null) {
            thumbnail = null;
            return;
        }
        int cols = data.getWidth();
        int rows = data.getHeight();
        Rectangle thumbArea = resolveThumbContentBounds();
        if (thumbnail != null && cols == cachedMapCols && rows == cachedMapRows
            && thumbArea.width == cachedThumbAreaW && thumbArea.height == cachedThumbAreaH) {
            return;
        }
        rebuildThumbnail(data, cols, rows, thumbArea.width, thumbArea.height);
    }

    private int resolveThumbOriginY() {
        return resolveThumbContentBounds().y;
    }

    private boolean shouldDrawViewportRect() {
        if (mapScroll == null || canvas == null) {
            return false;
        }
        JViewport vp = mapScroll.getViewport();
        Dimension ext = vp.getExtentSize();
        int cw = canvas.getWidth();
        int ch = canvas.getHeight();
        return ext.width < cw - 1 || ext.height < ch - 1;
    }

    private void drawViewportRect(Graphics2D g2, int offsetX, int offsetY) {
        Rectangle miniRect = computeViewportRectOnThumb();
        if (miniRect == null || miniRect.width <= 0 || miniRect.height <= 0) {
            return;
        }
        g2.setColor(VIEWPORT_RECT_STROKE);
        g2.setStroke(new BasicStroke(2F));
        g2.drawRect(offsetX + miniRect.x, offsetY + miniRect.y, miniRect.width, miniRect.height);
    }

    private Rectangle computeViewportRectOnThumb() {
        Rectangle mapRect = canvas.getMapContentRect();
        if (mapRect == null || mapRect.width <= 0 || mapRect.height <= 0 || mapScroll == null) {
            return null;
        }
        JViewport vp = mapScroll.getViewport();
        Point viewPos = vp.getViewPosition();
        Dimension ext = vp.getExtentSize();
        int visLeft = Math.max(viewPos.x, mapRect.x);
        int visTop = Math.max(viewPos.y, mapRect.y);
        int visRight = Math.min(viewPos.x + ext.width, mapRect.x + mapRect.width);
        int visBottom = Math.min(viewPos.y + ext.height, mapRect.y + mapRect.height);
        if (visRight <= visLeft || visBottom <= visTop) {
            return null;
        }
        double scale = thumbMapWidthPx / (double)mapRect.width;
        int mx = (int)Math.round(thumbMapLeftX + (visLeft - mapRect.x) * scale);
        int my = (int)Math.round(thumbMapTopY + (visTop - mapRect.y) * scale);
        int mw = Math.max(1, (int)Math.round((visRight - visLeft) * scale));
        int mh = Math.max(1, (int)Math.round((visBottom - visTop) * scale));
        return new Rectangle(mx, my, mw, mh);
    }

    private void handleMiniMapClick(int px, int py) {
        if (canvas == null || thumbnail == null) {
            return;
        }
        Insets ins = getInsets();
        int cx = px - ins.left;
        int cy = py - resolveThumbOriginY();
        Rectangle mapRect = canvas.getMapContentRect();
        Rectangle thumbArea = resolveThumbContentBounds();
        if (mapRect == null || thumbMapWidthPx <= 0) {
            return;
        }
        double scale = thumbMapWidthPx / (double)mapRect.width;
        double mapRelX = (cx - thumbMapLeftX) / scale;
        double mapRelY = (cy - thumbMapTopY) / scale;
        if (mapRelX < 0D || mapRelY < 0D || mapRelX > mapRect.width || mapRelY > mapRect.height) {
            return;
        }
        if (cx < 0 || cy < 0 || cx > thumbArea.width || cy > thumbArea.height) {
            return;
        }
        int canvasX = mapRect.x + (int)Math.round(mapRelX);
        int canvasY = mapRect.y + (int)Math.round(mapRelY);
        canvas.scrollViewToCanvasCenter(canvasX, canvasY);
        repaint();
        if (navigateListener != null) {
            navigateListener.onMiniMapNavigated(canvasX, canvasY);
        }
    }

    private void rebuildThumbnail(MapData data, int cols, int rows, int innerW, int innerH) {
        if (innerW <= 0 || innerH <= 0) {
            return;
        }
        double cellSize = Math.min((double)innerW / cols, (double)innerH / rows);
        if (cellSize <= 0D) {
            return;
        }
        thumbMapWidthPx = (int)Math.round(cols * cellSize);
        thumbMapHeightPx = (int)Math.round(rows * cellSize);
        thumbMapLeftX = (innerW - thumbMapWidthPx) / 2.0D;
        thumbMapTopY = (innerH - thumbMapHeightPx) / 2.0D;

        BufferedImage img = new BufferedImage(innerW, innerH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        try {
            g2.setColor(PANEL_BG);
            g2.fillRect(0, 0, innerW, innerH);
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            paintMapCells(g2, data, cols, rows, cellSize, innerW, innerH);
        } finally {
            g2.dispose();
        }
        thumbnail = img;
        cachedMapCols = cols;
        cachedMapRows = rows;
        cachedThumbAreaW = innerW;
        cachedThumbAreaH = innerH;
    }

    private void paintMapCells(Graphics2D g2, MapData data, int cols, int rows, double cellSize, int innerW,
        int innerH) {
        int[][] map = data.getMap();
        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                int val = map[col][row];
                if (val == 0) {
                    continue;
                }
                g2.setColor(MapViewerCanvas.resolveCellDisplayColor(val));
                double x0 = thumbMapLeftX + col * cellSize;
                double y0 = thumbMapTopY + (rows - 1 - row) * cellSize;
                double x1 = thumbMapLeftX + (col + 1) * cellSize;
                double y1 = thumbMapTopY + (rows - row) * cellSize;
                int ix0 = Math.max(0, Math.min(innerW, (int)Math.floor(x0)));
                int iy0 = Math.max(0, Math.min(innerH, (int)Math.floor(y0)));
                int ix1 = Math.max(0, Math.min(innerW, (int)Math.ceil(x1)));
                int iy1 = Math.max(0, Math.min(innerH, (int)Math.ceil(y1)));
                if (ix1 > ix0 && iy1 > iy0) {
                    g2.fillRect(ix0, iy0, ix1 - ix0, iy1 - iy0);
                }
            }
        }
    }

    /**
     * 小地图叠层容器：拦截全部鼠标事件，防止穿透到主地图。
     */
    public static final class MapMiniMapOverlayHost extends JPanel {

        private static final long serialVersionUID = 1L;

        public MapMiniMapOverlayHost(LayoutManager layout) {
            super(layout);
            setOpaque(true);
            setBackground(Color.WHITE);
        }

        @Override
        protected void processMouseEvent(MouseEvent e) {
            e.consume();
            super.processMouseEvent(e);
        }

        @Override
        protected void processMouseMotionEvent(MouseEvent e) {
            e.consume();
            super.processMouseMotionEvent(e);
        }
    }
}
