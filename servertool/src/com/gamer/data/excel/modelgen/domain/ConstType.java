package com.gamer.data.excel.modelgen.domain;

/**
 * 常量类型（用于生成枚举）
 */
public class ConstType {
    private final int id; // 枚举ID
    private final String des; // 枚举描述
    private final String name; // 枚举名称

    public ConstType(int id, String des, String name) {
        this.id = id;
        this.des = des != null ? des : "";
        this.name = name != null ? name : "";
    }

    public int getId() {
        return id;
    }

    public String getDes() {
        return des;
    }

    public String getName() {
        return name;
    }
}