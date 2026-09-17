package com.gamer.data.limit;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * limit/{Excel名}.txt 读写（[Sheet] + 列名=type + 列A|列B）。
 */
public class ColumnLengthLimitStore {

    /**
     * 禁止实例化。
     */
    private ColumnLengthLimitStore() {}

    /**
     * 读取 limit 列长度绑定段。
     *
     * @param limitFile
     *            limit 文件
     * @return Sheet -> 列对列表
     */
    public static Map<String, List<ColumnLengthPair>> read(File limitFile) {
        LimitFileData data = readFull(limitFile);
        return data.sheetPairs;
    }

    /**
     * 读取 limit 完整内容（type + 绑定）。
     *
     * @param limitFile
     *            limit 文件
     * @return 完整数据
     */
    public static LimitFileData readFull(File limitFile) {
        LimitFileData data = new LimitFileData();
        if (limitFile == null || !limitFile.isFile()) {
            return data;
        }
        BufferedReader reader = null;
        try {
            reader = openReader(limitFile);
            parseReaderLines(reader, data);
        } catch (Exception ignored) {
        } finally {
            closeReader(reader);
        }
        return data;
    }

    /**
     * 写入完整 limit 文件；无内容则删除文件。
     *
     * @param limitDir
     *            limit 目录
     * @param excelName
     *            Excel 文件名
     * @param data
     *            完整数据
     */
    public static void writeFull(File limitDir, String excelName, LimitFileData data) {
        if (limitDir == null || excelName == null) {
            return;
        }
        ensureLimitDir(limitDir);
        File limitFile = buildLimitFile(limitDir, excelName);
        if (data == null || data.isEmpty()) {
            deleteLimitFile(limitFile);
            return;
        }
        writeDataToFile(limitFile, data);
    }

    /**
     * 删除指定 Excel 的 limit 文件。
     *
     * @param limitDir
     *            limit 目录
     * @param excelName
     *            Excel 文件名
     * @return 是否删除成功或文件本不存在
     */
    public static boolean deleteExcelLimitFile(File limitDir, String excelName) {
        if (limitDir == null || excelName == null) {
            return false;
        }
        File limitFile = buildLimitFile(limitDir, excelName);
        if (!limitFile.isFile()) {
            return true;
        }
        if (limitFile.delete()) {
            return true;
        }
        limitFile.deleteOnExit();
        return false;
    }

    /**
     * 构造 limit 文件路径。
     *
     * @param limitDir
     *            limit 目录
     * @param excelName
     *            Excel 文件名
     * @return limit 文件
     */
    public static File buildLimitFile(File limitDir, String excelName) {
        String baseName = LimitPathUtil.toExcelBaseName(excelName);
        return new File(limitDir, baseName + ".txt");
    }

    /**
     * 打开 limit 文件 UTF-8 读取器。
     *
     * @param limitFile
     *            limit 文件
     * @return BufferedReader
     * @throws Exception
     *             打开失败
     */
    private static BufferedReader openReader(File limitFile) throws Exception {
        return new BufferedReader(
            new InputStreamReader(Files.newInputStream(limitFile.toPath()), StandardCharsets.UTF_8));
    }

