package com.gamer.data.map.grid;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * 从 map/level 目录下的 txt 文件加载地图，解析格式与 MapManager 一致。 第一行：length:W, weight:H（第一个值为 width，第二个为 height）。 后续行：逗号分隔的整数，reverse
 * 后 map[col][lineIndex]，左下角为原点。
 */
public final class MapFileLoader {

    private static final String COMMA = ",";

    private MapFileLoader() {}

    /**
     * 加载单个地图文件
     *
     * @param file
     *            地图 txt 文件
     * @return 解析后的 MapData，失败返回 null
     */
    public static MapData load(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return null;
        }
        try {
            List<String> fileContent = Files.readAllLines(Paths.get(file.getAbsolutePath()), StandardCharsets.UTF_8);
            if (fileContent.size() < 2) {
                return null;
            }
            MapData data = new MapData();
            parseMapDimensions(fileContent.get(0), data);
            if (data.getMap() == null) {
                return null;
            }
            fillMapData(fileContent, data);
            return data;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 解析首行尺寸，格式 "length:10, weight:5" 或 "width:10, height:5"
     */
    private static void parseMapDimensions(String dimensionLine, MapData mapData) {
        String[] split = dimensionLine.split(COMMA);
        if (split.length < 2) {
            return;
        }
        try {
            int width = Integer.parseInt(split[0].split(":")[1].trim());
            int height = Integer.parseInt(split[1].split(":")[1].trim());
            mapData.setWidth(width);
            mapData.setHeight(height);
            mapData.setMap(new int[width][height]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 与 MapManager.fillMapData 一致：reverse 后从第二行开始填 map 和 levelMovablePointSetCache
     */
    private static void fillMapData(List<String> fileContent, MapData mapData) {
        int[][] map = mapData.getMap();
        if (map == null) {
            return;
        }
        Collections.reverse(fileContent);
        int maxRows = map[0].length;
        for (int lineIndex = 0; lineIndex < fileContent.size() - 1 && lineIndex < maxRows; lineIndex++) {
            String line = fileContent.get(lineIndex);
            String[] rowData = line.split(COMMA);
            for (int colIndex = 0; colIndex < rowData.length && colIndex < map.length; colIndex++) {
                try {
                    int level = Integer.parseInt(rowData[colIndex].trim());
                    if (lineIndex < map[0].length) {
                        map[colIndex][lineIndex] = level;
                    }
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
