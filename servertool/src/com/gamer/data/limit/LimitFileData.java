package com.gamer.data.limit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * limit 文件完整内容：各 Sheet 列 type 与列长度绑定对。
 */
public class LimitFileData {

    /** Sheet 名 -> 列名(Excel第1行) -> limit type */
    public final Map<String, LinkedHashMap<String, String>> sheetTypes = new LinkedHashMap<>();

    /** Sheet 名 -> 列长度绑定对列表 */
    public final Map<String, List<ColumnLengthPair>> sheetPairs = new LinkedHashMap<>();

    /**
     * 获取指定 Sheet 的列 type 映射。
     *
     * @param sheetName
     *            Sheet 名
     * @return 列名 -> type；无则空映射
     */
    public Map<String, String> getSheetTypes(String sheetName) {
        // 读取已有 type 段
        LinkedHashMap<String, String> types = sheetTypes.get(sheetName);
        // 无则返回空映射
        if (types == null) {
            return new LinkedHashMap<>();
        }
        // 返回副本避免外部修改
        return new LinkedHashMap<>(types);
    }

    /**
     * 获取指定 Sheet 的列长度绑定列表。
     *
     * @param sheetName
     *            Sheet 名
     * @return 绑定对列表；无则空列表
     */
    public List<ColumnLengthPair> getSheetPairs(String sheetName) {
        // 读取已有绑定段
        List<ColumnLengthPair> pairs = sheetPairs.get(sheetName);
        // 无则返回空列表
        if (pairs == null) {
            return new ArrayList<>();
        }
        // 返回副本
        return new ArrayList<>(pairs);
    }

    /**
     * 设置指定 Sheet 的列 type（全量覆盖该 Sheet 段）。
     *
     * @param sheetName
     *            Sheet 名
     * @param types
     *            列名 -> type
     */
    public void putSheetTypes(String sheetName, Map<String, String> types) {
        // 新建有序映射
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        // 参数有效则写入
        if (types != null) {
            copy.putAll(types);
        }
        // 覆盖 Sheet type 段
        sheetTypes.put(sheetName, copy);
    }

    /**
     * 设置指定 Sheet 的列长度绑定对。
     *
     * @param sheetName
     *            Sheet 名
     * @param pairs
     *            绑定对列表
     */
    public void putSheetPairs(String sheetName, List<ColumnLengthPair> pairs) {
        // 新建列表
        List<ColumnLengthPair> copy = new ArrayList<>();
        // 参数有效则写入
        if (pairs != null) {
            copy.addAll(pairs);
        }
        // 覆盖 Sheet 绑定段
        sheetPairs.put(sheetName, copy);
    }

    /**
     * 判断 limit 文件是否无任何有效内容。
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        // 遍历 type 段
        for (Map.Entry<String, LinkedHashMap<String, String>> entry : sheetTypes.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                return false;
            }
        }
        // 遍历绑定段
        for (Map.Entry<String, List<ColumnLengthPair>> entry : sheetPairs.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                return false;
            }
        }
        // 全部为空
        return true;
    }
}
