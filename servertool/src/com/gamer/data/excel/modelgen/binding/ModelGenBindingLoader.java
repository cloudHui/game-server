package com.gamer.data.excel.modelgen.binding;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamer.data.excel.shared.FileWithSheets;
import com.gamer.data.excel.shared.Title;
import com.gamer.data.limit.ColumnLengthLimitStore;
import com.gamer.data.limit.ColumnLengthPair;
import com.gamer.data.limit.LimitPathUtil;

/**
 * 模型生成页列长度绑定：缓存、从 limit 加载、收集有效绑定对。
 */
public class ModelGenBindingLoader {

    /** Excel 文件名 -> 绑定状态（与绑定面板共享同一对象引用） */
    private static final Map<String, ExcelColumnBindingState> BINDING_CACHE = new HashMap<>();

    /**
     * 禁止实例化。
     */
    private ModelGenBindingLoader() {}

    /** 切 Excel 时丢掉绑定缓存，下次从 limit 重载。 */
    public static void invalidate(String excelName) {
        if (excelName != null) {
            BINDING_CACHE.remove(excelName);
        }
    }

    /**
     * 获取或从 limit 文件加载 Excel 的列绑定状态（结果写入缓存）。
     *
     * @param excel
     *            Excel 结构数据
     * @param currDir
     *            当前 user.dir
     * @param baseDir
     *            BaseCheck.baseDir
     * @return 绑定状态
     */
    public static ExcelColumnBindingState resolveBindingState(FileWithSheets excel, File currDir, File baseDir) {
        if (excel == null || excel.excelName == null) {
            return new ExcelColumnBindingState("");
        }
        ExcelColumnBindingState cached = BINDING_CACHE.get(excel.excelName);
        if (cached != null) {
            return cached;
        }
        File limitDir = LimitPathUtil.resolveLimitWriteDir(currDir, baseDir);
        List<String> sheetNames = collectSheetNames(excel);
        cached = loadBindingState(limitDir, excel.excelName, sheetNames);
        BINDING_CACHE.put(excel.excelName, cached);
        return cached;
    }

    /**
     * 收集写入 limit 用的有效列长度绑定（优先使用缓存中的面板编辑结果）。
     *
     * @param limitDir
     *            limit 目录
     * @param fullExcel
     *            树节点完整 Excel
     * @param dedupLog
     *            去重日志，可为 null
     * @return Sheet 名 -> 绑定对；无 fullExcel 时返回 null
     */
    public static Map<String, List<ColumnLengthPair>> collectPairsForWrite(File limitDir, FileWithSheets fullExcel,
        ColumnLengthPairDedup.DedupLog dedupLog) {
        if (fullExcel == null || fullExcel.excelName == null) {
            return null;
        }
        ExcelColumnBindingState state = BINDING_CACHE.get(fullExcel.excelName);
        if (state == null) {
            state = loadBindingState(limitDir, fullExcel.excelName, collectSheetNames(fullExcel));
        }
        ColumnLengthPairDedup.dedupeState(state, dedupLog);
        return collectValidPairs(state, dedupLog, fullExcel);
    }

    /**
     * 从 limit 文件加载绑定状态（不写入缓存）。
     *
     * @param limitDir
     *            limit 目录
     * @param excelName
     *            Excel 文件名
     * @param sheetNames
     *            Sheet 名列表
     * @return 绑定状态
     */
    private static ExcelColumnBindingState loadBindingState(File limitDir, String excelName, List<String> sheetNames) {
        ExcelColumnBindingState state = new ExcelColumnBindingState(excelName);
        initEmptySheets(state, sheetNames);
        if (limitDir == null) {
            return state;
        }
        File limitFile = ColumnLengthLimitStore.buildLimitFile(limitDir, excelName);
        fillStateFromFile(state, ColumnLengthLimitStore.read(limitFile));
        return state;
    }

    /**
     * 从绑定状态收集各 Sheet 的有效列长度绑定对。
     *
     * @param state
     *            绑定状态
     * @param dedupLog
     *            去重日志
     * @param excel
     *            Excel 结构（用于校验列名）
     * @return Sheet 名 -> 绑定对列表
     */
    private static Map<String, List<ColumnLengthPair>> collectValidPairs(ExcelColumnBindingState state,
        ColumnLengthPairDedup.DedupLog dedupLog, FileWithSheets excel) {
        Map<String, List<ColumnLengthPair>> valid = new LinkedHashMap<>();
        for (Map.Entry<String, List<ColumnBindingRow>> entry : state.sheetBindings.entrySet()) {
            String sheetName = entry.getKey();
            Set<String> validColumns = buildValidColumnSet(excel, sheetName);
            List<ColumnLengthPair> pairs =
                rowsToPairs(entry.getValue(), validColumns, state.excelName, sheetName, dedupLog);
            pairs = ColumnLengthPairDedup.dedupePairs(pairs, state.excelName, sheetName, dedupLog);
            if (!pairs.isEmpty()) {
                valid.put(sheetName, pairs);
            }
        }
        return valid;
    }

