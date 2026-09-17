package com.gamer.data.excel.shared;

/**
 * 模型成员列名类
 */
public class Title {

    private final String name;

    private final String oldName;

    private String type;

    private final String des;

    private String newCode = null;

    private final String originalType;

    public Title(String name, String type, String des, String oldName, String originalType) {
        this.name = name;
        this.type = type;
        this.des = des;
        this.oldName = oldName;
        this.originalType = originalType;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDes() {
        return des;
    }

    public String getOldName() {
        return oldName;
    }

    public String getNewCode() {
        return newCode;
    }

    public void setNewCode(String newCode) {
        this.newCode = newCode;
    }

    public boolean isOriginalVindex() {
        return originalType != null && originalType.trim().equalsIgnoreCase("vindex");
    }

    /**
     * 树节点显示（与历史 CodeTool 一致）。
     * <ul>
     * <li>{@code name}：生成 Java 字段名</li>
     * <li>{@code type}：生成用的类型（int / int[] / string[][] 等，含是否数组）</li>
     * <li>{@code originalType}：Excel 表上原始类型</li>
     * </ul>
     */
    @Override
    public String toString() {
        return name + '\''
            + ", '" + oldName + '\''
            + ", '" + type + '\''
            + ", '" + des + '\''
            + ", '" + newCode + '\''
            + ", '" + originalType + '\'';
    }
}