package com.gamer.data.excel.modelgen.binding;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamer.data.excel.shared.FileWithSheets;
import com.gamer.data.excel.shared.Title;
import com.gamer.data.limit.ColumnLengthLimitStore;
import com.gamer.data.limit.ColumnLengthPair;
import com.gamer.data.limit.LimitFileData;
import com.gamer.data.limit.LimitPathUtil;

/**
 * CodeTool 生成模型后，为勾选 Sheet 一次性写入 limit（列 type + 列长度绑定）。
 */
public class ModelGenLimitTypeWriter {

    /**
     * 禁止实例化。
     */
    private ModelGenLimitTypeWriter() {}

    /**
     * 为已生成结构的 Excel 写入 limit（列 type + 列长度绑定，仅更新勾选 Sheet）。
     *
     * @param currDir
     *            当前 user.dir
     * @param baseDir
     *            BaseCheck.baseDir
     * @param generatedFiles
     *            本次已生成结构的 Excel 列表
     * @param fullExcelByName
     *            树节点完整 Excel（用于列长度绑定）
     * @param log
     *            日志回调，可为 null
     */
    public static void writeGeneratedSheets(File currDir, File baseDir, List<FileWithSheets> generatedFiles,
        Map<String, FileWithSheets> fullExcelByName, LimitWriteLog log) {
        File limitDir = LimitPathUtil.resolveLimitWriteDir(currDir, baseDir);
        if (limitDir == null) {
            if (log != null) {
                log.log("limit 目录解析失败，跳过写入 limit", true);
            }
            return;
        }
        if (generatedFiles == null || generatedFiles.isEmpty()) {
            return;
        }
        ColumnLengthPairDedup.DedupLog dedupLog = toDedupLog(log);
        for (FileWithSheets excel : generatedFiles) {
            if (excel == null || excel.excelName == null) {
                continue;
            }
            FileWithSheets fullExcel = null;
            if (fullExcelByName != null) {
                fullExcel = fullExcelByName.get(excel.excelName);
            }
            writeOneExcel(limitDir, excel, fullExcel, dedupLog, log);
        }
    }

    /**
     * 写入单个 Excel 的 limit（type + 绑定）。
     *
     * @param limitDir
     *            limit 目录
     * @param generatedExcel
     *            本次生成的 Excel 子集
     * @param fullExcel
     *            树节点完整 Excel
     * @param dedupLog
     *            去重日志
     * @param log
     *            日志回调
     */
    private static void writeOneExcel(File limitDir, FileWithSheets generatedExcel, FileWithSheets fullExcel,
        ColumnLengthPairDedup.DedupLog dedupLog, LimitWriteLog log) {
        File limitFile = ColumnLengthLimitStore.buildLimitFile(limitDir, generatedExcel.excelName);
        LimitFileData data = ColumnLengthLimitStore.readFull(limitFile);
        Map<String, List<ColumnLengthPair>> bindingBySheet =
            ModelGenBindingLoader.collectPairsForWrite(limitDir, fullExcel, dedupLog);
        if (generatedExcel.sheets == null) {
            return;
        }
        for (Map.Entry<String, List<Title>> entry : generatedExcel.sheets.entrySet()) {
            String sheetName = entry.getKey();
            LinkedHashMap<String, String> types = buildSheetTypes(entry.getValue());
            List<ColumnLengthPair> pairs = resolveSheetPairs(sheetName, bindingBySheet, types.keySet(), data);
            data.putSheetTypes(sheetName, types);
            data.putSheetPairs(sheetName, pairs);
        }
        ColumnLengthLimitStore.writeFull(limitDir, generatedExcel.excelName, data);
        if (log != null) {
            log.log("已写入 limit: " + limitFile.getPath(), false);
        }
    }

    /**
     * 解析单个 Sheet 的列长度绑定：面板配置优先，否则保留 limit 文件中仍合法的旧绑定。
     *
     * @param sheetName
     *            Sheet 名
     * @param bindingBySheet
     *            面板有效绑定
     * @param validColumns
     *            本次生成列名
     * @param data
     *            已有 limit 数据
     * @return 绑定对列表
     */
    private static List<ColumnLengthPair> resolveSheetPairs(String sheetName,
        Map<String, List<ColumnLengthPair>> bindingBySheet, Set<String> validColumns, LimitFileData data) {
        if (bindingBySheet != null && bindingBySheet.containsKey(sheetName)) {
            return bindingBySheet.get(sheetName);
        }
        return filterPairsByColumns(data.getSheetPairs(sheetName), validColumns);
    }

    /**
     * 从 Title 列表构建列名 -> type 映射。
     *
     * @param titles
     *            列定义列表
     * @return 列 type 映射
     */
    private static LinkedHashMap<String, String> buildSheetTypes(List<Title> titles) {
        LinkedHashMap<String, String> types = new LinkedHashMap<>();
        if (titles == null) {
            return types;
        }
        for (Title title : titles) {
            if (title == null || title.getOldName() == null || title.getOldName().isEmpty()) {
                continue;
            }
            String type = title.getType();
            if (type == null || type.trim().isEmpty()) {
                type = "string";
            }
            types.put(title.getOldName(), type.trim());
        }
        return types;
    }

    /**
     * 保留两列均仍存在于本次生成列名中的绑定对。
     *
     * @param pairs
     *            原绑定列表
     * @param validColumns
     *            合法列名
     * @return 过滤后的列表
     */
    private static List<ColumnLengthPair> filterPairsByColumns(List<ColumnLengthPair> pairs, Set<String> validColumns) {
        List<ColumnLengthPair> result = new ArrayList<>();
        if (pairs == null || validColumns == null || validColumns.isEmpty()) {
            return result;
        }
        for (ColumnLengthPair pair : pairs) {
            if (pair == null) {
                continue;
            }
            if (validColumns.contains(pair.colA) && validColumns.contains(pair.colB)) {
                result.add(pair);
            }
        }
        return result;
    }

    /**
     * 将 limit 写入日志适配为去重日志。
     *
     * @param log
     *            limit 写入日志
     * @return 去重日志；log 为 null 时返回 null
     */
    private static ColumnLengthPairDedup.DedupLog toDedupLog(final LimitWriteLog log) {
        if (log == null) {
            return null;
        }
        return message -> log.log(message, true);
    }

    /**
     * limit 写入日志回调。
     */
    public interface LimitWriteLog {

        /**
         * 输出日志。
         *
         * @param message
         *            消息
         * @param isError
         *            是否错误样式
         */
        void log(String message, boolean isError);
    }
}
