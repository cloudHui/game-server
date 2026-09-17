package com.gamer.data.excel.ui;

import java.util.Arrays;

public class ViewUtils {

    // 常见的数组分隔符
    private static final String[] ARRAY_DELIMITERS = {"\\|", ",", ";"};
    private static final double DOUBLE_EPSILON = 0.0001; // 浮点数比较精度

    /**
     * 比较两个对象是否相等（用于Excel数据对比，支持原始数据类型：int、string、int[]、string[]）
     */
    public static boolean objectsNotEqual(Object obj1, Object obj2) {
        return areNotValuesEqual(obj1, obj2);
    }

    /**
     * 核心比较方法：判断两个值是否相等
     */
    private static boolean areNotValuesEqual(Object obj1, Object obj2) {
        if (obj1 == obj2) {
            return false;
        }
        if (obj1 == null || obj2 == null) {
            return true;
        }

        // 1. 处理数组类型
        if (obj1.getClass().isArray() && obj2.getClass().isArray()) {
            return !compareArrays(obj1, obj2);
        }

        // 2. 处理字符串（可能包含数组格式）
        if (obj1 instanceof String && obj2 instanceof String) {
            return !compareStringValues((String)obj1, (String)obj2);
        }

        // 3. 处理数值类型
        if (isNumericType(obj1) || isNumericType(obj2)) {
            return !compareNumericValues(obj1, obj2);
        }

        // 4. 默认使用equals比较
        return !obj1.equals(obj2);
    }

    /**
     * 比较两个数组是否相等
     */
    private static boolean compareArrays(Object array1, Object array2) {
        // 处理一维数组
        if (array1 instanceof int[] && array2 instanceof int[]) {
            return Arrays.equals((int[])array1, (int[])array2);
        }
        if (array1 instanceof String[] && array2 instanceof String[]) {
            return Arrays.equals((String[])array1, (String[])array2);
        }

        // 处理二维数组
        if (array1 instanceof int[][] && array2 instanceof int[][]) {
            return deepEqualsInt2D((int[][])array1, (int[][])array2);
        }
        if (array1 instanceof String[][] && array2 instanceof String[][]) {
            return deepEqualsString2D((String[][])array1, (String[][])array2);
        }

        // 处理Object数组
        if (array1 instanceof Object[] && array2 instanceof Object[]) {
            return Arrays.deepEquals((Object[])array1, (Object[])array2);
        }

        return false; // 数组类型不匹配
    }

    /**
     * 比较字符串值（支持数组字符串格式）
     */
    private static boolean compareStringValues(String str1, String str2) {
        // 如果都是普通字符串，直接比较
        if (isNotArrayString(str1) && isNotArrayString(str2)) {
            return str1.equals(str2);
        }

        // 尝试解析为数组并比较
        String[] arr1 = parseArrayString(str1);
        String[] arr2 = parseArrayString(str2);

        // 长度不同则不相等
        if (arr1.length != arr2.length) {
            return false;
        }

        // 尝试按整数数组比较
        try {
            int[] intArr1 = parseStringArrayToInt(arr1);
            int[] intArr2 = parseStringArrayToInt(arr2);
            return Arrays.equals(intArr1, intArr2);
        } catch (NumberFormatException e) {
            // 不是整数数组，按字符串数组比较
            return Arrays.equals(arr1, arr2);
        }
    }

    /**
     * 比较数值类型（支持不同类型间的数值比较）
     */
    private static boolean compareNumericValues(Object obj1, Object obj2) {
        try {
            double val1 = getNumericValue(obj1);
            double val2 = getNumericValue(obj2);
            return Math.abs(val1 - val2) < DOUBLE_EPSILON;
        } catch (Exception e) {
            // 数值转换失败，尝试其他比较方式
            if (obj1 instanceof String || obj2 instanceof String) {
                return obj1.toString().equals(obj2.toString());
            }
            return false;
        }
    }

    /**
     * 判断字符串是否为数组格式
     */
    public static boolean isNotArrayString(String str) {
        if (str == null || str.trim().isEmpty()) {
            return true;
        }
        // 检查是否包含常见的数组分隔符
        for (String delimiter : ARRAY_DELIMITERS) {
            if (str.contains(delimiter.replace("\\", ""))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析数组字符串（支持多种分隔符）
     */
    public static String[] parseArrayString(String str) {
        if (str == null || str.trim().isEmpty()) {
            return new String[0];
        }

        String trimmed = str.trim();

        // 按优先级尝试分隔符
        for (String delimiter : ARRAY_DELIMITERS) {
            if (trimmed.contains(delimiter.replace("\\", ""))) {
                return trimmed.split(delimiter);
            }
        }

        return new String[] {trimmed};
    }

    /**
     * 将字符串数组转换为整数数组
     */
    private static int[] parseStringArrayToInt(String[] strArray) {
        int[] result = new int[strArray.length];
        for (int i = 0; i < strArray.length; i++) {
            result[i] = Integer.parseInt(strArray[i].trim());
        }
        return result;
    }

    /**
     * 深度比较二维整数数组
     */
    public static boolean deepEqualsInt2D(int[][] arr1, int[][] arr2) {
        if (arr1 == arr2)
            return true;
        if (arr1 == null || arr2 == null)
            return false;
        if (arr1.length != arr2.length)
            return false;

        for (int i = 0; i < arr1.length; i++) {
            if (!Arrays.equals(arr1[i], arr2[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 深度比较二维字符串数组
     */
    public static boolean deepEqualsString2D(String[][] arr1, String[][] arr2) {
        if (arr1 == arr2)
            return true;
        if (arr1 == null || arr2 == null)
            return false;
        if (arr1.length != arr2.length)
            return false;

        for (int i = 0; i < arr1.length; i++) {
            if (!Arrays.equals(arr1[i], arr2[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * 获取数值
     */
    public static double getNumericValue(Object obj) {
        if (obj == null) {
            throw new IllegalArgumentException("对象不能为null");
        }

        if (obj instanceof Number) {
            return ((Number)obj).doubleValue();
        }

        if (obj instanceof String) {
            String str = ((String)obj).trim();
            if (str.isEmpty()) {
                throw new IllegalArgumentException("字符串不能为空");
            }
            return Double.parseDouble(str);
        }

        throw new IllegalArgumentException("无法转换为数值: " + obj.getClass().getName());
    }

    /**
     * 判断是否为数值类型
     */
    private static boolean isNumericType(Object obj) {
        return obj instanceof Number || (obj instanceof String && isNumericString((String)obj));
    }

    /**
     * 格式化文件大小供日志展示。
     *
     * @param bytes 字节数
     * @return 如 {@code 34 MB}
     */
    public static String formatFileSizeForLog(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return (bytes / 1024L) + " KB";
        }
        return (bytes / (1024L * 1024L)) + " MB";
    }

    /**
     * 判断字符串是否为数值字符串
     */
    private static boolean isNumericString(String str) {
        if (str == null || str.trim().isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}