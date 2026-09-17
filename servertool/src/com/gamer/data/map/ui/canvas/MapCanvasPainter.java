package com.gamer.data.map.ui.canvas;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

import com.gamer.data.map.grid.MapRingUtil;
import com.gamer.data.map.level.LevelNodeBean;
import com.gamer.data.map.level.NodeType;
import com.gamer.data.map.path.common.PathGridPoint;
import com.gamer.data.map.path.common.PathBlockReason;
import com.gamer.data.map.path.common.PathFailureDetail;
import com.gamer.data.map.path.common.PathWorldPoint;

/**
 * 地图画布分层绘制逻辑。
 */
public final class MapCanvasPainter {

    private static final Color COLOR_GRID = new Color(180, 180, 180);
    private static final Color COLOR_MARKER = Color.RED;
    private static final Color COLOR_DISTANCE_MARKER_A = new Color(0, 120, 255);
    private static final Color COLOR_DISTANCE_MARKER_B = new Color(255, 140, 0);
    private static final Color COLOR_DISTANCE_LINE = new Color(80, 80, 80, 180);
    private static final Color COLOR_SELECTED_NODE = new Color(255, 80, 80);
    private static final int SELECTED_NODE_LABEL_OFFSET_X_PX = 36;
    private static final int SELECTED_NODE_LABEL_OFFSET_Y_PX = -28;
    private static final int SELECTED_NODE_LABEL_PADDING_PX = 4;
    private static final Color COLOR_SELECTED_NODE_LABEL_BG = new Color(255, 248, 220, 230);
    private static final Color COLOR_SELECTED_NODE_LABEL_BORDER = new Color(160, 120, 40);
    private static final Color COLOR_PATH_TILE = new Color(30, 160, 220, 55);
    /** 搜索展开格：淡黄半透明，避免压住路径 */
    private static final Color COLOR_SEARCH_TILE = new Color(255, 220, 60, 90);
    /** 细坐标搜索点：同属搜索层，用偏黄区分 World 橙路径 */
    private static final Color COLOR_SEARCH_WORLD = new Color(255, 190, 40, 110);
    private static final Color COLOR_PATH_LINE = new Color(0, 140, 220, 230);
    private static final Color COLOR_COMPARE_PATH_TILE = new Color(255, 140, 30, 50);
    private static final Color COLOR_COMPARE_PATH_LINE = new Color(245, 110, 10, 230);
    private static final Color COLOR_PATH_START = new Color(0, 140, 220);
    private static final Color COLOR_PATH_END = new Color(220, 40, 40);
    private static final Color COLOR_OBSTACLE_TILE = new Color(230, 40, 40, 95);
    private static final Color COLOR_BLOCK_REASON_TILE = new Color(255, 0, 0, 190);
    private static final Color COLOR_BLOCK_REASON_RADIUS = new Color(255, 0, 0, 80);
    private static final Color COLOR_FAILURE_STUCK = new Color(255, 190, 0);

    private MapCanvasPainter() {
    }

    /**
     * 供小地图缩略图复用格子配色。
     */
    public static Color resolveCellDisplayColor(int val) {
        return val <= 0 ? Color.GRAY : MapCanvasPaintModel.CELL_COLORS[(val - 1) % MapCanvasPaintModel.CELL_COLORS.length];
    }

