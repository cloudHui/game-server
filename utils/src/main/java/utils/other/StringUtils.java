package utils.other;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringJoiner;

/**
 * 字符串分割与通用处理工具类
 * <p>提供安全字符串判定、多类型数组/列表安全分割与版本号比较功能。</p>
 *
 * @author cloud
 */
public class StringUtils {

    private static final String DEFAULT_DELIMITER = ",";

    private StringUtils() {
    }

    /**
     * 判断字符串是否为 null 或空串
     */
    public static boolean isNullOrEmpty(String data) {
        return data == null || data.isEmpty();
    }

    public static List<Long> splitLong(String data) {
        return splitLong(data, DEFAULT_DELIMITER);
    }

    /**
     * 按指定分隔符将字符串安全分割为 Long 列表（过滤无法解析的项）
     */
    public static List<Long> splitLong(String data, String regex) {
        if (isNullOrEmpty(data)) {
            return Collections.emptyList();
        }
        String[] parts = data.split(regex);
        List<Long> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            try {
                result.add(Long.parseLong(part.trim()));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static List<Integer> splitInt(String data) {
        return splitInt(data, DEFAULT_DELIMITER);
    }

    /**
     * 按指定分隔符将字符串安全分割为 Integer 列表
     */
    public static List<Integer> splitInt(String data, String regex) {
        if (isNullOrEmpty(data)) {
            return Collections.emptyList();
        }
        String[] parts = data.split(regex);
        List<Integer> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            try {
                result.add(Integer.parseInt(part.trim()));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static int[] splitArrayInt(String data) {
        return splitArrayInt(data, DEFAULT_DELIMITER);
    }

    /**
     * 将字符串分割为原生 int[] 数组
     */
    public static int[] splitArrayInt(String data, String regex) {
        if (isNullOrEmpty(data)) {
            return null;
        }
        List<Integer> list = splitInt(data, regex);
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    public static List<Float> splitFloat(String data) {
        return splitFloat(data, DEFAULT_DELIMITER);
    }

    public static List<Float> splitFloat(String data, String regex) {
        if (isNullOrEmpty(data)) {
            return Collections.emptyList();
        }
        String[] parts = data.split(regex);
        List<Float> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            try {
                result.add(Float.parseFloat(part.trim()));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static List<Double> splitDouble(String data) {
        return splitDouble(data, DEFAULT_DELIMITER);
    }

    public static List<Double> splitDouble(String data, String regex) {
        if (isNullOrEmpty(data)) {
            return Collections.emptyList();
        }
        String[] parts = data.split(regex);
        List<Double> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            try {
                result.add(Double.parseDouble(part.trim()));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static List<String> splitString(String data) {
        return splitString(data, DEFAULT_DELIMITER);
    }

    public static List<String> splitString(String data, String regex) {
        if (isNullOrEmpty(data)) {
            return Collections.emptyList();
        }
        String[] parts = data.split(regex);
        List<String> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            result.add(part.trim());
        }
        return result;
    }

    /**
     * 将泛型列表转为逗号拼接字符串
     */
    public static <T> String listToString(List<T> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",");
        for (T item : data) {
            if (item != null) {
                joiner.add(item.toString());
            }
        }
        return joiner.toString();
    }

    /**
     * 版本号大小比对：判断 after 版本是否严格大于 before 版本
     *
     * @param before 旧版本（如 "1.0.1"）
     * @param after  新版本（如 "1.0.2"）
     * @return true 若 after 大于 before，否则 false
     */
    public static boolean versionBigger(String before, String after) {
        if (isNullOrEmpty(before) || isNullOrEmpty(after)) {
            return false;
        }
        String[] befores = before.split("\\.");
        String[] afters = after.split("\\.");
        int maxLen = Math.max(befores.length, afters.length);
        for (int i = 0; i < maxLen; i++) {
            int vBefore = (i < befores.length) ? NumberUtil.stringToNumber(befores[i], 0) : 0;
            int vAfter = (i < afters.length) ? NumberUtil.stringToNumber(afters[i], 0) : 0;
            if (vAfter > vBefore) {
                return true;
            } else if (vAfter < vBefore) {
                return false;
            }
        }
        return false;
    }
}
