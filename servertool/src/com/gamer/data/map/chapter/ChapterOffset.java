package com.gamer.data.map.chapter;

import com.gamer.data.map.grid.MapRingUtil;

/**
 * 章节偏移：LevelChapter.ChapterOffset（如 0,1）。
 * <p>
 * 含义：本章地块本地原点 (0,0)，在大世界里要平移 ox/oz 个「整章」。
 * 一章像素宽 = 地图列数 × 10，高 = 行数 × 10。
 * </p>
 */
public final class ChapterOffset {

    /** 横向章节格偏移 */
    public final int ox;

    /** 纵向章节格偏移 */
    public final int oz;

    /** 命中的章节 id；没有则为 -1 */
    public final int chapterId;

    /** 状态栏用的短说明 */
    public final String info;

    /**
     * @param ox
     *            横向章节格偏移
     * @param oz
     *            纵向章节格偏移
     * @param chapterId
     *            章节 id
     * @param info
     *            状态栏说明
     */
    public ChapterOffset(int ox, int oz, int chapterId, String info) {
        this.ox = ox;
        this.oz = oz;
        this.chapterId = chapterId;
        this.info = info == null ? "" : info;
    }

    /**
     * 造一个零偏移（第 1 章常见）。
     *
     * @return 零偏移
     */
    public static ChapterOffset zero() {
        return new ChapterOffset(0, 0, -1, "偏移 0,0");
    }

    /**
     * 算本章本地原点的大世界像素 X。
     *
     * @param mapCols
     *            地图宽（格）
     * @return 大世界 X
     */
    public int originWorldX(int mapCols) {
        // 原点 X = 横向第几章 × 一章像素宽
        return ox * mapCols * MapRingUtil.WORLD_PIXEL_PER_CELL;
    }

    /**
     * 算本章本地原点的大世界像素 Z。
     *
     * @param mapRows
     *            地图高（格）
     * @return 大世界 Z
     */
    public int originWorldZ(int mapRows) {
        // 原点 Z = 纵向第几章 × 一章像素高
        return oz * mapRows * MapRingUtil.WORLD_PIXEL_PER_CELL;
    }
}
