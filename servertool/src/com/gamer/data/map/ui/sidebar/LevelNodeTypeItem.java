package com.gamer.data.map.ui.sidebar;

/**
 * 关卡节点类型下拉项。
 */
final class LevelNodeTypeItem {

    final int id;
    final String name;

    LevelNodeTypeItem(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return name + " (" + id + ")";
    }
}
