package web.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 分布式 Web 网关服务能力探活与健康检测控制器。
 * <p>
 * 负责探测远端分布式微服务集群（Gate 网关、Lobby 大厅、Game 对局引擎、Center 注册中心）的连通性。
 * 针对高频轮询场景引入并发异步探测与内存级短期缓存（TTL 3s），避免串行阻塞导致 Web 请求线程耗尽。
 *
 * @author cloud
 */
@RestController
public class CapabilitiesController {

    private static final Logger logger = LoggerFactory.getLogger(CapabilitiesController.class);

    /** 单个节点探测网络超时时间（毫秒） */
    private static final int PROBE_TIMEOUT_MS = 300;

    /** 探活状态内存缓存有效期（毫秒） */
    private static final long CACHE_TTL_MS = 3000L;

    /** 网关服务主机名 */
    @Value("${gate.host:127.0.0.1}")
    private String gateHost;

    /** 网关服务 TCP 端口 */
    @Value("${gate.port:5600}")
    private int gatePort;

    /** 大厅后台 HTTP 地址 */
    @Value("${lobby.admin-http:http://127.0.0.1:5701}")
    private String lobbyAdminHttp;

    /** 对局核心服务主机名 */
    @Value("${capabilities.game-host:127.0.0.1}")
    private String gameHost;

    /** 对局核心服务 TCP 端口 */
    @Value("${capabilities.game-port:5500}")
    private int gamePort;

    /** 注册中心主机名 */
    @Value("${capabilities.center-host:127.0.0.1}")
    private String centerHost;

    /** 注册中心 TCP 端口 */
    @Value("${capabilities.center-port:5400}")
    private int centerPort;

    /** 上次探测缓存结果 */
    private volatile CachedSnapshot cachedSnapshot;

    /**
     * 内部缓存切片封装。
     */
    private static class CachedSnapshot {
        final long timestamp;
        final Map<String, Object> data;

        CachedSnapshot(long timestamp, Map<String, Object> data) {
            this.timestamp = timestamp;
            this.data = data;
        }
    }

    /**
     * 查询分布式集群整体可用性及各节点健康切片。
     *
     * @return 包含各服务连通性与精准离线原因的结果映射表
     */
    @GetMapping("/api/capabilities")
    public Map<String, Object> capabilities() {
        long now = System.currentTimeMillis();
        CachedSnapshot snapshot = cachedSnapshot;
        // 缓存命中：3 秒内复用检测结果，防止多客户端并发探活打爆 Socket 连接池
        if (snapshot != null && (now - snapshot.timestamp) < CACHE_TTL_MS) {
            return snapshot.data;
        }

        // 并行异步并发探活，避免原先串行阻塞累计 1600ms 造成 Tomcat 线程饥饿
        CompletableFuture<Boolean> gateFuture = CompletableFuture.supplyAsync(() -> tcpOpen(gateHost, gatePort));
        CompletableFuture<Boolean> lobbyFuture = CompletableFuture.supplyAsync(() -> httpOpen(lobbyAdminHttp));
        CompletableFuture<Boolean> gameFuture = CompletableFuture.supplyAsync(() -> tcpOpen(gameHost, gamePort));
        CompletableFuture<Boolean> centerFuture = CompletableFuture.supplyAsync(() -> tcpOpen(centerHost, centerPort));

        boolean gate = getOrDefault(gateFuture);
        boolean lobby = getOrDefault(lobbyFuture);
        boolean game = getOrDefault(gameFuture);
        boolean center = getOrDefault(centerFuture);

        boolean onlineTables = gate && lobby && game;

        // 收集精准离线服务名称，辅助运维与前端精确定位排查
        List<String> offlineComponents = new ArrayList<>();
        if (!gate) offlineComponents.add("网关(Gate:" + gatePort + ")");
        if (!lobby) offlineComponents.add("大厅(Lobby:5701)");
        if (!game) offlineComponents.add("对局核心(Game:" + gamePort + ")");

        String message;
        if (onlineTables) {
            message = "全部就绪";
        } else {
            message = "联网服务尚未启动: " + String.join("、", offlineComponents) + " 未就绪";
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("web", true);
        result.put("learning", true);
        result.put("center", center);
        result.put("gate", gate);
        result.put("lobby", lobby);
        result.put("game", game);
        result.put("onlineTables", onlineTables);
        result.put("miniOnline", true);
        result.put("miniLocal", true);
        result.put("message", message);
        result.put("offline", offlineComponents);

        cachedSnapshot = new CachedSnapshot(now, result);
        return result;
    }

    private static boolean getOrDefault(CompletableFuture<Boolean> future) {
        try {
            return future.get(PROBE_TIMEOUT_MS + 100, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * TCP 端口可用性探测。
     */
    private static boolean tcpOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROBE_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * HTTP 探活请求检测。
     */
    private static boolean httpOpen(String base) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(base.endsWith("/") ? base : base + "/");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(PROBE_TIMEOUT_MS);
            conn.setReadTimeout(PROBE_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            int code = conn.getResponseCode();
            return code > 0 && code < 500;
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}
