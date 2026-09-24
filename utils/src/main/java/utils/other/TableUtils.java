package utils.other;

import java.sql.Timestamp;
import java.util.*;

/**
 * 策划配表与键值映射解析工具类
 * <p>提供 Map 字段的安全提取以及字符串与基础数组、集合之间的双向解析转换。</p>
 *
 * @author cloud
 */
public class TableUtils {

    /** 单例对象 */
    private static final TableUtils INSTANCE = new TableUtils();

    private TableUtils() {
    }

    /**
     * 获取单例实例
     *
     * @return TableUtils 实例
     */
    public static TableUtils getInstance() {
        return INSTANCE;
    }

    /**
     * 从 Map 中安全提取字符串值
     *
     * @param map 数据字典
     * @param key 字段键名
     * @return 字符串值，若不存在或为空则返回 null
     */
    public String getString(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object val = map.get(key);
        return val != null ? String.valueOf(val) : null;
    }

    /**
     * 从 HashMap 中获取字符串值（重载兼容）
     */
    public String getString(HashMap<String, Object> map, String key) {
        return getString((Map<String, Object>) map, key);
    }

    /**
     * 从 Map 中安全提取浮点数值
     *
     * @param map 数据字典
     * @param key 字段键名
     * @return 浮点数值，不存在则默认返回 -1.0f
     */
    public float getFloat(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return -1.0f;
        }
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).floatValue();
        }
        if (val != null) {
            try {
                return Float.parseFloat(String.valueOf(val));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1.0f;
    }

    public float getFloat(HashMap<String, Object> map, String key) {
        return getFloat((Map<String, Object>) map, key);
    }

    /**
     * 从 Map 中安全提取整型数值
     *
     * @param map 数据字典
     * @param key 字段键名
     * @return 整数值，不存在则默认返回 -1
     */
    public int getInt(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return -1;
        }
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        if (val != null) {
            try {
                return Integer.parseInt(String.valueOf(val));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    public int getInt(HashMap<String, Object> map, String key) {
        return getInt((Map<String, Object>) map, key);
    }

    /**
     * 从 Map 中安全提取长整型数值
     *
     * @param map 数据字典
     * @param key 字段键名
     * @return 长整型值，不存在则默认返回 -1L
     */
    public long getLong(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return -1L;
        }
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).longValue();
        }
        if (val != null) {
            try {
                return Long.parseLong(String.valueOf(val));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1L;
    }

    public long getLong(HashMap<String, Object> map, String key) {
        return getLong((Map<String, Object>) map, key);
    }

    /**
     * 从 Map 中安全提取布尔值
     *
     * @param map 数据字典
     * @param key 字段键名
     * @return 布尔值，不存在则默认返回 false
     */
    public Boolean getBoolean(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return false;
        }
        Object val = map.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return val != null && Boolean.parseBoolean(String.valueOf(val));
    }

    public Boolean getBoolean(HashMap<String, Object> map, String key) {
        return getBoolean((Map<String, Object>) map, key);
    }

    /**
     * 从 Map 中提取 Timestamp 时间戳
     */
    public Timestamp getTimestamp(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object val = map.get(key);
        return (val instanceof Timestamp) ? (Timestamp) val : null;
    }

    public Timestamp getTimestamp(HashMap<String, Object> map, String key) {
        return getTimestamp((Map<String, Object>) map, key);
    }

    public String[] getStringArray(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object val = map.get(key);
        return (val instanceof String[]) ? (String[]) val : null;
    }

    public String[] getStringArray(HashMap<String, Object> map, String key) {
        return getStringArray((Map<String, Object>) map, key);
    }

    public int[] getIntArray(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object val = map.get(key);
        return (val instanceof int[]) ? (int[]) val : null;
    }

    public int[] getIntArray(HashMap<String, Object> map, String key) {
        return getIntArray((Map<String, Object>) map, key);
    }

    public long[] getLongArray(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object val = map.get(key);
        return (val instanceof long[]) ? (long[]) val : null;
    }

    public long[] getLongArray(HashMap<String, Object> map, String key) {
        return getLongArray((Map<String, Object>) map, key);
    }

    public float[] getFloatArray(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object val = map.get(key);
        return (val instanceof float[]) ? (float[]) val : null;
    }

    public float[] getFloatArray(HashMap<String, Object> map, String key) {
        return getFloatArray((Map<String, Object>) map, key);
    }

    public int[][] getIntArrayArray(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object val = map.get(key);
        return (val instanceof int[][]) ? (int[][]) val : null;
    }

    public float[][] getFloatArrayArray(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object val = map.get(key);
        return (val instanceof float[][]) ? (float[][]) val : null;
    }

    // ----------------------- 静态解析与序列化方法 -----------------------

    /**
     * 将字符串列表转换为逗号分隔字符串
     *
     * @param arrayStr 字符串列表
     * @return 逗号拼接结果
     */
    public static String StringArrayToString(List<String> arrayStr) {
        if (arrayStr == null || arrayStr.isEmpty()) {
            return "";
        }
        return String.join(",", arrayStr);
    }

    /**
     * 解析形如 "[a,b,c]" 或 "(a,b)" 的字符串为字符串数组
     *
     * @param value 原始文本
     * @return 解析后的字符串数组
     */
    public static String[] StringArrayParse(String value) {
        if (value == null) {
            return new String[0];
        }
        String clean = value.trim()
                .replace("(", "")
                .replace(")", "")
                .replace("[", "")
                .replace("]", "");
        if (clean.isEmpty()) {
            return new String[0];
        }
        return clean.split(",");
    }

    /**
     * 解析时间戳字符串并按时区转换
     *
     * @param value 时间戳文本
     * @return Timestamp 对象
     */
    public static Timestamp TimeStampParse(String value) {
        Timestamp timestamp = Timestamp.valueOf(value);
        TimeZone curTimeZone = TimeZone.getDefault();
        int offset = curTimeZone.getOffset(System.currentTimeMillis());
        long realTime;
        if ("GMT+8".equals(TimeUtil.logicTimeZone)) {
            realTime = timestamp.getTime() - (8 * TimeUtil.HOUR - offset);
        } else {
            realTime = timestamp.getTime() + offset;
        }
        return new Timestamp(Math.max(0, realTime));
    }

    /**
     * 解析逗号分隔的整数数组
     */
    public static int[] IntArrayParse(String value) {
        String[] intStrArray = StringArrayParse(value);
        int[] intArray = new int[intStrArray.length];
        for (int i = 0; i < intStrArray.length; i++) {
            intArray[i] = Integer.parseInt(intStrArray[i].trim());
        }
        return intArray;
    }

    /**
     * 将整型数组转为 "[1,2,3]" 格式
     */
    public static String IntArrayToString(int[] intArray) {
        if (intArray == null || intArray.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < intArray.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(intArray[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 解析整型列表
     */
    public static List<Integer> IntegerListParse(String str) {
        List<Integer> list = new ArrayList<>();
        if (str == null || str.trim().isEmpty()) {
            return list;
        }
        for (String s : StringArrayParse(str)) {
            list.add(Integer.parseInt(s.trim()));
        }
        return list;
    }

    /**
     * 将整型列表转为 "[1,2,3]" 格式
     */
    public static String IntegerListToString(List<Integer> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 解析长整型数组
     */
    public static long[] LongArrayParse(String value) {
        String[] strArray = StringArrayParse(value);
        long[] result = new long[strArray.length];
        for (int i = 0; i < strArray.length; i++) {
            result[i] = Long.parseLong(strArray[i].trim());
        }
        return result;
    }

    /**
     * 将长整型数组转为 "[1,2,3]" 格式
     */
    public static String LongArrayToString(long[] longArray) {
        if (longArray == null || longArray.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < longArray.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(longArray[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 解析浮点型数组
     */
    public static float[] FloatArrayParse(String value) {
        String[] strArray = StringArrayParse(value);
        float[] result = new float[strArray.length];
        for (int i = 0; i < strArray.length; i++) {
            result[i] = Float.parseFloat(strArray[i].trim());
        }
        return result;
    }

    /**
     * 解析时间戳数组
     */
    public static Timestamp[] TimestampArrayParse(String value) {
        String[] strArray = reFormatString(value);
        Timestamp[] array = new Timestamp[strArray.length];
        for (int i = 0; i < strArray.length; i++) {
            array[i] = TimeStampParse(strArray[i]);
        }
        return array;
    }

    /**
     * 解析二维整型数组
     */
    public static int[][] IntArrayArrayParse(String value) {
        String[] strArray = reFormatString(value);
        int[][] result = new int[strArray.length][];
        for (int i = 0; i < strArray.length; i++) {
            result[i] = IntArrayParse(strArray[i]);
        }
        return result;
    }

    /**
     * 解析二维长整型数组
     */
    public static long[][] LongArrayArrayParse(String value) {
        String[] strArray = reFormatString(value);
        long[][] result = new long[strArray.length][];
        for (int i = 0; i < strArray.length; i++) {
            result[i] = LongArrayParse(strArray[i]);
        }
        return result;
    }

    /**
     * 解析二维浮点数组
     */
    public static float[][] FloatArrayArrayParse(String value) {
        String[] strArray = reFormatString(value);
        float[][] result = new float[strArray.length][];
        for (int i = 0; i < strArray.length; i++) {
            result[i] = FloatArrayParse(strArray[i]);
        }
        return result;
    }

    /**
     * 解析二维字符串数组
     */
    public static String[][] StringArrayArrayParse(String value) {
        String[] strArray = reFormatString(value);
        String[][] result = new String[strArray.length][];
        for (int i = 0; i < strArray.length; i++) {
            result[i] = StringArrayParse(strArray[i]);
        }
        return result;
    }

    /**
     * 长整型列表转逗号分隔字符串
     */
    public static String listToString(List<Long> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",");
        for (Long id : list) {
            joiner.add(String.valueOf(id));
        }
        return joiner.toString();
    }

    /**
     * 二维复合格式规整（支持括号分组或多维中括号）
     */
    private static String[] reFormatString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return new String[0];
        }
        String clean = value.trim();
        if (clean.contains("(")) {
            clean = clean.replace("[", "").replace("]", "")
                    .replace("),", ")|")
                    .replace("(", "").replace(")", "");
        } else {
            clean = clean.replace("[[", "").replace("]]", "")
                    .replace("],", "]|")
                    .replace("[", "").replace("]", "");
        }
        if (clean.isEmpty()) {
            return new String[0];
        }
        return clean.split("\\|");
    }

    /**
     * 解析长整型列表
     */
    public static List<Long> LongListParse(String str) {
        List<Long> list = new ArrayList<>();
        if (str == null || str.trim().isEmpty()) {
            return list;
        }
        for (String s : StringArrayParse(str)) {
            list.add(Long.parseLong(s.trim()));
        }
        return list;
    }

    /**
     * 将长整型列表转为 "[1,2,3]" 格式
     */
    public static String LongListToString(List<Long> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 过滤列表中为空的字符串项
     */
    public static List<String> filterEmptyString(List<String> inList) {
        if (inList == null) {
            return Collections.emptyList();
        }
        List<String> list = new ArrayList<>();
        for (String s : inList) {
            if (s != null && !s.isEmpty()) {
                list.add(s);
            }
        }
        return list;
    }

    public static List<String> filterEmptyString(String[] inArray) {
        return inArray != null ? filterEmptyString(Arrays.asList(inArray)) : Collections.emptyList();
    }

    public static ArrayList<Integer> arrayToList(int[] value) {
        if (value == null) {
            return new ArrayList<>();
        }
        ArrayList<Integer> list = new ArrayList<>(value.length);
        for (int j : value) {
            list.add(j);
        }
        return list;
    }

    public static int[] listToArray(List<Integer> value) {
        if (value == null || value.isEmpty()) {
            return new int[0];
        }
        int[] arr = new int[value.size()];
        for (int i = 0; i < value.size(); i++) {
            arr[i] = value.get(i);
        }
        return arr;
    }

    /**
     * 解析点分隔版本号为整型列表（如 "1.0.3" -> [1, 0, 3]）
     */
    public static List<Integer> IntListVersionParse(String value) {
        List<Integer> list = new ArrayList<>();
        if (value == null || value.trim().isEmpty() || "[]".equals(value)) {
            return list;
        }
        for (String s : value.split("\\.")) {
            list.add(Integer.valueOf(s.trim()));
        }
        return list;
    }

    /**
     * 解析字符串为整型列表
     */
    public static List<Integer> IntListParse(String value) {
        List<Integer> list = new ArrayList<>();
        if (value == null || value.trim().isEmpty() || "[]".equals(value)) {
            return list;
        }
        for (String s : StringArrayParse(value)) {
            list.add(Integer.parseInt(s.trim()));
        }
        return list;
    }

    /**
     * 解析二维长整型列表
     */
    public static List<long[]> listLongArrayArrayParse(String value) {
        String[] strArray = reFormatString(value);
        List<long[]> list = new ArrayList<>(strArray.length);
        for (String s : strArray) {
            list.add(LongArrayParse(s));
        }
        return list;
    }

    /**
     * 浮点映射转字符串 "[ (1.0, 2.0), (3.0, 4.0) ]"
     */
    public static String mapToString(Map<Float, Float> map) {
        if (map == null || map.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (Map.Entry<Float, Float> entry : map.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            sb.append("(").append(entry.getKey()).append(",").append(entry.getValue()).append(")");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }
}
