package http.client;

import org.apache.http.Header;
import org.apache.http.HeaderElement;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HTTP;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

/**
 * HTTP 客户端连接池工具类
 * <p>基于 Apache HttpClient 连接池实现，支持 HTTP/HTTPS、自定义超时、Header 注入与统一请求重试。</p>
 *
 * @author cloud
 */
public class HttpClientPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientPool.class);

    private static final String CONTENT_TYPE_JSON_URL = "application/json;charset=utf-8";
    private static final String CONTENT_TYPE_WWW_FORM = "application/x-www-form-urlencoded;charset=utf-8";

    private final String charset;
    private PoolingHttpClientConnectionManager pool;
    private ConnectionConfig connectionConfig;
    private RequestConfig requestConfig;
    private volatile CloseableHttpClient httpClient;

    public HttpClientPool() {
        this("UTF-8");
    }

    public HttpClientPool(String charset) {
        this(charset, 10);
    }

    public HttpClientPool(String charset, int timeout) {
        this.charset = charset;
        setConnectionConfig(4 * 1024);
        int timeoutMs = timeout * 1000;
        setTimeoutConfig(timeoutMs, timeoutMs, timeoutMs);
    }

    /**
     * 初始化连接池
     */
    public HttpClientPool init(int poolSize) {
        try {
            SSLContextBuilder builder = new SSLContextBuilder()
                    .loadTrustMaterial(null, new TrustSelfSignedStrategy());

            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(builder.build());
            Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.getSocketFactory())
                    .register("https", socketFactory).build();

            pool = new PoolingHttpClientConnectionManager(registry);
            pool.setMaxTotal(poolSize);
            pool.setDefaultMaxPerRoute(20);
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
            LOGGER.error("初始化 HttpClient 连接池失败", e);
        }
        return this;
    }

    public void setTimeoutConfig(int socketTimeout, int connectTimeout, int requestTimeout) {
        requestConfig = RequestConfig.custom()
                .setSocketTimeout(socketTimeout)
                .setConnectTimeout(connectTimeout)
                .setConnectionRequestTimeout(requestTimeout).build();
    }

    public void setConnectionConfig(int size) {
        connectionConfig = ConnectionConfig.custom()
                .setBufferSize(size)
                .build();
    }

    /**
     * 获取或懒加载构建 HttpClient 单例实例
     */
    public CloseableHttpClient getClient() {
        if (httpClient != null) {
            return httpClient;
        }
        synchronized (this) {
            if (httpClient == null) {
                httpClient = HttpClients.custom()
                        .setConnectionManager(pool)
                        .setDefaultRequestConfig(requestConfig)
                        .setDefaultConnectionConfig(connectionConfig)
                        .setKeepAliveStrategy((response, context) -> parseKeepAlive(response.getAllHeaders()))
                        .setRetryHandler(new DefaultHttpRequestRetryHandler())
                        .build();
            }
        }
        return httpClient;
    }

    private long parseKeepAlive(Header[] headers) {
        if (headers == null) return 10000L;
        for (Header h : headers) {
            HeaderElement[] elements = h.getElements();
            if (elements == null) continue;
            for (HeaderElement el : elements) {
                if (el.getName().toUpperCase().contains(HTTP.CONN_KEEP_ALIVE.toUpperCase())) {
                    if (el.getValue() != null && !el.getValue().isEmpty()) {
                        return Long.parseLong(el.getValue()) * 1000L;
                    }
                }
            }
        }
        return 10000L;
    }

    /**
     * 核心统一请求执行器（带资源安全自动释放）
     */
    private String executeRequest(HttpUriRequest request) {
        if (request instanceof HttpRequestBase && requestConfig != null) {
            ((HttpRequestBase) request).setConfig(requestConfig);
        }
        try (CloseableHttpResponse response = getClient().execute(request, HttpClientContext.create())) {
            if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
                LOGGER.error("HTTP 请求状态异常: {} URI: {}", response.getStatusLine().getStatusCode(), request.getURI());
                throw new RuntimeException("HTTP Request is not success, Response code is " + response.getStatusLine().getStatusCode());
            }
            HttpEntity entity = response.getEntity();
            return (entity != null) ? EntityUtils.toString(entity, charset) : null;
        } catch (Exception e) {
            LOGGER.error("HTTP 请求执行失败: {}", request.getURI(), e);
            return null;
        }
    }

    public String sendPost(HttpPost httpPost) {
        return executeRequest(httpPost);
    }

    public String sendGet(HttpGet httpGet) {
        return executeRequest(httpGet);
    }

    public String sendDelete(HttpDelete httpDelete) {
        return executeRequest(httpDelete);
    }

    public String sendPost(String url, Map<String, String> header) {
        if (isNullOrEmpty(url)) return null;
        HttpPost httpPost = new HttpPost(url);
        applyHeaders(httpPost, header);
        return sendPost(httpPost);
    }

    public String sendPostHeadBody(String url, Map<String, String> header, String content) {
        if (isNullOrEmpty(url)) return null;
        HttpPost httpPost = new HttpPost(url);
        applyHeaders(httpPost, header);
        if (!isNullOrEmpty(content)) {
            StringEntity entity = new StringEntity(content, charset);
            entity.setContentType(CONTENT_TYPE_JSON_URL);
            httpPost.setEntity(entity);
        }
        return sendPost(httpPost);
    }

    public String sendPost(String url, String content) {
        return sendPostHeadBody(url, null, content);
    }

    public String sendPosFormHeadBody(String url, Map<String, String> header, String content) {
        if (isNullOrEmpty(url)) return null;
        HttpPost httpPost = new HttpPost(url);
        applyHeaders(httpPost, header);
        if (!isNullOrEmpty(content)) {
            StringEntity entity = new StringEntity(content, charset);
            entity.setContentType(CONTENT_TYPE_WWW_FORM);
            httpPost.setEntity(entity);
        }
        return sendPost(httpPost);
    }

    public String sendPostForm(String url, String content) {
        return sendPosFormHeadBody(url, null, content);
    }

    public String sendGet(String url) {
        if (isNullOrEmpty(url)) return null;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("HTTP GET: {}", url);
        }
        return sendGet(new HttpGet(url));
    }

    public String sendGetHead(String url, Map<String, String> header) {
        if (isNullOrEmpty(url)) return null;
        HttpGet httpGet = new HttpGet(url);
        applyHeaders(httpGet, header);
        return sendGet(httpGet);
    }

    private void applyHeaders(HttpRequestBase request, Map<String, String> headers) {
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.addHeader(entry.getKey(), entry.getValue());
            }
        }
    }

    private static boolean isNullOrEmpty(String data) {
        return data == null || data.isEmpty();
    }
}
