package com.gamer.data.excel.modelgen.generator;

import java.util.List;

import com.gamer.data.excel.modelgen.domain.ConstType;
import com.gamer.data.excel.shared.Title;

/**
 * 模型代码渲染 Module：只接收已分析的领域数据并生成 Java 源码。
 *
 * <p>该 Module 不读取 Excel、不写文件，也不修改 Spring XML。</p>
 */
final class CodeRenderer {

    /** 配置模型包名前缀。 */
    private static final String DATA_PACKAGE_PREFIX = "com.gow.common.config.";

    private CodeRenderer() {}

    /**
     * 渲染配置模型类。
     *
     * @param javaName
     *            类名
     * @param titles
     *            已完成类型分析的字段
     * @param packagePath
     *            配置子包路径
     * @param author
     *            作者
     * @return Java 源码
     */
    static String renderModel(String javaName, List<Title> titles, String packagePath, String author) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(DATA_PACKAGE_PREFIX).append(packagePath).append(";\n\n");
        code.append("import com.gamer.core.CoreObject;\n");
        code.append("import com.gow.common.config.DataCell;\n\n");
        code.append("/**\n * ").append(javaName).append("\n *\n * @author ").append(author).append("\n */\n");
        code.append("public class ").append(javaName).append(" extends CoreObject {\n\n");
        code.append("    private static final long serialVersionUID = 1L;\n\n");
        code.append("    public ").append(javaName).append("() {\n");
        code.append("        // 默认构造函数\n");
        code.append("    }\n");

        // 字段和访问方法使用同一份类型映射，避免生成结果漂移。
        for (Title title : titles) {
            appendField(code, title);
        }
        code.append("\n    // Getters\n");
        for (Title title : titles) {
            appendGetter(code, title);
        }
        code.append("}\n");
        return code.toString();
    }

    /**
     * 渲染常量枚举类。
     *
     * @param className
     *            枚举类名
     * @param enumItems
     *            枚举项
     * @param packagePath
     *            包路径
     * @param author
     *            作者
     * @return Java 源码
     */
    static String renderEnum(String className, List<ConstType> enumItems, String packagePath, String author) {
        StringBuilder code = new StringBuilder();
        code.append("package ").append(packagePath.replace('/', '.')).append(";\n\n");
        code.append("import java.util.HashMap;\nimport java.util.Map;\n\n");
        code.append("/**\n * ").append(className).append("\n *\n * @author ").append(author).append("\n */\n");
        code.append("public enum ").append(className).append(" {\n\n");
        appendEnumItems(code, enumItems);
        code.append("    private final int code;\n\n");
        code.append("    private final String comment;\n\n");
        code.append("    private final static Map<Integer, ").append(className)
            .append("> enums = new HashMap<Integer, ").append(className).append(">();\n\n");
        code.append("    static {\n");
        code.append("        for (").append(className).append(" value : values()) {\n");
        code.append("            enums.put(value.getCode(), value);\n        }\n    }\n\n");
        code.append("    ").append(className).append("(int code, String comment) {\n");
        code.append("        this.code = code;\n        this.comment = comment;\n    }\n\n");
        code.append("    public static ").append(className).append(" fromId(int code) {\n");
        code.append("        return enums.get(code);\n    }\n\n");
        code.append("    public int getCode() {\n        return code;\n    }\n\n");
        code.append("    public String getComment() {\n        return comment;\n    }\n");
        code.append("}");
        return code.toString();
    }

    /**
     * 追加一个带 DataCell 注解的字段。
     *
     * @param code
     *            输出缓冲区
     * @param title
     *            字段定义
     */
    private static void appendField(StringBuilder code, Title title) {
        code.append('\n').append(JavaNames.description(title.getDes())).append('\n');
        if (title.getNewCode() != null) {
            code.append("    @DataCell(").append(title.getNewCode()).append(")\n");
        } else {
            code.append("    @DataCell(\"").append(title.getOldName()).append("\")\n");
        }
        code.append("    private ").append(toJavaType(title.getType())).append(' ')
            .append(title.getName()).append(";\n");
    }

    /**
     * 追加字段 Getter。
     *
     * @param code
     *            输出缓冲区
     * @param title
     *            字段定义
     */
    private static void appendGetter(StringBuilder code, Title title) {
        code.append("    public ").append(toJavaType(title.getType())).append(" get")
            .append(JavaNames.upperFirst(title.getName())).append("() {\n");
        code.append("        return ").append(title.getName()).append(";\n    }\n\n");
    }

    /**
     * 追加枚举项并处理最后一个分号。
     *
     * @param code
     *            输出缓冲区
     * @param items
     *            枚举项
     */
    private static void appendEnumItems(StringBuilder code, List<ConstType> items) {
        for (int i = 0; i < items.size(); i++) {
            ConstType item = items.get(i);
            code.append("    /** ").append(item.getName()).append('(').append(item.getId()).append(", \"")
                .append(item.getDes()).append("\") */\n");
            code.append("    ").append(item.getName()).append('(').append(item.getId()).append(", \"")
                .append(item.getDes()).append("\")");
            code.append(i < items.size() - 1 ? ",\n" : ";\n\n");
        }
    }

    /**
     * 将配置类型映射为 Java 类型。
     *
     * @param type
     *            配置类型
     * @return Java 类型
     */
    private static String toJavaType(String type) {
        if (type == null || "string".equalsIgnoreCase(type)) {
            return "String";
        }
        return type.toLowerCase();
    }
}
