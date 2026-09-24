package utils.other;

/**
 * 数字类型安全转换工具类
 * <p>提供字符串及任意对象向基础数字类型（int/long/float/double）的安全转换与默认值兜底。</p>
 *
 * @author cloud
 */
public final class NumberUtil {

    private NumberUtil() {
    }

    /**
     * 将字符串安全转换为 int，若解析失败或为空则返回 defaultValue
     *
     * @param str          待转换字符串
     * @param defaultValue 兜底默认值
     * @return 转换后的 int 值
     */
    public static int stringToNumber(String str, int defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 将任意对象安全转换为 int
     */
    public static int stringToNumber(Object obj, int defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        return stringToNumber(String.valueOf(obj), defaultValue);
    }

    /**
     * 将字符串转换为包装类型 Integer
     */
    public static Integer stringToNumber(String str, Integer defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 将字符串安全转换为 long
     */
    public static long stringToNumber(String str, long defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 将任意对象安全转换为 long
     */
    public static long stringToNumber(Object obj, long defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        return stringToNumber(String.valueOf(obj), defaultValue);
    }

    /**
     * 将字符串转换为包装类型 Long
     */
    public static Long stringToNumber(String str, Long defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.valueOf(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 将字符串安全转换为 double
     */
    public static double stringToNumber(String str, double defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 将任意对象安全转换为 double
     */
    public static double stringToNumber(Object obj, double defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        return stringToNumber(String.valueOf(obj), defaultValue);
    }

    /**
     * 将字符串转换为包装类型 Double
     */
    public static Double stringToNumber(String str, Double defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Double.valueOf(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 将字符串安全转换为 float
     */
    public static float stringToNumber(String str, float defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 将字符串转换为包装类型 Float
     */
    public static Float stringToNumber(String str, Float defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Float.valueOf(str.trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
