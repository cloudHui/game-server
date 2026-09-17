package com.gamer.data.map.ui.canvas;

/**
 * 地图画布渲染上下文：封装布局参数，避免重复计算。
 */
public final class MapViewContext {

    /** 单格边长（像素） */
    public final double cellSize;

    /** 地图绘制区左边界 X */
    public final double mapLeftX;

    /** 地图绘制区上边界 Y */
    public final double mapTopY;

    /** 地图绘制区右边界 X */
    public final double mapRightX;

    /** 地图绘制区下边界 Y */
    public final double mapBottomY;

    /** 地图列数 */
    public final int cols;

    /** 地图行数 */
    public final int rows;

    MapViewContext(double cellSize, double mapLeftX, double mapTopY, double mapRightX, double mapBottomY, int cols,
        int rows) {
        this.cellSize = cellSize;
        this.mapLeftX = mapLeftX;
        this.mapTopY = mapTopY;
        this.mapRightX = mapRightX;
        this.mapBottomY = mapBottomY;
        this.cols = cols;
        this.rows = rows;
    }
}
