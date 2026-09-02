package web.arpu;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
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

/** 管理后台 ARPU 查询代理：服务端请求外部接口，规避浏览器跨域限制。 */
@Service
public class ArpuLookupService {
    private final ObjectMapper mapper;
    private final String checkUrl;
    private final Executor executor;

    public ArpuLookupService(ObjectMapper mapper,
                             @Value("${arpu.check-url:https://arpu.151365.cc/check}") String checkUrl,
                             @Qualifier("arpuExecutor") Executor executor) {
        this.mapper = mapper;
        this.checkUrl = checkUrl;
        this.executor = executor;
    }

    /** 把阻塞式上游查询移到专用线程池，Controller 仍以异步响应返回。 */
    public CompletableFuture<Map<String, Object>> checkAsync(String phoneNo) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return check(phoneNo);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    /** 只把手机号拼到 phone_no 参数，原始 JSON 交给后台展示。 */
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

    public String requestUrl(String phoneNo) {
        try {
            return checkUrl + "?phone_no=" + URLEncoder.encode(phoneNo, "UTF-8");
        } catch (Exception e) {
            return checkUrl + "?phone_no=" + phoneNo;
        }
    }

    private HttpURLConnection open(String phoneNo) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl(phoneNo)).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Encoding", "identity");
        return connection;
    }

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
