package com.gamer.data.read;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 按首列 ID 对比两份 {@link GdData}：同 ID 逐列改值、增删行。
 * <p>
 * 轻量实现，仅依赖 {@code read} 包，供 WindowsTools「更新文档」等场景写日志；
 * 不依赖 {@code excel.diff}（该包未打入 WindowsTools.jar）。
 * </p>
 */
public final class GdIdRowDiff {

    /** 列修改明细最多保留条数，超出写「还有 N 处」。 */
    public static final int MAX_CELL_DIFFS = 50;

    /** 增删行 ID 最多列出个数，超出写「等共 N 个」。 */
    public static final int MAX_ROW_ID_SAMPLES = 20;

    private GdIdRowDiff() {}

    /**
     * 对比结果：列修改（已截断）+ 增删行 ID。
     */
    public static final class Result {

        /** 列修改明细，格式：{@code ID·列名：旧值 → 新值} */
        public final List<String> cellChanges;

        /** 列修改总数（含未写入明细的） */
        public final int totalCellChanges;

        /** 新增行 ID（已排序） */
        public final List<String> addedIds;

        /** 删除行 ID（已排序） */
        public final List<String> deletedIds;

        private Result(List<String> cellChanges, int totalCellChanges, List<String> addedIds,
            List<String> deletedIds) {
            this.cellChanges = cellChanges;
            this.totalCellChanges = totalCellChanges;
            this.addedIds = addedIds;
            this.deletedIds = deletedIds;
        }

        /**
         * @return 无列修改且无增删行时为 true
         */
        public boolean isEmpty() {
            return totalCellChanges == 0 && addedIds.isEmpty() && deletedIds.isEmpty();
        }
    }

    /**
     * 按首列 ID 对比两份 GD。
     * <p>
     * 仅对比两侧表头共有的列名，避免「新增列」与单元格变更重复计数。
     * </p>
     *
     * @param oldData
     *            上一版 GD
     * @param newData
     *            当前 GD
     * @return 对比结果，入参为 null 时返回空结果
     */
    public static Result compare(GdData oldData, GdData newData) {
        if (oldData == null || newData == null) {
            return new Result(Collections.emptyList(), 0, Collections.emptyList(),
                Collections.emptyList());
        }
        // 两侧共有列（按当前表顺序），用于同 ID 逐列比较
        List<String> sharedColumns = sharedColumnNames(oldData, newData);
        Map<String, Map<String, String>> oldRows = buildRowsById(oldData);
        Map<String, Map<String, String>> newRows = buildRowsById(newData);

        List<String> cellChanges = new ArrayList<>();
        int totalCellChanges = 0;
        // 同 ID：逐共有列比对
        for (Map.Entry<String, Map<String, String>> entry : newRows.entrySet()) {
            String id = entry.getKey();
            Map<String, String> oldRow = oldRows.get(id);
            if (oldRow == null) {
                continue;
            }
            Map<String, String> newRow = entry.getValue();
            for (String col : sharedColumns) {
                String oldVal = cellOf(oldRow, col);
                String newVal = cellOf(newRow, col);
                if (oldVal.equals(newVal)) {
                    continue;
                }
                totalCellChanges++;
                if (cellChanges.size() < MAX_CELL_DIFFS) {
                    cellChanges.add(id + "·" + col + "：" + oldVal + " → " + newVal);
                }
            }
        }

        // 增删行：按 ID 差集
        Set<String> added = new TreeSet<>(newRows.keySet());
        added.removeAll(oldRows.keySet());
        Set<String> deleted = new TreeSet<>(oldRows.keySet());
        deleted.removeAll(newRows.keySet());
        return new Result(cellChanges, totalCellChanges, new ArrayList<>(added), new ArrayList<>(deleted));
    }