    /**
     * 绘制完整地图内容（不含 super.paintComponent 背景）。
     */
    public static void paintMapContent(Graphics2D g2, MapCanvasPaintModel model, MapViewContext ctx, int canvasWidth,
        int canvasHeight) {
        int[][] map = model.mapData.getMap();
        for (int col = 0; col < ctx.cols; col++) {
            for (int row = 0; row < ctx.rows; row++) {
                int val = map[col][row];
                if (val != 0) {
                    g2.setColor(resolveCellDisplayColor(val));
                    fillTile(g2, col, row, ctx);
                }
            }
        }

        g2.setColor(COLOR_GRID);
        for (int i = 0; i <= ctx.cols; i++) {
            int x = (int)(ctx.mapLeftX + i * ctx.cellSize);
            g2.drawLine(x, (int)ctx.mapTopY, x, (int)ctx.mapBottomY);
        }
        for (int i = 0; i <= ctx.rows; i++) {
            int y = (int)(ctx.mapTopY + i * ctx.cellSize);
            g2.drawLine((int)ctx.mapLeftX, y, (int)ctx.mapRightX, y);
        }

        drawPathDebugLayers(g2, model, ctx);

        if (model.levelNodes != null) {
            for (LevelNodeBean node : model.levelNodes) {
                drawCircleAtWorld(g2, node.getX(), node.getZ(), ctx, NodeType.getColor(node.getType()), 8);
            }
        }

        if (model.selectedLevelNode != null) {
            drawSelectedNodeLabel(g2, ctx, model.selectedLevelNode, canvasWidth, canvasHeight);
            drawCircleAtWorld(g2, model.selectedLevelNode.getX(), model.selectedLevelNode.getZ(), ctx,
                COLOR_SELECTED_NODE, 3);
        }

        if (model.markerWorldX != MapCanvasPaintModel.MARKER_NONE) {
            drawCircleAtWorld(g2, model.markerWorldX, model.markerWorldZ, ctx, COLOR_MARKER, 3);
        }

        if (model.distanceMarker1WorldX != MapCanvasPaintModel.MARKER_NONE) {
            drawCircleAtWorld(g2, model.distanceMarker1WorldX, model.distanceMarker1WorldZ, ctx,
                COLOR_DISTANCE_MARKER_A, 4);
        }
        if (model.distanceMarker2WorldX != MapCanvasPaintModel.MARKER_NONE) {
            drawCircleAtWorld(g2, model.distanceMarker2WorldX, model.distanceMarker2WorldZ, ctx,
                COLOR_DISTANCE_MARKER_B, 4);
        }
        if (model.distanceMarker1WorldX != MapCanvasPaintModel.MARKER_NONE
            && model.distanceMarker2WorldX != MapCanvasPaintModel.MARKER_NONE) {
            int[] p1 = MapViewTransform.worldToPixel(model.distanceMarker1WorldX, model.distanceMarker1WorldZ, ctx);
            int[] p2 = MapViewTransform.worldToPixel(model.distanceMarker2WorldX, model.distanceMarker2WorldZ, ctx);
            g2.setColor(COLOR_DISTANCE_LINE);
            g2.drawLine(p1[0], p1[1], p2[0], p2[1]);
        }

        drawAxisLabels(g2, ctx);
    }

    /**
     * 绘制路径调试层（搜索 → Tool → World，上层更透以免互相盖死）。
     */
    private static void drawPathDebugLayers(Graphics2D g2, MapCanvasPaintModel model, MapViewContext ctx) {
        drawPathTiles(g2, model.blockedTiles, ctx, COLOR_OBSTACLE_TILE);
        if (model.showSearch) {
            drawPathTiles(g2, model.searchedTiles, ctx, COLOR_SEARCH_TILE);
            drawWorldSearchPoints(g2, model.searchedWorldPoints, ctx);
        }
        if (model.showToolPath) {
            drawPathTiles(g2, model.pathTiles, ctx, COLOR_PATH_TILE);
            drawPathLine(g2, model.worldPathPoints, ctx, COLOR_PATH_LINE, 2.2F, false);
        }
        if (model.showWorldPath) {
            drawPathTiles(g2, model.comparePathTiles, ctx, COLOR_COMPARE_PATH_TILE);
            drawPathLine(g2, model.compareWorldPathPoints, ctx, COLOR_COMPARE_PATH_LINE, 2.8F, true);
        }
        drawBlockReason(g2, model.pathBlockReason, ctx);
        drawFailureDetail(g2, model.pathFailureDetail, ctx);
        drawPathEndpoint(g2, ctx, model.pathStartWorldPoint, COLOR_PATH_START, "S");
        drawPathEndpoint(g2, ctx, model.pathEndWorldPoint, COLOR_PATH_END, "E");
    }

    private static void drawFailureDetail(Graphics2D g2, PathFailureDetail detail, MapViewContext ctx) {
        if (detail == null || !detail.isPresent()) {
            return;
        }
        for (PathBlockReason reason : detail.getBlockReasons()) {
            drawBlockReason(g2, reason, ctx);
        }
        PathWorldPoint stuckWorld = detail.getStuckWorld();
        if (stuckWorld != null) {
            drawPathEndpoint(g2, ctx, stuckWorld, COLOR_FAILURE_STUCK, "卡");
        } else if (detail.getStuckGrid() != null) {
            PathGridPoint stuckGrid = detail.getStuckGrid();
            drawPathEndpoint(g2, ctx, new PathWorldPoint(stuckGrid.getWorldX(), stuckGrid.getWorldZ()),
                COLOR_FAILURE_STUCK, "卡");
        }
    }