    /**
     * 逐行解析 limit 文件写入 LimitFileData。
     *
     * @param reader
     *            读取器
     * @param data
     *            目标数据
     * @throws Exception
     *             读取异常
     */
    private static void parseReaderLines(BufferedReader reader, LimitFileData data) throws Exception {
        String line;
        String currentSheet = null;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (isSectionHeader(line)) {
                currentSheet = extractSectionName(line);
                ensureSheetTypes(data, currentSheet);
                ensureSheetPairs(data, currentSheet);
                continue;
            }
            if (currentSheet == null) {
                continue;
            }
            if (isTypeLine(line)) {
                parseTypeLine(data, currentSheet, line);
                continue;
            }
            ColumnLengthPair pair = parsePairLine(line);
            if (pair != null) {
                data.sheetPairs.get(currentSheet).add(pair);
            }
        }
    }

    /**
     * 判断是否为列 type 行（含 = 且不含 |）。
     *
     * @param line
     *            文本行
     * @return 是否 type 行
     */
    private static boolean isTypeLine(String line) {
        int eq = line.indexOf('=');
        if (eq <= 0 || eq >= line.length() - 1) {
            return false;
        }
        return line.indexOf('|') < 0;
    }

    /**
     * 解析列 type 行写入 data。
     *
     * @param data
     *            目标数据
     * @param sheetName
     *            当前 Sheet
     * @param line
     *            文本行
     */
    private static void parseTypeLine(LimitFileData data, String sheetName, String line) {
        int eq = line.indexOf('=');
        String colName = line.substring(0, eq).trim();
        String type = line.substring(eq + 1).trim();
        if (colName.isEmpty() || type.isEmpty()) {
            return;
        }
        data.sheetTypes.get(sheetName).put(colName, type);
    }

    /**
     * 确保 Sheet type 段存在。
     *
     * @param data
     *            目标数据
     * @param sheetName
     *            Sheet 名
     */
    private static void ensureSheetTypes(LimitFileData data, String sheetName) {
        if (!data.sheetTypes.containsKey(sheetName)) {
            data.sheetTypes.put(sheetName, new LinkedHashMap<>());
        }
    }

    /**
     * 确保 Sheet 绑定段存在。
     *
     * @param data
     *            目标数据
     * @param sheetName
     *            Sheet 名
     */
    private static void ensureSheetPairs(LimitFileData data, String sheetName) {
        if (!data.sheetPairs.containsKey(sheetName)) {
            data.sheetPairs.put(sheetName, new ArrayList<>());
        }
    }

    /**
     * 关闭读取器。
     *
     * @param reader
     *            BufferedReader
     */
    private static void closeReader(BufferedReader reader) {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * 判断是否为 [Sheet] 段头。
     *
     * @param line
     *            文本行
     * @return 是否段头
     */
    private static boolean isSectionHeader(String line) {
        return line.startsWith("[") && line.endsWith("]") && line.length() > 2;
    }

    /**
     * 从段头行提取 Sheet 名。
     *
     * @param line
     *            段头行
     * @return Sheet 名
     */
    private static String extractSectionName(String line) {
        return line.substring(1, line.length() - 1).trim();
    }

    /**
     * 确保 limit 目录存在。
     *
     * @param limitDir
     *            limit 目录
     */
    private static void ensureLimitDir(File limitDir) {
        if (!limitDir.exists()) {
            limitDir.mkdirs();
        }
    }

    /**
     * 删除 limit 文件。
     *
     * @param limitFile
     *            limit 文件
     */
    private static void deleteLimitFile(File limitFile) {
        if (limitFile.exists() && !limitFile.delete()) {
            limitFile.deleteOnExit();
        }
    }

    /**
     * 将 LimitFileData 写入文件。
     *
     * @param limitFile
     *            目标文件
     * @param data
     *            数据
     */
    private static void writeDataToFile(File limitFile, LimitFileData data) {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(limitFile.toPath()), StandardCharsets.UTF_8));
            writeAllSections(writer, data);
        } catch (Exception ignored) {
        } finally {
            closeWriter(writer);
        }
    }

    /**
     * 写入全部 Sheet 段。
     *
     * @param writer
     *            写入器
     * @param data
     *            数据
     * @throws Exception
     *             写入异常
     */
    private static void writeAllSections(BufferedWriter writer, LimitFileData data) throws Exception {
        Set<String> sheetNames = new LinkedHashSet<>();
        sheetNames.addAll(data.sheetTypes.keySet());
        sheetNames.addAll(data.sheetPairs.keySet());
        boolean first = true;
        for (String sheetName : sheetNames) {
            Map<String, String> types = data.sheetTypes.get(sheetName);
            List<ColumnLengthPair> pairs = data.sheetPairs.get(sheetName);
            boolean hasTypes = types != null && !types.isEmpty();
            boolean hasPairs = pairs != null && !pairs.isEmpty();
            if (!hasTypes && !hasPairs) {
                continue;
            }
            if (!first) {
                writer.newLine();
            }
            writeOneSection(writer, sheetName, types, pairs);
            first = false;
        }
    }

    /**
     * 写入单个 Sheet 段（type 行 + 绑定行）。
     *
     * @param writer
     *            写入器
     * @param sheetName
     *            Sheet 名
     * @param types
     *            列 type
     * @param pairs
     *            列绑定
     * @throws Exception
     *             写入异常
     */
    private static void writeOneSection(BufferedWriter writer, String sheetName, Map<String, String> types,
        List<ColumnLengthPair> pairs) throws Exception {
        writer.write("[");
        writer.write(sheetName);
        writer.write("]");
        writer.newLine();
        if (types != null) {
            for (Map.Entry<String, String> entry : types.entrySet()) {
                writer.write(entry.getKey());
                writer.write("=");
                writer.write(entry.getValue());
                writer.newLine();
            }
        }
        if (pairs != null) {
            for (ColumnLengthPair pair : pairs) {
                writer.write(pair.colA);
                writer.write("|");
                writer.write(pair.colB);
                writer.newLine();
            }
        }
    }

    /**
     * 关闭写入器。
     *
     * @param writer
     *            BufferedWriter
     */
    private static void closeWriter(BufferedWriter writer) {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (Exception ignored) {
        }
    }

    /**
     * 解析列绑定行（ColA|ColB）。
     *
     * @param line
     *            文本行
     * @return 列对；非法返回 null
     */
    private static ColumnLengthPair parsePairLine(String line) {
        if (line.indexOf('=') >= 0) {
            return null;
        }
        int idx = line.indexOf('|');
        if (idx <= 0 || idx >= line.length() - 1) {
            return null;
        }
        String colA = line.substring(0, idx).trim();
        String colB = line.substring(idx + 1).trim();
        if (colA.isEmpty() || colB.isEmpty()) {
            return null;
        }
        return new ColumnLengthPair(colA, colB);
    }
}
