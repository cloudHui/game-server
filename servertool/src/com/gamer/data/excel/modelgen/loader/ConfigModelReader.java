package com.gamer.data.excel.modelgen.loader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gamer.data.excel.modelgen.generator.JavaNames;

/**
 * 已有 Config 源码读取器：按 Excel、Sheet 映射源码，并提取类中声明的 DataCell 列名。
 */
public final class ConfigModelReader {

    /** Config 源码相对于 Server 根目录的位置 */
    private static final String CONFIG_SOURCE_RELATIVE_PATH = "common/src/com/gow/common/config";

    /** DataCell 的 value 字符串；行首限制可避开注释中的示例文本 */
    private static final Pattern DATA_CELL_PATTERN = Pattern.compile(
        "(?m)^\\s*@DataCell\\s*\\(\\s*(?:value\\s*=\\s*)?\"([^\"]+)\"");

    /**
     * 禁止实例化。
     */
    private ConfigModelReader() {}

    /**
     * 按项目映射定位 Config 源文件。
     *
     * @param serverPath
     *            Server 根目录
     * @param excelName
     *            Excel 文件名
     * @param sheetName
     *            Sheet 名
     * @return 对应的 Config 源文件
     */
    public static File resolveConfigSourceFile(String serverPath, String excelName, String sheetName) {
        // Excel 文件名去扩展名并转小写，对应 config 子包。
        String fileName = new File(excelName).getName();
        int dotIndex = fileName.lastIndexOf('.');
        String packageName = (dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName).toLowerCase(Locale.ROOT);

        // Sheet 名对应 Config 类，已有 Config 后缀时不重复追加。
        String configClassName = JavaNames.upperFirst(sheetName.trim());
        if (!configClassName.endsWith("Config")) {
            configClassName += "Config";
        }
        return new File(new File(new File(serverPath, CONFIG_SOURCE_RELATIVE_PATH), packageName),
            configClassName + ".java");
    }

    /**
     * 从 Config 源码提取所有 DataCell 列名，保持源码声明顺序。
     *
     * @param configSourceFile
     *            Config 源文件
     * @return DataCell 列名集合
     * @throws IOException
     *             源文件读取失败
     */
    public static Set<String> readDataCellNames(File configSourceFile) throws IOException {
        String source = new String(Files.readAllBytes(configSourceFile.toPath()), StandardCharsets.UTF_8);
        Set<String> dataCellNames = new LinkedHashSet<>();
        Matcher matcher = DATA_CELL_PATTERN.matcher(source);
        while (matcher.find()) {
            dataCellNames.add(matcher.group(1));
        }
        return dataCellNames;
    }
}