    private static void drawBlockReason(Graphics2D g2, PathBlockReason reason, MapViewContext ctx) {
        if (reason == null || !reason.isPresent()) {
            return;
        }
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(new BasicStroke(2.0F));
        if (reason.getCol() >= 0 && reason.getRow() >= 0) {
            int x = (int)(ctx.mapLeftX + reason.getCol() * ctx.cellSize);
            int y = (int)(ctx.mapTopY + (ctx.rows - 1 - reason.getRow()) * ctx.cellSize);
            g2.setColor(COLOR_BLOCK_REASON_TILE);
            g2.drawRect(x, y, (int)ctx.cellSize + 1, (int)ctx.cellSize + 1);
        }
        if (reason.getWorldX() >= 0 && reason.getWorldZ() >= 0) {
            drawCircleAtWorld(g2, reason.getWorldX(), reason.getWorldZ(), ctx, COLOR_BLOCK_REASON_TILE, 10);
        }
        if (reason.getObstacle() != null) {
            drawWorldRadius(g2, reason.getObstacle().getNode().getX(), reason.getObstacle().getNode().getZ(),
                reason.getRadius(), ctx, COLOR_BLOCK_REASON_RADIUS, true);
            drawWorldRadius(g2, reason.getObstacle().getNode().getX(), reason.getObstacle().getNode().getZ(),
                reason.getRadius(), ctx, COLOR_BLOCK_REASON_TILE, false);
        }
        g2.setStroke(oldStroke);
    }

    /**
     * 绘制路径方块。
     */
    private static void drawPathTiles(Graphics2D g2, List<PathGridPoint> tiles, MapViewContext ctx, Color color) {
        if (tiles == null || tiles.isEmpty()) {
            return;
        }
        g2.setColor(color);
        for (PathGridPoint point : tiles) {
            fillTile(g2, point.getCol(), point.getRow(), ctx);
        }
    }

    /**
     * 绘制细坐标世界搜索点（每个点画成一个小方块）。
     *
     * @param g2     画布
     * @param points 世界坐标搜索点列表
     * @param ctx    视图上下文（缩放/偏移）
     */
    private static void drawWorldSearchPoints(Graphics2D g2, List<PathWorldPoint> points, MapViewContext ctx) {
        if (points == null || points.isEmpty()) {
            return;
        }
        // 方块边长随缩放联动，并夹在 [2,5] 像素，避免过小看不见或过大糊成一片
        int size = (int)Math.round(ctx.cellSize / MapRingUtil.WORLD_PIXEL_PER_CELL * 2D);
        if (size < 2) {
            size = 2;
        } else if (size > 5) {
            size = 5;
        }
        int half = size / 2;
        g2.setColor(MapCanvasPainter.COLOR_SEARCH_WORLD);
        // 逐点将世界坐标转换为屏幕像素并居中绘制小方块
        for (PathWorldPoint point : points) {
            int[] p = MapViewTransform.worldToPixel(point.getWorldX(), point.getWorldZ(), ctx);
            g2.fillRect(p[0] - half, p[1] - half, size, size);
        }
    }

