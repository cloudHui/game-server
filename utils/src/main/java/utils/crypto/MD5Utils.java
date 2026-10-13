package utils.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 散列消息摘要工具类。
 * <p>
 * 提供标准 32 位小写 MD5 散列字符串生成，严格采用 {@link StandardCharsets#UTF_8} 编码，杜绝跨操作系统字符集差异。
 * </p>
 *
 * @author cloud
 */
public class MD5Utils {

    /** 统一日志记录器 */
    private static final Logger LOGGER = LoggerFactory.getLogger(MD5Utils.class);

    public MD5Utils() {
    }

    /**
     * 计算输入字符串的标准 32 位小写 MD5 散列值。
     *
     * @param data 原始输入字符串
     * @return 32 位十六进制 MD5 字符串，入参为空时返回空串
     */
    public static String MD5(String data) {
        if (data == null || data.isEmpty()) {
            LOGGER.trace("[MD5] 输入数据为空，直接返回空串");
            return "";
        }
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            // 严格采用 UTF-8 编码，消除 Windows(GBK) 与 Linux(UTF-8) 字符编码不一致产生的哈希裂隙
            messageDigest.update(data.getBytes(StandardCharsets.UTF_8));
            byte[] d = messageDigest.digest();
            StringBuilder sb = new StringBuilder(32);
            for (byte b : d) {
                int val = b & 0xFF;
                if (val < 16) {
                    sb.append("0");
                }
                sb.append(Integer.toHexString(val));
            }
            String result = sb.toString();
            LOGGER.trace("[MD5] 散列计算完成: inputLen={}, hash={}", data.length(), result);
            return result;
        } catch (NoSuchAlgorithmException e) {
            LOGGER.error("[MD5] 运行环境缺少 MD5 算法支撑: input='{}'", data, e);
            return null;
        }
    }
}
