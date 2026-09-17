package com.gamer.data.excel.modelgen.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.gamer.data.excel.modelgen.ModelGenContext;

/**
 * 模型注册写入 Module：批量更新 Spring XML 与 BeanManager Java 文件。
 */
final class RegistrationWriter {

    /** 配置模型包名前缀。 */
    private static final String DATA_PACKAGE_PREFIX = "com.gow.common.config.";

    /** 一条 Manager 注册定义。 */
    static final class Definition {
        final String managerClassName;
        final String configClassName;
        final String sheetName;
        final String packagePath;

        /**
         * @param managerClassName Manager 类名
         * @param configClassName Config 类名
         * @param sheetName 配置表名
         * @param packagePath 配置子包
         */
        Definition(String managerClassName, String configClassName, String sheetName, String packagePath) {
            this.managerClassName = managerClassName;
            this.configClassName = configClassName;
            this.sheetName = sheetName;
            this.packagePath = packagePath;
        }
    }

    private RegistrationWriter() {}

    /**
     * 批量写入一个服务器的 Spring 与 BeanManager 注册。
     *
     * @param xmlPath
     *            Spring XML 路径
     * @param beanManagerPath
     *            BeanManager Java 路径
     * @param definitions
     *            注册定义
     * @param context
     *            生成上下文
     */
    static void write(String xmlPath, String beanManagerPath, List<Definition> definitions, ModelGenContext context) {
        if (definitions == null || definitions.isEmpty()) {
            return;
        }
        try {
            writeSpringXml(Paths.get(xmlPath), definitions);
            context.logMessage("已添加 bean 配置到: " + xmlPath);
        } catch (Exception ex) {
            context.logMessage("添加 bean 配置失败: " + xmlPath + " error: " + ex);
        }
        try {
            writeBeanManager(Paths.get(beanManagerPath), definitions);
            context.logMessage("已批量添加方法到 BeanManager: " + beanManagerPath);
        } catch (Exception ex) {
            context.logMessage("添加方法到 BeanManager 失败: " + beanManagerPath + " error: " + ex);
        }
    }

    /**
     * 在最后一个 bean 后批量插入缺失的 Manager 定义。
     *
     * @param path
     *            XML 路径
     * @param definitions
     *            注册定义
     * @throws IOException
     *             文件读写失败
     */
    private static void writeSpringXml(Path path, List<Definition> definitions) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("配置文件不存在: " + path);
        }
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        int insertAt = content.lastIndexOf("</bean>");
        if (insertAt < 0) {
            throw new IOException("未找到 </bean> 标签: " + path);
        }
        insertAt += "</bean>".length();

        StringBuilder addition = new StringBuilder();
        for (Definition definition : definitions) {
            String beanId = JavaNames.camelCase(definition.managerClassName, false);
            String className = DATA_PACKAGE_PREFIX + definition.packagePath + "." + definition.managerClassName;
            if (content.contains("id=\"" + beanId + "\"") || content.contains("class=\"" + className + "\"")) {
                continue;
            }
            addition.append("\n    <!-- ").append(definition.configClassName).append(" -->\n");
            addition.append("    <bean id=\"").append(beanId).append("\" class=\"").append(className)
                .append("\" parent=\"dataManager\">\n");
            addition.append("        <property name=\"fileName\" value=\"").append(definition.sheetName)
                .append("\"/>\n    </bean>");
        }
        if (addition.length() > 0) {
            String result = content.substring(0, insertAt) + addition + content.substring(insertAt);
            Files.write(path, result.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 批量重建配置 import 组并追加缺失的获取方法。
     *
     * @param path
     *            BeanManager 路径
     * @param definitions
     *            注册定义
     * @throws IOException
     *             文件读写失败
     */
    private static void writeBeanManager(Path path, List<Definition> definitions) throws IOException {
        if (!Files.exists(path)) {
            throw new IOException("BeanManager 文件不存在: " + path);
        }
        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        String[] lines = content.split("\n", -1);
        Set<String> imports = collectConfigImports(lines);
        StringBuilder methods = new StringBuilder();

        // Import 与获取方法从同一注册定义生成，避免两处信息失配。
        for (Definition definition : definitions) {
            imports.add("import " + DATA_PACKAGE_PREFIX + definition.packagePath + "."
                + definition.managerClassName + ";");
            String methodName = "get" + definition.managerClassName;
            if (!content.contains(methodName + "()")) {
                appendGetter(methods, definition, methodName);
            }
        }

        String rebuilt = replaceConfigImports(lines, imports);
        if (methods.length() > 0) {
            int classEnd = rebuilt.lastIndexOf('}');
            if (classEnd < 0) {
                throw new IOException("未找到类结束位置: " + path);
            }
            rebuilt = rebuilt.substring(0, classEnd) + methods + rebuilt.substring(classEnd);
        }
        Files.write(path, rebuilt.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 收集现有配置 Manager import。
     *
     * @param lines
     *            Java 文件行
     * @return 去重 import 集合
     */
    private static Set<String> collectConfigImports(String[] lines) {
        Set<String> imports = new HashSet<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("import com.gow.common.config.")) {
                imports.add(trimmed);
            }
        }
        return imports;
    }

    /**
     * 将配置 import 组替换为排序后的完整集合。
     *
     * @param lines
     *            Java 文件行
     * @param imports
     *            完整 import 集合
     * @return 重建后的 Java 文本
     */
    private static String replaceConfigImports(String[] lines, Set<String> imports) {
        List<String> sorted = new ArrayList<>(imports);
        Collections.sort(sorted);
        int first = -1;
        int last = -1;
        int lastImport = -1;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.startsWith("import ")) {
                lastImport = i;
            }
            if (trimmed.startsWith("import com.gow.common.config.")) {
                first = first < 0 ? i : first;
                last = i;
            }
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (first >= 0 && i == first) {
                appendLines(result, sorted);
                i = last;
                continue;
            }
            result.append(lines[i]).append('\n');
            if (first < 0 && i == lastImport) {
                appendLines(result, sorted);
            }
        }
        return result.toString();
    }

    /**
     * 追加 Manager 获取方法。
     *
     * @param output 输出缓冲区
     * @param definition 注册定义
     * @param methodName 方法名
     */
    private static void appendGetter(StringBuilder output, Definition definition, String methodName) {
        output.append("    /**\n     * 获取").append(definition.configClassName)
            .append("配置管理器\n     *\n     * @return 配置管理器\n     */\n");
        output.append("    public static ").append(definition.managerClassName).append(' ').append(methodName)
            .append("() {\n        return getComponent(\"")
            .append(JavaNames.camelCase(definition.managerClassName, false)).append("\");\n    }\n\n");
    }

    /**
     * 逐行追加已排序 import。
     *
     * @param output 输出缓冲区
     * @param values import 行
     */
    private static void appendLines(StringBuilder output, List<String> values) {
        for (String value : values) {
            output.append(value).append('\n');
        }
    }
}
