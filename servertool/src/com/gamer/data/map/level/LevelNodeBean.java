package com.gamer.data.map.level;

/**
 * Level 节点数据快照，参考 gameserver 侧 {@code ChapterLevel}： 这里只保留地图展示与调试常用的字段。
 */
public class LevelNodeBean {

    /** Position.X → 世界坐标 X（像素） */
    private int x;
    /** Position.Y → 世界坐标 Y（像素） */
    private int z;

    /** Type，对应 NodeTypeEnum / NodeTypeColor */
    private int type;

    /** DataId：章节节点唯一 id */
    private int dataId;
    /** BelongId：所属章节/分组 id */
    private int belongId;
    /** NextDataId：下一个节点的 DataId */
    private int nextDataId;
    /** TextId：文案 id */
    private int textId;
    /** IndexId：节点序号 */
    private int indexId;

    /** 原始 Param 字段（如玩法路线 "x,y,z&x,y,z"），便于 Tooltip 展示 */
    private String paramRaw;

    public LevelNodeBean(int x, int z, int type, int dataId, int belongId, int nextDataId, int textId, int indexId,
        String paramRaw) {
        this.x = x;
        this.z = z;
        this.type = type;
        this.dataId = dataId;
        this.belongId = belongId;
        this.nextDataId = nextDataId;
        this.textId = textId;
        this.indexId = indexId;
        this.paramRaw = paramRaw;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public int getType() {
        return type;
    }

    public int getDataId() {
        return dataId;
    }

    public int getBelongId() {
        return belongId;
    }

    public int getNextDataId() {
        return nextDataId;
    }

    public int getTextId() {
        return textId;
    }

    public int getIndexId() {
        return indexId;
    }

    public String getParamRaw() {
        return paramRaw;
    }

    public void setX(int x) {
        this.x = x;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public void setType(int type) {
        this.type = type;
    }

    public void setDataId(int dataId) {
        this.dataId = dataId;
    }

    public void setBelongId(int belongId) {
        this.belongId = belongId;
    }

    public void setNextDataId(int nextDataId) {
        this.nextDataId = nextDataId;
    }

    public void setTextId(int textId) {
        this.textId = textId;
    }

    public void setIndexId(int indexId) {
        this.indexId = indexId;
    }

    public void setParamRaw(String paramRaw) {
        this.paramRaw = paramRaw;
    }

    /**
     * 浅拷贝，用于编辑前保存快照以便字节级局部写回对比。
     */
    public LevelNodeBean copy() {
        return new LevelNodeBean(x, z, type, dataId, belongId, nextDataId, textId, indexId,
            paramRaw == null ? "" : paramRaw);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("\n");
        sb.append("  Type=").append(type).append(" ").append(NodeType.getTypeName(type));
        sb.append("  Position=(").append(x).append(", ").append(z).append(")");
        sb.append("  DataId=").append(dataId);
        sb.append("  BelongId=").append(belongId);

        if (paramRaw != null && !paramRaw.isEmpty()) {
            sb.append("  Param=").append(paramRaw);
        }
        sb.append("\n");
        return sb.toString();
    }
}