    /**
     * 收集 Excel 全部 Sheet 名。
     *
     * @param excel
     *            Excel 结构
     * @return Sheet 名列表
     */
    private static List<String> collectSheetNames(FileWithSheets excel) {
        List<String> sheetNames = new ArrayList<>();
        if (excel != null && excel.sheets != null) {
            sheetNames.addAll(excel.sheets.keySet());
        }
        return sheetNames;
    }

    /**
     * 为绑定状态预创建空 Sheet 行列表。
     *
     * @param state
     *            绑定状态
     * @param sheetNames
     *            Sheet 名列表
     */
    private static void initEmptySheets(ExcelColumnBindingState state, List<String> sheetNames) {
        if (sheetNames == null) {
            return;
        }
        for (String sheetName : sheetNames) {
            state.getSheetRows(sheetName);
        }
    }

    /**
     * 用 limit 文件中的绑定对填充状态。
     *
     * @param state
     *            绑定状态
     * @param parsed
     *            自 limit 解析的 Sheet -> 列对
     */
    private static void fillStateFromFile(ExcelColumnBindingState state, Map<String, List<ColumnLengthPair>> parsed) {
        if (parsed == null) {
            return;
        }
        for (Map.Entry<String, List<ColumnLengthPair>> entry : parsed.entrySet()) {
            List<ColumnBindingRow> rows = state.getSheetRows(entry.getKey());
            rows.clear();
            List<ColumnLengthPair> pairs = entry.getValue();
            if (pairs == null) {
                continue;
            }
            for (ColumnLengthPair pair : pairs) {
                rows.add(new ColumnBindingRow(pair.colA, pair.colB));
            }
        }
    }

    /**
     * 将绑定行转为列对，并过滤无效列名。
     *
     * @param rows
     *            绑定行
     * @param validColumns
     *            合法列名集合
     * @param excelName
     *            Excel 文件名
     * @param sheetName
     *            Sheet 名
     * @param dedupLog
     *            去重日志
     * @return 列对列表
     */
    private static List<ColumnLengthPair> rowsToPairs(List<ColumnBindingRow> rows, Set<String> validColumns,
        String excelName, String sheetName, ColumnLengthPairDedup.DedupLog dedupLog) {
        List<ColumnLengthPair> pairs = new ArrayList<>();
        boolean skippedInvalid = false;
        if (rows == null) {
            return pairs;
        }
        for (ColumnBindingRow row : rows) {
            if (row == null) {
                continue;
            }
            String colA = trim(row.colA);
            String colB = trim(row.colB);
            if (colA.isEmpty() && colB.isEmpty()) {
                continue;
            }
            if (!isValidColumnPair(colA, colB, validColumns)) {
                skippedInvalid = true;
                continue;
            }
            if (ColumnLengthPairDedup.isSameColumn(colA, colB)) {
                continue;
            }
            pairs.add(new ColumnLengthPair(colA, colB));
        }
        if (skippedInvalid && dedupLog != null) {
            dedupLog.log("[列绑定] " + excelName + " [" + sheetName + "] 存在无效列对，已跳过");
        }
        return pairs;
    }

    /**
     * 判断列对是否均存在于合法列名集合中。
     *
     * @param colA
     *            列 A
     * @param colB
     *            列 B
     * @param validColumns
     *            合法列名
     * @return 是否合法
     */
    private static boolean isValidColumnPair(String colA, String colB, Set<String> validColumns) {
        if (colA.isEmpty() || colB.isEmpty()) {
            return false;
        }
        if (validColumns == null || validColumns.isEmpty()) {
            return false;
        }
        return validColumns.contains(colA) && validColumns.contains(colB);
    }

    /**
     * 从 Excel 列定义构建合法列名集合。
     *
     * @param excel
     *            Excel 结构
     * @param sheetName
     *            Sheet 名
     * @return 列名集合
     */
    private static Set<String> buildValidColumnSet(FileWithSheets excel, String sheetName) {
        Set<String> columns = new HashSet<>();
        if (excel == null || excel.sheets == null) {
            return columns;
        }
        List<Title> titles = excel.sheets.get(sheetName);
        if (titles == null) {
            return columns;
        }
        for (Title title : titles) {
            if (title != null && title.getOldName() != null && !title.getOldName().isEmpty()) {
                columns.add(title.getOldName());
            }
        }
        return columns;
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
