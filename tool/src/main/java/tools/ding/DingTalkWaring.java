package tools.ding;

import com.alibaba.fastjson.JSON;
import http.client.HttpClientPool;
import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 钉钉群自定义机器人告警工具类（轻量零 SDK 实现）。
 * <p>
 * 基于项目已有的 HttpClientPool 连接池与标准 HmacSHA256 签名算法直接调用钉钉 Webhook，
 * 彻底解绑 4.1MB 的私有 taobao-sdk-java-auto.jar 依赖。
 *
 * @author cloud
 */
public class DingTalkWaring {

    /** 日志记录器 */
    private static final Logger LOGGER = LoggerFactory.getLogger(DingTalkWaring.class);

    /** HTTP 连接池（UTF-8 编码，超时 5 秒） */
    private static final HttpClientPool HTTP_POOL = new HttpClientPool("UTF-8", 5);

    /** 钉钉机器人的 Webhook AccessToken */
    private final String accessToken;

    /** 钉钉机器人安全设置中的加签密钥 Secret */
    private final String secret;

    /**
     * 使用默认预设群机器人构建告警实例。
     */
    public DingTalkWaring() {
        this("c025bd04abf39f4b19aca4ac4d277b073619d231cde1875f458e3bb10badf80b",
             "SEC74fa5d21ccd682f91446220528864a4bf00d580e82e15ee38eec3be18b6374b5");
    }

    /**
     * 使用指定的自定义机器人参数构建告警实例。
     *
     * @param accessToken 机器人 AccessToken
     * @param secret      机器人加签 Secret
     */
    public DingTalkWaring(String accessToken, String secret) {
        this.accessToken = accessToken;
        this.secret = secret;
    }

    /**
     * 发送普通文本告警（不 @ 任何人）。
     *
     * @param message 要发送的报警消息文本
     */
    public void sendMsg(String message) {
        sendMsg(message, null);
    }

    /**
     * 发送钉钉文本告警消息。
     *
     * @param message 要发送的报警消息文本
     * @param phones  要 @ 的手机号列表，多个以英文逗号隔开
     */
    public void sendMsg(String message, String phones) {
        long timestamp = System.currentTimeMillis();
        Map<String, Object> payload = new HashMap<>();
        payload.put("msgtype", "text");

        Map<String, Object> textObj = new HashMap<>();
        textObj.put("content", message);
        payload.put("text", textObj);

        // 如果配置了手机号，则构建 @ 节点
        if (phones != null && !phones.trim().isEmpty()) {
            Map<String, Object> atObj = new HashMap<>();
            List<String> atMobiles = new ArrayList<>();
            for (String p : phones.split(",")) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) {
                    atMobiles.add(trimmed);
                }
            }
            atObj.put("atMobiles", atMobiles);
            atObj.put("isAtAll", false);
            payload.put("at", atObj);
        }

        try {
            // 拼装钉钉 Webhook 加签 URL: access_token + timestamp + sign
            String url = "https://oapi.dingtalk.com/robot/send?access_token=" + accessToken
                    + "&timestamp=" + timestamp
                    + "&sign=" + getSign(timestamp, secret);

            String jsonBody = JSON.toJSONString(payload);
            // 通过 HttpClientPool 发送 POST 请求，底层已设置 Content-Type 为 application/json
            String response = HTTP_POOL.sendPost(url, jsonBody);
            LOGGER.info("[DingTalk sendMsg] url: {}, param: {}, res: {}, cost: {}ms",
                    url, jsonBody, response, (System.currentTimeMillis() - timestamp));
        } catch (Exception e) {
            LOGGER.error("发送钉钉告警消息失败: {}", message, e);
        }
    }

    /**
     * 计算钉钉 Webhook 要求的 HmacSHA256 签名。
     *
     * @param timestamp 当前毫秒时间戳
     * @param secret    加签密钥
     * @return 经过 Base64 与 URLEncoder 编码的签名串
     */
    private static String getSign(long timestamp, String secret)
            throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        return URLEncoder.encode(new String(Base64.encodeBase64(signData)), "UTF-8");
    }

    public static void main(String[] args) {
        DingTalkWaring dingTalkWaring = new DingTalkWaring();
        dingTalkWaring.sendMsg("【测试】钉钉轻量HTTP通知", "17671292550");
    }
}
