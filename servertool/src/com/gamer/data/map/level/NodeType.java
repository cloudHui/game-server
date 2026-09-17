package com.gamer.data.map.level;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

/**
 * 节点类型对应颜色与名称，与 NodeTypeEnum 一致。
 */
public enum NodeType {

    /** 无/主节点 */
    NONE(-1, new Color(160, 160, 160), "无/主节点"),
    /** 等级 */
    LEVEL(0, new Color(100, 100, 255), "等级"),
    /** 开始 */
    START(1, new Color(0, 200, 0), "开始"),
    /** 结束 */
    END(2, new Color(200, 0, 0), "结束"),
    /** 入口 */
    ENTRANCE(3, new Color(255, 180, 0), "入口"),
    /** 敌人 */
    ENEMY(4, new Color(200, 0, 200), "敌人"),
    /** 奖励 */
    REWARD(5, new Color(255, 215, 0), "奖励"),
    /** 港口 */
    PORT(6, new Color(0, 180, 255), "港口"),
    /** 事件 */
    EVENT(7, new Color(255, 140, 0), "事件"),
    /** NPC */
    NPC(8, new Color(0, 200, 200), "NPC"),
    /** 资源 */
    RESOURCE(9, new Color(34, 139, 34), "资源"),
    /** 关卡建筑 */
    LEVEL_BUILDING(10, new Color(139, 69, 19), "关卡建筑"),
    /** 玩法路线 */
    PLAY_ROUTE(11, new Color(148, 0, 211), "玩法路线");

    private final int id;
    private final Color color;
    private final String name;

    private static final Map<Integer, NodeType> TYPE_MAP = new HashMap<>();

    NodeType(int id, Color color, String name) {
        this.id = id;
        this.color = color;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public Color getColor() {
        return color;
    }

    public String getName() {
        return name;
    }

    static {
        for (NodeType type : NodeType.values()) {
            TYPE_MAP.put(type.getId(), type);
        }
    }

    public static Color getColor(int typeId) {
        return TYPE_MAP.getOrDefault(typeId, NONE).getColor();
    }

    /** 类型 id 对应的显示名称 */
    public static String getTypeName(int typeId) {
        return TYPE_MAP.getOrDefault(typeId, NONE).getName();
    }
}
