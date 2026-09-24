package utils.other;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/**
 * 接口签名与 AES-GCM 加解密工具类
 * <p>符合中宣部/实名认证系统及开放平台的 AES-GCM-128 加密与 SHA256 签名规范。</p>
 *
 * @author cloud
 */
public class SignUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignUtils.class);

    private static final String ALGORITHM = "AES/GCM/PKCS5Padding";
    private static final int TAG_LENGTH_BIT = 128;
    private static final int IV_LENGTH_BYTE = 12;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SignUtils() {
    }

    /**
     * AES-GCM-128 加密（实名认证系统规则）
     *
     * @param content 明文内容
     * @param hexKey  十六进制密钥
     * @return Base64 编码的密文（包含 12 字节 IV 前缀）
     */
    public static String encryptAu(String content, String hexKey) {
        try {
            byte[] keyBytes = HexUtils.decodeHex(hexKey.toCharArray());
            return doEncryptGcm(content, keyBytes);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM 加密失败", e);
        }
    }

    /**
     * AES-GCM-128 加密（通用接口）
     */
    public static String encrypt(String content, String hexKey) {
        try {
            byte[] keyBytes = HexUtils.decodeHex(hexKey);
            return doEncryptGcm(content, keyBytes);
        } catch (Exception e) {
            throw new RuntimeException("AES-GCM 加密失败", e);
        }
    }

    /**
     * 内部底层 AES-GCM 加密统一实现
     */
    private static String doEncryptGcm(String content, byte[] keyBytes) throws Exception {
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

        byte[] iv = new byte[IV_LENGTH_BYTE];
        SECURE_RANDOM.nextBytes(iv);

        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
        byte[] encrypted = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));

        ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
        buffer.put(iv);
        buffer.put(encrypted);
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    /**
     * 构建认证基础请求头/参数
     */
    public static Map<String, String> param(String appID, String bizID, String time) {
        Map<String, String> params = new HashMap<>();
        params.put("Content-Type", "application/json; charset=utf-8");
        params.put("appId", appID);
        params.put("bizId", bizID);
        params.put("timestamps", time);
        return params;
    }

    /**
     * 计算字典序 SHA256 签名
     */
    public static String sign(String secretKey, Map<String, String> params, String encryptData) {
        String signStr = parseMapString(params);
        signStr = secretKey + signStr + (encryptData != null ? encryptData : "");
        String sign = EncryptUtils.sha256(signStr);
        LOGGER.info("signStr:{} sign:{}", signStr, sign);
        return sign;
    }

    /**
     * 将参数按 Key 升序拼装
     */
    private static String parseMapString(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }
        List<String> sortedKeys = new ArrayList<>(params.keySet());
        Collections.sort(sortedKeys);
        StringBuilder sb = new StringBuilder();
        for (String key : sortedKeys) {
            if ("sign".equalsIgnoreCase(key) || "Content-Type".equalsIgnoreCase(key)) {
                continue;
            }
            sb.append(key).append(params.get(key));
        }
        return sb.toString();
    }

    /**
     * 获取对象的所有声明字段名
     */
    public static String[] getFiledName(Object o) {
        if (o == null) {
            return new String[0];
        }
        Field[] fields = o.getClass().getDeclaredFields();
        String[] fieldNames = new String[fields.length];
        for (int i = 0; i < fields.length; i++) {
            fieldNames[i] = fields[i].getName();
        }
        return fieldNames;
    }

    /**
     * 根据 getter 方法获取属性值
     */
    public static Object getFieldValueByName(String fieldName, Object o) {
        if (o == null || fieldName == null || fieldName.isEmpty()) {
            return null;
        }
        try {
            String getter = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            Method method = o.getClass().getMethod(getter);
            return method.invoke(o);
        } catch (Exception e) {
            return null;
        }
    }
}
