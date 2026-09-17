package com.gamer.data.excel.modelgen.binding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamer.data.limit.ColumnLengthPair;

/**
 * 列长度绑定去重：同一 Sheet 内无序列对去重，且左右相同列视为无效。
 */
public class ColumnLengthPairDedup {

    /**
     * 去重日志回调（红色日志）。
     */
    public interface DedupLog {

        /**
         * 输出去重日志。
         *
         * @param message
         *            日志内容
         */
        void log(String message);
    }

    /**
     * 禁止实例化。
     */
    private ColumnLengthPairDedup() {}

    /**
     * 对 Excel 全部 Sheet 的绑定行去重。
     *
     * @param state
     *            绑定状态
     * @param dedupLog
     *            日志回调，可为 null
     */
    public static void dedupeState(ExcelColumnBindingState state, DedupLog dedupLog) {
        if (state == null) {
            return;
        }
        for (Map.Entry<String, List<ColumnBindingRow>> entry : state.sheetBindings.entrySet()) {
            dedupeRows(entry.getValue(), state.excelName, entry.getKey(), dedupLog);
        }
    }

    /**
     * 对单个 Sheet 绑定行去重（原地修改列表，保留先出现的行）。
     *
     * @param rows
     *            绑定行列表
     * @param excelName
     *            Excel 文件名
     * @param sheetName
     *            Sheet 名
     * @param dedupLog
     *            日志回调，可为 null
     * @return 移除行数
     */
    public static int dedupeRows(List<ColumnBindingRow> rows, String excelName, String sheetName, DedupLog dedupLog) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        Set<String> seenKeys = new HashSet<>();
        List<ColumnBindingRow> toRemove = new ArrayList<>();
        collectDuplicateRows(rows, excelName, sheetName, seenKeys, toRemove, dedupLog);
        rows.removeAll(toRemove);
        return toRemove.size();
    }

    /**
     * 对已收集列对去重（写入 limit 前）。
     *
     * @param pairs
     *            列对列表
     * @param excelName
     *            Excel 文件名
     * @param sheetName
     *            Sheet 名
     * @param dedupLog
     *            日志回调，可为 null
     * @return 去重后的列对列表
     */
    public static List<ColumnLengthPair> dedupePairs(List<ColumnLengthPair> pairs, String excelName, String sheetName,
        DedupLog dedupLog) {
        List<ColumnLengthPair> result = new ArrayList<>();
        if (pairs == null || pairs.isEmpty()) {
            return result;
        }
        Set<String> seenKeys = new HashSet<>();
        for (ColumnLengthPair pair : pairs) {
            if (pair == null) {
                continue;
            }
            if (isSameColumn(pair.colA, pair.colB)) {
                logRemovedPair(excelName, sheetName, pair.colA, pair.colB, "左右相同列", dedupLog);
                continue;
            }
            String key = buildUnorderedKey(pair.colA, pair.colB);
            if (key == null) {
                continue;
            }
            if (seenKeys.contains(key)) {
                logRemovedPair(excelName, sheetName, pair.colA, pair.colB, "重复列对", dedupLog);
                continue;
            }
            seenKeys.add(key);
            result.add(pair);
        }
        return result;
    }

    /**
     * 构建无序列对键（小列名|大列名）。
     *
     * @param colA
     *            列 A
     * @param colB
     *            列 B
     * @return 键；无效返回 null
     */
    public static String buildUnorderedKey(String colA, String colB) {
        String a = trim(colA);
        String b = trim(colB);
        if (a.isEmpty() || b.isEmpty()) {
            return null;
        }
        if (a.compareTo(b) <= 0) {
            return a + "|" + b;
        }
        return b + "|" + a;
    }

    /**
     * 判断是否左右相同列。
     *
     * @param colA
     *            列 A
     * @param colB
     *            列 B
     * @return 是否相同
     */
    public static boolean isSameColumn(String colA, String colB) {
        String a = trim(colA);
        String b = trim(colB);
        return !a.isEmpty() && a.equals(b);
    }

    /**
     * 扫描并收集需移除的重复绑定行。
     *
     * @param rows
     *            绑定行列表
     * @param excelName
     *            Excel 文件名
     * @param sheetName
     *            Sheet 名
     * @param seenKeys
     *            已见列对键
     * @param toRemove
     *            待移除行
     * @param dedupLog
     *            日志回调
     */
    private static void collectDuplicateRows(List<ColumnBindingRow> rows, String excelName, String sheetName,
        Set<String> seenKeys, List<ColumnBindingRow> toRemove, DedupLog dedupLog) {
        for (ColumnBindingRow row : rows) {
            if (row == null) {
                continue;
            }
            String colA = trim(row.colA);
            String colB = trim(row.colB);
            if (colA.isEmpty() || colB.isEmpty()) {
                continue;
            }
            if (colA.equals(colB)) {
                toRemove.add(row);
                logRemovedPair(excelName, sheetName, colA, colB, "左右相同列", dedupLog);
                continue;
            }
            String key = buildUnorderedKey(colA, colB);
            if (seenKeys.contains(key)) {
                toRemove.add(row);
                logRemovedPair(excelName, sheetName, colA, colB, "重复列对", dedupLog);
                continue;
            }
            seenKeys.add(key);
        }
    }

    /**
     * 输出移除日志。
     *
     * @param excelName
     *            Excel 文件名
     * @param sheetName
     *            Sheet 名
     * @param colA
     *            列 A
     * @param colB
     *            列 B
     * @param reason
     *            移除原因
     * @param dedupLog
     *            日志回调
     */
    private static void logRemovedPair(String excelName, String sheetName, String colA, String colB, String reason,
        DedupLog dedupLog) {
        if (dedupLog == null) {
            return;
        }
        String msg = "[列绑定去重] " + excelName + " [" + sheetName + "] 列对 " + colA + "|" + colB + " " + reason + "，已移除";
        dedupLog.log(msg);
    }

    /**
     * trim 字符串，null 转空串。
     *
     * @param value
     *            原字符串
     * @return trim 结果
     */
    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
