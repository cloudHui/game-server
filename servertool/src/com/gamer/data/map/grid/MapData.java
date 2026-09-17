package com.gamer.data.map.grid;

/**
 * 地图数据，与 MapManager 解析结果一致，供地图范围查看器使用。 左下角为原点，map[col][row] 表示 (col, row)。
 */
public class MapData {

    private int[][] map;
    private int width;
    private int height;

    public int[][] getMap() {
        return map;
    }

    public void setMap(int[][] map) {
        this.map = map;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }
}
