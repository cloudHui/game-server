package com.gamer.data.map.ui.path.replay;

/**
 * 寻路调试画布使用的搜索回放阶段。
 */
public enum PathSearchReplayStage {

    /** 粗格 A* 搜索格子阶段 */
    GRID_SEARCH,

    /** 世界坐标 A* 搜索点阶段 */
    POINT_SEARCH
}
