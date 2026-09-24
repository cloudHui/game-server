package utils.config;

import utils.other.ConfigPathUtils;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全局配置管理器
 * <p>负责加载并解析 app.properties，结构化映射为 ServerConfiguration 与 ConnectConfiguration。</p>
 *
 * @author cloud
 */
public class ConfigurationManager {

    private static final String FILE_NAME = "app.properties";
    private static final Pattern DEFAULT_PATTERN = Pattern.compile("([\\w\\d_]+)\\.([\\w\\d_]+)\\.([\\w\\d_]+)");
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("property\\.([\\w\\d_.]+)");

    private Map<String, ServerConfiguration> serverConfigurationMap = new HashMap<>();
    private Map<String, ConnectConfiguration> connectConfigurationMap = new HashMap<>();
    private Map<String, String> propertiesMap = new HashMap<>();

    private static class Holder {
        private static final ConfigurationManager INSTANCE = new ConfigurationManager();
    }

    public static ConfigurationManager getInstance() {
        return Holder.INSTANCE;
    }

    private ConfigurationManager() {
        load();
    }

    /**
     * 获取自定义配置项
     */
    public String getProperty(String name) {
        return this.propertiesMap.get(name);
    }

    public Map<String, ServerConfiguration> getServers() {
        return Collections.unmodifiableMap(this.serverConfigurationMap);
    }

    public Map<String, ConnectConfiguration> getConnects() {
        return Collections.unmodifiableMap(this.connectConfigurationMap);
    }

    /**
     * 重新加载配置文件
     */
    public synchronized void load() {
        Properties properties = new Properties();
        try (InputStream in = findConfigurationStream()) {
            if (in != null) {
                properties.load(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error! Failed to load " + FILE_NAME, e);
        }
        this.parse(properties);
    }

    /**
     * 多路径探测定位配置文件输入流
     */
    private InputStream findConfigurationStream() throws IOException {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(FILE_NAME);
        if (is != null) {
            return is;
        }
        String[] candidatePaths = {
                ConfigPathUtils.getConfigFilePath() + FILE_NAME,
                ConfigPathUtils.getResourceFilePath() + FILE_NAME,
                ConfigPathUtils.getProjectPath() + File.separator + FILE_NAME
        };
        for (String path : candidatePaths) {
            File f = new File(path);
            if (f.exists()) {
                return new FileInputStream(f);
            }
        }
        return null;
    }

    /**
     * 解析 Properties 属性集合
     */
    private void parse(Properties properties) {
        for (Entry<Object, Object> entry : properties.entrySet()) {
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            if (key.startsWith("server.")) {
                parseNamedConfig(key, value, serverConfigurationMap, ServerConfiguration.class);
            } else if (key.startsWith("connect.")) {
                parseNamedConfig(key, value, connectConfigurationMap, ConnectConfiguration.class);
            } else if (key.startsWith("property.")) {
                parseProperty(key, value);
            } else {
                this.propertiesMap.put(key, value);
            }
        }
    }

    /**
     * 通用结构化配置解析（整合 server.* 与 connect.*）
     */
    private <T> void parseNamedConfig(String key, String value, Map<String, T> map, Class<T> clazz) {
        Matcher matcher = DEFAULT_PATTERN.matcher(key);
        if (!matcher.matches() || matcher.groupCount() != 3) {
            throw new RuntimeException("Invalid configuration key format: " + key);
        }
        String name = matcher.group(2);
        String field = matcher.group(3);
        T item = map.computeIfAbsent(name, k -> {
            try {
                T obj = clazz.getDeclaredConstructor().newInstance();
                Field nameField = clazz.getDeclaredField("name");
                nameField.setAccessible(true);
                nameField.set(obj, name);
                return obj;
            } catch (Exception e) {
                throw new RuntimeException("Cannot instantiate " + clazz.getSimpleName(), e);
            }
        });
        try {
            setField(item, field, value);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Failed to parse config (%s=%s)", key, value), e);
        }
    }

    private void parseProperty(String key, String value) {
        Matcher matcher = PROPERTY_PATTERN.matcher(key);
        if (matcher.matches() && matcher.groupCount() >= 1) {
            this.propertiesMap.put(matcher.group(1), value);
        }
    }

    /**
     * 反射注入对象字段值
     */
    private void setField(Object object, String fieldName, String value) throws NoSuchFieldException, IllegalAccessException {
        Field field = object.getClass().getDeclaredField(fieldName);
        boolean accessible = field.isAccessible();
        field.setAccessible(true);
        try {
            field.set(object, parseValue(field.getType(), value));
        } finally {
            field.setAccessible(accessible);
        }
    }

    /**
     * 基础数据类型反射转换
     */
    private Object parseValue(Class<?> type, String value) {
        if (type == String.class) {
            return value;
        }
        if (type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        }
        if (type == long.class || type == Long.class) {
            return Long.parseLong(value);
        }
        if (type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        }
        if (type == double.class || type == Double.class) {
            return Double.parseDouble(value);
        }
        if (type == float.class || type == Float.class) {
            return Float.parseFloat(value);
        }
        if (type == byte.class || type == Byte.class) {
            return Byte.parseByte(value);
        }
        if (type == short.class || type == Short.class) {
            return Short.parseShort(value);
        }
        throw new UnsupportedOperationException("Unsupported field type: " + type.getName());
    }

    public Integer getInt(String name, Integer defaultValue) {
        String property = this.getProperty(name);
        return property == null ? defaultValue : Integer.parseInt(property);
    }

    @Override
    public String toString() {
        return "ConfigurationManager{" +
                "serverConfigurationMap=" + serverConfigurationMap +
                ", connectConfigurationMap=" + connectConfigurationMap +
                ", propertiesMap=" + propertiesMap +
                '}';
    }
}
