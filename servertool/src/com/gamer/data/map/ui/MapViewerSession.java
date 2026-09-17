package com.gamer.data.map.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.gamer.data.map.chapter.ChapterOffset;
import com.gamer.data.map.grid.MapData;
import com.gamer.data.map.level.LevelDocument;
import com.gamer.data.map.level.LevelNodeBean;
import com.gamer.data.map.ui.canvas.MapViewerCanvas;

/**
 * 地图查看器各子模块共享的可变会话状态。
 */
public final class MapViewerSession {

    /** 主地图画布 */
    public MapViewerCanvas canvas;

    /** 当前地图数据 */
    public MapData currentMapData;

    /** 当前关卡节点（完整列表）；坐标为章节本地像素 */
    public List<LevelNodeBean> currentLevelNodes = new ArrayList<>();

    /** 与 txt 同步的关卡文档；null 表示只读回退 */
    public LevelDocument currentLevelDoc;

    /** GD/data 目录（CodeTool / StrategyTool 传入）；用于读 LevelChapter 偏移 */
    public File gdDataDir;

    /** 当前章节偏移；默认零偏移 */
    public ChapterOffset chapterOffset = ChapterOffset.zero();

    /** 当前加载的章节地图文件名（含 .txt） */
    public String currentMapFileName;

    /** 当前加载的关卡节点文件名（含 .txt） */
    public String currentLevelFileName;
}
