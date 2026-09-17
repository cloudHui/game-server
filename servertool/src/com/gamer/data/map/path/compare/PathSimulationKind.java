package com.gamer.data.map.path.compare;

/**
 * 工具模拟类型。
 */
public enum PathSimulationKind {

    TOOL("工具模拟"),
    WORLD("WorldServer模拟");

    /** 显示名称。 */
    private final String label;

    PathSimulationKind(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