    /**
     * 绘制路径线；World 对比路径用虚线，便于和 Tool 实线区分。
     */
    private static void drawPathLine(Graphics2D g2, List<PathWorldPoint> worldPathPoints, MapViewContext ctx,
        Color color, float strokeWidth, boolean dashed) {
        if (worldPathPoints == null || worldPathPoints.size() < 2) {
            return;
        }
        Stroke oldStroke = g2.getStroke();
        g2.setStroke(dashed
            ? new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, new float[] {8f, 6f}, 0f)
            : new BasicStroke(strokeWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(color);
        for (int i = 1; i < worldPathPoints.size(); i++) {
            PathWorldPoint prev = worldPathPoints.get(i - 1);
            PathWorldPoint cur = worldPathPoints.get(i);
            int[] p1 = MapViewTransform.worldToPixel(prev.getWorldX(), prev.getWorldZ(), ctx);
            int[] p2 = MapViewTransform.worldToPixel(cur.getWorldX(), cur.getWorldZ(), ctx);
            g2.drawLine(p1[0], p1[1], p2[0], p2[1]);
        }
        g2.setStroke(oldStroke);
    }

    /**
     * 绘制路径端点。
     */
    private static void drawPathEndpoint(Graphics2D g2, MapViewContext ctx, PathWorldPoint point, Color color,
        String text) {
        if (point == null) {
            return;
        }
        int[] p = MapViewTransform.worldToPixel(point.getWorldX(), point.getWorldZ(), ctx);
        drawCircle(g2, p[0], p[1], color, 12);
        g2.setColor(Color.WHITE);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(text, p[0] - fm.stringWidth(text) / 2, p[1] + fm.getAscent() / 2 - 1);
    }

    /**
     * 绘制世界半径。
     */
    private static void drawWorldRadius(Graphics2D g2, int worldX, int worldZ, int radius, MapViewContext ctx,
        Color color, boolean fill) {
        int[] center = MapViewTransform.worldToPixel(worldX, worldZ, ctx);
        int drawRadius = (int)Math.max(2D, Math.round((double)radius / MapRingUtil.WORLD_PIXEL_PER_CELL * ctx.cellSize));
        g2.setColor(color);
        if (fill) {
            g2.fillOval(center[0] - drawRadius, center[1] - drawRadius, drawRadius * 2, drawRadius * 2);
        } else {
            g2.drawOval(center[0] - drawRadius, center[1] - drawRadius, drawRadius * 2, drawRadius * 2);
        }
    }

    /**
     * 绘制选中节点标签。
     */
    private static void drawSelectedNodeLabel(Graphics2D g2, MapViewContext ctx, LevelNodeBean node, int canvasWidth,
        int canvasHeight) {
        int[] tipPt = MapViewTransform.worldToPixel(node.getX(), node.getZ(), ctx);
        int tipX = tipPt[0];
        int tipY = tipPt[1];

        String label = node.toString();
        if (label == null) {
            label = "";
        }
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(label);
        int th = fm.getHeight();
        int boxW = tw + SELECTED_NODE_LABEL_PADDING_PX * 2;
        int boxH = th + SELECTED_NODE_LABEL_PADDING_PX * 2;
        int boxCx = tipX + SELECTED_NODE_LABEL_OFFSET_X_PX;
        int boxCy = tipY + SELECTED_NODE_LABEL_OFFSET_Y_PX;
        int boxX = boxCx - boxW / 2;
        int boxY = boxCy - boxH / 2;

        if (boxX < 2) {
            boxX = 2;
        }
        if (boxY < 2) {
            boxY = 2;
        }
        if (boxX + boxW > canvasWidth - 2) {
            boxX = canvasWidth - 2 - boxW;
        }
        if (boxY + boxH > canvasHeight - 2) {
            boxY = canvasHeight - 2 - boxH;
        }

        g2.setColor(COLOR_SELECTED_NODE_LABEL_BG);
        g2.fill(new RoundRectangle2D.Double(boxX, boxY, boxW, boxH, 6.0D, 6.0D));
        g2.setColor(COLOR_SELECTED_NODE_LABEL_BORDER);
        g2.draw(new RoundRectangle2D.Double(boxX, boxY, boxW, boxH, 6.0D, 6.0D));
        g2.setColor(Color.BLACK);
        int textBaselineY = boxY + SELECTED_NODE_LABEL_PADDING_PX + fm.getAscent();
        g2.drawString(label, boxX + SELECTED_NODE_LABEL_PADDING_PX, textBaselineY);
    }

    /**
     * 填充方块。
     */
    private static void fillTile(Graphics2D g2, int col, int row, MapViewContext ctx) {
        int x = (int)(ctx.mapLeftX + col * ctx.cellSize);
        int y = (int)(ctx.mapTopY + (ctx.rows - 1 - row) * ctx.cellSize);
        g2.fillRect(x, y, (int)ctx.cellSize + 1, (int)ctx.cellSize + 1);
    }

    /**
     * 绘制世界圆。
     */
    private static void drawCircleAtWorld(Graphics2D g2, int worldX, int worldZ, MapViewContext ctx, Color color,
        int diam) {
        int[] p = MapViewTransform.worldToPixel(worldX, worldZ, ctx);
        drawCircle(g2, p[0], p[1], color, diam);
    }

    /**
     * 绘制圆。
     */
    private static void drawCircle(Graphics2D g2, int cx, int cy, Color color, int diam) {
        int r = diam / 2;
        g2.setColor(color);
        g2.fillOval(cx - r, cy - r, diam, diam);
        g2.setColor(Color.BLACK);
        g2.drawOval(cx - r, cy - r, diam, diam);
    }

    /**
     * 绘制轴标签。
     */
    private static void drawAxisLabels(Graphics2D g2, MapViewContext ctx) {
        g2.setColor(Color.BLACK);
        int baseY = (int)ctx.mapBottomY;
        g2.drawString("原点(左下) (0,0)", (int)ctx.mapLeftX, baseY + 15);
        g2.drawString("X→", (int)ctx.mapRightX - 15, baseY + 15);
        g2.drawString("Y↑", (int)ctx.mapLeftX - 18, (int)ctx.mapTopY + 12);
    }
}
