package com.gamer.data.map.path.compare;

/**
 * Path simulation run mode selected by UI.
 */
public enum PathSimulationMode {

    TOOL_ONLY("只跑工具模拟", true, false),
    WORLD_ONLY("只跑WorldServer", false, true),
    COMPARE("同世界坐标对比", true, true);

    /** UI label. */
    private final String label;

    /** Whether to run servertool simulation. */
    private final boolean toolEnabled;

    /** Whether to run WorldServer simulation. */
    private final boolean worldEnabled;

    PathSimulationMode(String label, boolean toolEnabled, boolean worldEnabled) {
        this.label = label;
        this.toolEnabled = toolEnabled;
        this.worldEnabled = worldEnabled;
    }

    public boolean isToolEnabled() {
        return toolEnabled;
    }

    public boolean isWorldEnabled() {
        return worldEnabled;
    }

    @Override
    public String toString() {
        return label;
    }
}
