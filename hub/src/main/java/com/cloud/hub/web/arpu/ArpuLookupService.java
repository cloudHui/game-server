package com.cloud.hub.web.arpu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * 管理后台 ARPU 查询代理服务。
 * <p>
 * 由服务端发起对外部 ARPU 接口的 HTTP 查询，规避浏览器端的跨域安全限制，
 * 并支持通过专用线程池进行异步无阻塞查询。
 *
 * @author cloud
 */
@Service
public class ArpuLookupService {

    private final ObjectMapper mapper;
    private final String checkUrl;
    private final Executor executor;

    /**
     * 构造 ARPU 查询代理服务。
     *
     * @param mapper   Jackson 序列化工具
     * @param checkUrl 外部查询接口 URL
     * @param executor ARPU 专用异步线程池
     */
    public ArpuLookupService(ObjectMapper mapper,
                             @Value("${arpu.check-url:https://arpu.151365.cc/check}") String checkUrl,
                             @Qualifier("arpuExecutor") Executor executor) {
        this.mapper = mapper;
        this.checkUrl = checkUrl;
        this.executor = executor;
    }

    /**
     * 异步查询指定手机号的 ARPU 详情。
     *
     * @param phoneNo 手机号
     * @return 异步 CompletableFuture 字典
     */
    public CompletableFuture<Map<String, Object>> checkAsync(String phoneNo) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return check(phoneNo);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * 同步查询指定手机号的 ARPU 详情。
     *
     * @param phoneNo 手机号
     * @return 接口返回的原始 JSON 转换的字典
     * @throws IOException 网络或解析异常
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> check(String phoneNo) throws IOException {
        HttpURLConnection connection = open(phoneNo);
        try {
            int status = connection.getResponseCode();
            String body = readBody(connection, status);
            if (status < 200 || status >= 300) {
                throw new IOException("ARPU 接口返回 HTTP " + status);
            }
            if (body.trim().isEmpty()) {
                throw new IOException("ARPU 接口返回为空");
            }
            return mapper.readValue(body, Map.class);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 组装带参的请求 URL。
     *
     * @param phoneNo 手机号
     * @return 完整 URL
     */
    public String requestUrl(String phoneNo) {
        try {
            return checkUrl + "?phone_no=" + URLEncoder.encode(phoneNo, "UTF-8");
        } catch (Exception e) {
            return checkUrl + "?phone_no=" + phoneNo;
        }
    }

    /**
     * 打开 HTTP 物理连接。
     */
    private HttpURLConnection open(String phoneNo) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl(phoneNo)).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Encoding", "identity");
        return connection;
    }

    /**
     * 读取 HTTP 响应文本内容。
     */
    private String readBody(HttpURLConnection connection, int status) throws IOException {
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }
        }
        return body.toString();
    }
}
