package com.gamer.data.map.chapter;

import java.io.File;

import com.gamer.data.read.GdData;
import com.gamer.data.read.GdFileReader;

/**
 * 切地图时读 LevelChapter.gd，查出本章 ChapterOffset。
 * <p>
 * 只按当前地图文件名对 ChapterMap（例如 Map_Chapter_2）。 同一张地图被多章共用时，取最小 ChapterId（2 优先于 995），避免误用大偏移把节点减出地图。
 * </p>
 */
public final class ChapterOffsetLoader {

    /** 章节表文件名 */
    private static final String CHAPTER_GD = "LevelChapter.gd";

    private ChapterOffsetLoader() {}

    /**
     * 按当前地图文件名读偏移；失败返回零偏移并带原因说明。
     *
     * @param gdDir
     *            GD/data 目录
     * @param mapFileName
     *            如 Map_Chapter_2.txt
     * @return 偏移
     */
    public static ChapterOffset load(File gdDir, String mapFileName) {
        // 未传入 GD 目录
        if (gdDir == null) {
            return new ChapterOffset(0, 0, -1, "无GD目录 偏移 0,0");
        }
        // 目录不存在
        if (!gdDir.isDirectory()) {
            return new ChapterOffset(0, 0, -1, "GD不存在:" + gdDir.getAbsolutePath());
        }

        try {
            // 打开 LevelChapter.gd
            File chapterFile = new File(gdDir, CHAPTER_GD);
            if (!chapterFile.isFile()) {
                return new ChapterOffset(0, 0, -1, "无LevelChapter.gd");
            }
            GdData chapterGd = GdFileReader.readGdFile(chapterFile);

            // 用地图名找章；同名多章取最小 id
            int chapterId = findChapterIdByMapFile(chapterGd, mapFileName);
            if (chapterId <= 0) {
                return new ChapterOffset(0, 0, -1, "未匹配章 偏移 0,0");
            }

            // 读该章 ChapterOffset
            return readOffset(chapterGd, chapterId);
        } catch (Exception e) {
            // 读失败不挡地图
            e.printStackTrace();
            return new ChapterOffset(0, 0, -1, "读GD失败");
        }
    }

    /**
     * ChapterMap == 地图名（无 .txt）时，在所有命中行里取最小 ChapterId。
     *
     * @param chapterGd
     *            章节表
     * @param mapFileName
     *            地图文件名
     * @return 章 id；没有返回 0
     */
    private static int findChapterIdByMapFile(GdData chapterGd, String mapFileName) {
        // 去掉 .txt
        String mapName = stripTxt(mapFileName);
        if (mapName == null) {
            return 0;
        }

        int mapCol = col(chapterGd, "ChapterMap");
        int idCol = col(chapterGd, "ChapterId");
        if (mapCol < 0 || idCol < 0) {
            return 0;
        }

        // 扫全表，同名取最小章 id（Map_Chapter_2 → 2 而不是 995）
        int bestId = 0;
        for (Object[] row : chapterGd.dataRows) {
            if (!mapName.equals(text(row[mapCol]))) {
                continue;
            }
            int id = toInt(row[idCol]);
            if (id <= 0) {
                continue;
            }
            if (bestId == 0 || id < bestId) {
                bestId = id;
            }
        }
        return bestId;
    }

    /**
     * 按章 id 读 ChapterOffset（格式 ox,oz）。
     *
     * @param chapterGd
     *            章节表
     * @param chapterId
     *            章 id
     * @return 偏移
     */
    private static ChapterOffset readOffset(GdData chapterGd, int chapterId) {
        int idCol = col(chapterGd, "ChapterId");
        int offCol = col(chapterGd, "ChapterOffset");
        if (idCol < 0 || offCol < 0) {
            return ChapterOffset.zero();
        }

        // 找该章行
        for (Object[] row : chapterGd.dataRows) {
            if (toInt(row[idCol]) != chapterId) {
                continue;
            }
            // 解析 "0,1"
            int ox = 0;
            int oz = 0;
            String raw = text(row[offCol]);
            String[] parts = raw.split(",");
            if (parts.length >= 1) {
                ox = toInt(parts[0].trim());
            }
            if (parts.length >= 2) {
                oz = toInt(parts[1].trim());
            }
            return new ChapterOffset(ox, oz, chapterId, "章" + chapterId + " 偏移 " + ox + "," + oz);
        }
        return ChapterOffset.zero();
    }

    /**
     * 去掉 .txt。
     *
     * @param name
     *            文件名
     * @return 无后缀；空则 null
     */
    private static String stripTxt(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (name.endsWith(".txt")) {
            return name.substring(0, name.length() - 4);
        }
        return name;
    }

    /**
     * 查列下标。
     *
     * @param gd
     *            GD
     * @param name
     *            列名
     * @return 下标；没有 -1
     */
    private static int col(GdData gd, String name) {
        if (gd == null || gd.header == null || gd.header.columnNames == null) {
            return -1;
        }
        return gd.header.columnNames.indexOf(name);
    }

    /**
     * 单元格转文本。
     *
     * @param cell
     *            单元格
     * @return 文本
     */
    private static String text(Object cell) {
        return cell == null ? "" : String.valueOf(cell).trim();
    }

    /**
     * 转 int。
     *
     * @param cell
     *            单元格
     * @return 整数；失败 0
     */
    private static int toInt(Object cell) {
        if (cell instanceof Number) {
            return ((Number)cell).intValue();
        }
        try {
            return Integer.parseInt(text(cell));
        } catch (Exception e) {
            return 0;
        }
    }
}
