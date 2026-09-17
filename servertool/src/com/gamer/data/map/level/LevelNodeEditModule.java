package com.gamer.data.map.level;

/**
 * 关卡节点编辑 Module：集中保存编辑值、生成差异并应用到节点。
 */
public final class LevelNodeEditModule {

    /** 不可变编辑值。 */
    public static final class Values {
        public final int x;
        public final int z;
        public final int type;
        public final int dataId;
        public final int belongId;
        public final int nextDataId;
        public final String typeName;

        /**
         * @param x 世界 X 坐标
         * @param z 世界 Z 坐标
         * @param type 节点类型
         * @param dataId 节点数据 id
         * @param belongId 所属节点 id
         * @param nextDataId 下一节点 id
         * @param typeName 类型显示名
         */
        public Values(int x, int z, int type, int dataId, int belongId, int nextDataId, String typeName) {
            this.x = x;
            this.z = z;
            this.type = type;
            this.dataId = dataId;
            this.belongId = belongId;
            this.nextDataId = nextDataId;
            this.typeName = typeName == null ? "" : typeName;
        }
    }

    private LevelNodeEditModule() {}

    /**
     * 生成字段级差异文案。
     *
     * @param before
     *            修改前快照
     * @param values
     *            新值
     * @return 差异文案；无变化时为空字符串
     */
    public static String buildDiff(LevelNodeBean before, Values values) {
        StringBuilder diff = new StringBuilder();
        appendDiff(diff, "地图 X", before.getX(), values.x, null);
        appendDiff(diff, "地图 Z", before.getZ(), values.z, null);
        appendDiff(diff, "类型", before.getType(), values.type, values.typeName);
        appendDiff(diff, "DataId", before.getDataId(), values.dataId, null);
        appendDiff(diff, "BelongId", before.getBelongId(), values.belongId, null);
        appendDiff(diff, "NextDataId", before.getNextDataId(), values.nextDataId, null);
        return diff.toString();
    }

    /**
     * 将确认后的编辑值应用到节点 Bean。
     *
     * @param target
     *            目标节点
     * @param values
     *            已确认的新值
     */
    public static void apply(LevelNodeBean target, Values values) {
        target.setX(values.x);
        target.setZ(values.z);
        target.setType(values.type);
        target.setDataId(values.dataId);
        target.setBelongId(values.belongId);
        target.setNextDataId(values.nextDataId);
    }

    /**
     * 比较单个数字字段并追加差异。
     *
     * @param output 输出缓冲区
     * @param label 字段名
     * @param oldValue 原值
     * @param newValue 新值
     * @param detail 可选的新值说明
     */
    private static void appendDiff(StringBuilder output, String label, int oldValue, int newValue, String detail) {
        if (oldValue == newValue) {
            return;
        }
        output.append(String.format("· %s: %d -> %d", label, oldValue, newValue));
        if (detail != null && !detail.isEmpty()) {
            output.append(" (").append(detail).append(')');
        }
        output.append('\n');
    }
}
