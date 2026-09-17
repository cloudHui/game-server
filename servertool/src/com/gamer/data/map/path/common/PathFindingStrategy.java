package com.gamer.data.map.path.common;

/**
 * 寻路策略接口。
 */
public interface PathFindingStrategy {

    /**
     * 查找路径。
     *
     * @param request
     *            寻路请求
     * @return 寻路结果
     */
    PathResult findPath(PathRequest request);
}
