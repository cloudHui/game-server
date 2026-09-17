package com.gamer.data.excel.modelgen.generator;

/** Java 标识符和注释文本格式化规则。 */
public final class JavaNames {

    private static final String COMMENT_PREFIX = "    // ";
    private static final int MAX_COMMENT_LENGTH = 120;

    private JavaNames() {}

    public static String camelCase(String value, boolean upperFirst) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        int initialUppers = 0;
        while (initialUppers < value.length() && Character.isUpperCase(value.charAt(initialUppers))) {
            initialUppers++;
        }
        boolean preserveInitialUppers = initialUppers >= 2;
        StringBuilder result = new StringBuilder();
        boolean upperNext = false;
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == '_' || Character.isWhitespace(character)) {
                upperNext = true;
                continue;
            }
            int outputIndex = result.length();
            if (upperNext) {
                character = Character.toUpperCase(character);
            } else if (!(preserveInitialUppers && outputIndex < initialUppers) && outputIndex == 0) {
                character = upperFirst ? Character.toUpperCase(character) : Character.toLowerCase(character);
            }
            result.append(character);
            upperNext = false;
        }
        return result.toString();
    }

    public static String upperFirst(String value) {
        return "ID".equals(value) ? value : camelCase(value, true);
    }

    public static String description(String value) {
        if (value == null || value.trim().isEmpty()) {
            return COMMENT_PREFIX;
        }
        StringBuilder result = new StringBuilder();
        StringBuilder line = new StringBuilder(COMMENT_PREFIX);
        for (String word : value.trim().split("\\s+")) {
            if (line.length() + word.length() + 1 > MAX_COMMENT_LENGTH) {
                result.append(line).append('\n');
                line = new StringBuilder(COMMENT_PREFIX);
            }
            if (line.length() > COMMENT_PREFIX.length()) {
                line.append(' ');
            }
            line.append(word);
        }
        return result.append(line).toString();
    }
}
