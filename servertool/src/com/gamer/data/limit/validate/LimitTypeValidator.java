package com.gamer.data.limit.validate;

/**
 * 按 limit 中 CodeTool 最终结构 type 校验单元格是否可转换。
 */
public class LimitTypeValidator {

    private LimitTypeValidator() {}

    public static boolean canConvert(String limitType, String value) {
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        String type = normalizeLimitType(limitType);
        switch (type) {
            case "int":
                return canConvertInt(value.trim());
            case "int[]":
                return canConvertIntArray(value.trim());
            case "int[][]":
                return canConvertIntArrayTwo(value.trim());
        }
        return true;
    }

    private static String normalizeLimitType(String limitType) {
        if (limitType == null || limitType.trim().isEmpty()) {
            return "string";
        }
        String type = limitType.trim().toLowerCase();
        if ("vindex".equals(type)) {
            return "int";
        }
        return type;
    }

    private static boolean canConvertInt(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean canConvertIntArray(String value) {
        String[] parts;
        if (value.indexOf('|') >= 0) {
            parts = value.split("\\|", -1);
        } else if (value.indexOf(',') >= 0) {
            parts = value.split(",", -1);
        } else {
            return canConvertInt(value);
        }
        for (String s : parts) {
            String part = s.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (!canConvertInt(part)) {
                return false;
            }
        }
        return true;
    }

    private static boolean canConvertIntArrayTwo(String value) {
        String[] rows = value.split("\\|", -1);
        for (String s : rows) {
            String row = s.trim();
            if (row.isEmpty()) {
                continue;
            }
            String[] cells = row.split(",", -1);
            for (String string : cells) {
                String cell = string.trim();
                if (cell.isEmpty()) {
                    continue;
                }
                if (!canConvertInt(cell)) {
                    return false;
                }
            }
        }
        return true;
    }
}