    /**
     * 组装多行日志：路径标题 + 列增删 + 单元格修改 + 行增删（含 ID 样例）。
     *
     * @param path
     *            GD 相对路径（日志前缀）
     * @param addedColumns
     *            新增列名，可为 null
     * @param deletedColumns
     *            删除列名，可为 null
     * @param result
     *            {@link #compare} 结果
     * @return 无任何变更时返回空列表
     */
    public static List<String> toLogLines(String path, Collection<String> addedColumns,
        Collection<String> deletedColumns, Result result) {
        List<String> lines = new ArrayList<>();
        boolean hasColumnChange = (addedColumns != null && !addedColumns.isEmpty())
            || (deletedColumns != null && !deletedColumns.isEmpty());
        if (!hasColumnChange && (result == null || result.isEmpty())) {
            return lines;
        }
        lines.add(path + "：");
        if (addedColumns != null && !addedColumns.isEmpty()) {
            lines.add("  新增列[" + joinIds(addedColumns, -1) + "]");
        }
        if (deletedColumns != null && !deletedColumns.isEmpty()) {
            lines.add("  删除列[" + joinIds(deletedColumns, -1) + "]");
        }
        if (result == null) {
            return lines;
        }
        for (String change : result.cellChanges) {
            lines.add("  修改 " + change);
        }
        int hidden = result.totalCellChanges - result.cellChanges.size();
        if (hidden > 0) {
            lines.add("  还有 " + hidden + " 处列修改未展示");
        }
        if (!result.addedIds.isEmpty()) {
            lines.add("  新增行" + result.addedIds.size() + " ["
                + joinIds(result.addedIds, MAX_ROW_ID_SAMPLES) + "]");
        }
        if (!result.deletedIds.isEmpty()) {
            lines.add("  删除行" + result.deletedIds.size() + " ["
                + joinIds(result.deletedIds, MAX_ROW_ID_SAMPLES) + "]");
        }
        return lines;
    }

    /**
     * 取两侧表头列名交集，顺序与当前 GD 一致。
     */
    private static List<String> sharedColumnNames(GdData oldData, GdData newData) {
        Set<String> oldNames = new HashSet<>();
        if (oldData.header != null && oldData.header.columnNames != null) {
            oldNames.addAll(oldData.header.columnNames);
        }
        List<String> shared = new ArrayList<>();
        if (newData.header == null || newData.header.columnNames == null) {
            return shared;
        }
        for (String name : newData.header.columnNames) {
            if (oldNames.contains(name)) {
                shared.add(name);
            }
        }
        return shared;
    }

    /**
     * 将数据行转为 ID →（列名 → 单元格字符串）。
     */
    private static Map<String, Map<String, String>> buildRowsById(GdData data) {
        Map<String, Map<String, String>> map = new HashMap<>();
        if (data.dataRows == null || data.header == null || data.header.columnNames == null) {
            return map;
        }
        List<String> columnNames = data.header.columnNames;
        for (Object[] row : data.dataRows) {
            if (row == null || row.length == 0) {
                continue;
            }
            String id = cellText(row[0]);
            if (id.isEmpty()) {
                continue;
            }
            Map<String, String> cells = new HashMap<>();
            for (int i = 0; i < columnNames.size(); i++) {
                Object value = i < row.length ? row[i] : null;
                cells.put(columnNames.get(i), cellText(value));
            }
            map.put(id, cells);
        }
        return map;
    }

    private static String cellOf(Map<String, String> row, String col) {
        if (row == null) {
            return "";
        }
        String value = row.get(col);
        return value == null ? "" : value;
    }

    private static String cellText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    /**
     * 用顿号拼接 ID/列名；{@code maxSamples < 0} 表示全部；否则超限追加「等共 N 个」。
     */
    private static String joinIds(Collection<String> ids, int maxSamples) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int index = 0;
        for (String id : ids) {
            if (maxSamples >= 0 && index >= maxSamples) {
                break;
            }
            if (index > 0) {
                sb.append("、");
            }
            sb.append(id);
            index++;
        }
        if (maxSamples >= 0 && ids.size() > maxSamples) {
            sb.append("等共").append(ids.size()).append("个");
        }
        return sb.toString();
    }
}
