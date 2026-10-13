package utils.json;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;

/**
 * 统一 JSON 序列化与反序列化工具类。
 * <p>
 * 基于 Jackson ObjectMapper 封装，默认忽略空字段、未知属性兼容、标准 Java 8 时间支持，并提供完备的防御性日志与空指针防护。
 * </p>
 *
 * @author cloud
 */
public class JsonUtils {

    /** 统一日志记录器 */
    private static final Logger LOGGER = LoggerFactory.getLogger(JsonUtils.class);

    /** 全局共享单例 ObjectMapper */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public JsonUtils() {
    }

    /**
     * 获取全局底层 ObjectMapper 实例。
     *
     * @return ObjectMapper
     */
    public static ObjectMapper getMapper() {
        return MAPPER;
    }

    /**
     * 将对象序列化并写入指定输出流。
     *
     * @param out   输出流
     * @param value 待序列化对象
     */
    public static void writeValue(OutputStream out, Object value) {
        if (out == null) {
            LOGGER.warn("[writeValue] 输出流为 null，忽略写入");
            return;
        }
        try {
            MAPPER.writeValue(out, value);
            LOGGER.trace("[writeValue] 写入流成功: type={}", value != null ? value.getClass().getName() : "null");
        } catch (IOException e) {
            LOGGER.error("[writeValue] 序列化对象至输出流失败: value={}", value != null ? value.getClass().getName() : "null", e);
        }
    }

    /**
     * 将 JSON 文本反序列化为指定类型对象。
     *
     * @param content    JSON 文本内容
     * @param objectType 目标类 Class
     * @param <T>        目标实体类型
     * @return 反序列化后的实体，解析异常时返回 null
     */
    public static <T> T readValue(String content, Class<T> objectType) {
        if (content == null || content.trim().isEmpty() || objectType == null) {
            LOGGER.trace("[readValue] JSON 内容或目标类型为空: type={}", objectType != null ? objectType.getSimpleName() : "null");
            return null;
        }
        try {
            T result = MAPPER.readValue(content, objectType);
            LOGGER.trace("[readValue] 反序列化成功: type={}", objectType.getSimpleName());
            return result;
        } catch (IOException e) {
            LOGGER.error("[readValue] JSON 反序列化失败: type={}, content='{}'", objectType.getName(), content, e);
            return null;
        }
    }

    /**
     * 将任意对象安全序列化为 JSON 字符串。
     *
     * @param value 待序列化对象
     * @return JSON 文本字符串，解析失败时返回 null
     */
    public static String writeValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            String json = MAPPER.writeValueAsString(value);
            LOGGER.trace("[writeValue] 序列化为 JSON 字符串成功: type={}, len={}", value.getClass().getSimpleName(), json.length());
            return json;
        } catch (JsonProcessingException e) {
            LOGGER.error("[writeValue] 对象序列化为 JSON 文本失败: type={}", value.getClass().getName(), e);
            return null;
        }
    }

    static {
        MAPPER.setSerializationInclusion(Include.NON_NULL);
        MAPPER.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        MAPPER.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        MAPPER.registerModule(new JavaTimeModule());
        LOGGER.debug("[init] JsonUtils ObjectMapper 配置初始化完成");
    }
}
