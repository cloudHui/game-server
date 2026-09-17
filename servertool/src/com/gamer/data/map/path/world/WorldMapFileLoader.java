package com.gamer.data.map.path.world;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * 加载 WorldServer 地图 txt 格式，不反转文件行。
 */
public final class WorldMapFileLoader {

    /** 地图 txt 列分隔符。 */
    private static final String COMMA = ",";

    private WorldMapFileLoader() {}

    /**
     * 加载 WorldServer 地图文件。
     *
     * @param file
     *            地图 txt 文件，例如 Map_Chapter_1.txt
     * @return 解析后的地图数据
     * @throws IOException
     *             当文件无法读取或解析时
     */
    public static WorldMapData load(File file) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IOException("world map file not found");
        }
        List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            throw new IOException("world map file is empty: " + file.getName());
        }
        int[] size = parseSize(lines.get(0));
        WorldMapData mapData = new WorldMapData(size[0], size[1]);
        fillMapData(lines, mapData);
        return mapData;
    }

    /**
     * 解析 "Width: W, Height: H" 或兼容的头部。
     */
    private static int[] parseSize(String header) throws IOException {
        String[] parts = header.split(COMMA);
        if (parts.length < 2) {
            throw new IOException("invalid world map header: " + header);
        }
        try {
            int width = parseNumberAfterColon(parts[0]);
            int height = parseNumberAfterColon(parts[1]);
            if (width <= 0 || height <= 0) {
                throw new IOException("invalid world map size: " + header);
            }
            return new int[] {width, height};
        } catch (NumberFormatException e) {
            throw new IOException("invalid world map header: " + header, e);
        }
    }

    /**
     * 解析冒号后的数字。
     *
     * @param text
     *            文本
     * @return 数字
     */
    private static int parseNumberAfterColon(String text) {
        int colon = text.indexOf(':');
        return Integer.parseInt(text.substring(colon + 1).trim());
    }

    /**
     * 填充地图数据，与 WorldMapManager 使用的行顺序相同。
     */
    private static void fillMapData(List<String> lines, WorldMapData mapData) throws IOException {
        int height = mapData.getHeight();
        int width = mapData.getWidth();
        for (int y = 0; y < height; y++) {
            int lineIndex = y + 1;
            if (lineIndex >= lines.size()) {
                break;
            }
            String[] rowData = lines.get(lineIndex).split(COMMA);
            for (int x = 0; x < rowData.length && x < width; x++) {
                try {
                    int value = Integer.parseInt(rowData[x].trim());
                    mapData.setWalkable(x, y, value != 0);
                } catch (NumberFormatException e) {
                    throw new IOException("invalid world map cell at x=" + x + ", y=" + y, e);
                }
            }
        }
    }
}
