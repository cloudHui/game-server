package com.cloud.weball.web.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 前端每分钟轮询：哪些能力可用。未就绪的联网玩法展示「敬请期待」。
 */
@RestController
public class CapabilitiesController {

    @Value("${gate.host:127.0.0.1}")
    private String gateHost;
    @Value("${gate.port:5600}")
    private int gatePort;
    @Value("${lobby.admin-http:http://127.0.0.1:5701}")
    private String lobbyAdminHttp;
    @Value("${capabilities.game-host:127.0.0.1}")
    private String gameHost;
    @Value("${capabilities.game-port:5500}")
    private int gamePort;
    @Value("${capabilities.center-host:127.0.0.1}")
    private String centerHost;
    @Value("${capabilities.center-port:5400}")
    private int centerPort;

    @GetMapping("/api/capabilities")
    public Map<String, Object> capabilities() {
        boolean gate = tcpOpen(gateHost, gatePort);
        boolean lobby = httpOpen(lobbyAdminHttp);
        boolean game = tcpOpen(gameHost, gamePort);
        boolean center = tcpOpen(centerHost, centerPort);
        boolean onlineTables = gate && lobby && game;
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
        result.put("message", onlineTables ? "全部就绪" : "联网牌桌暂未开放，敬请期待");
        return result;
    }

    private static boolean tcpOpen(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean httpOpen(String base) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(base.endsWith("/") ? base : base + "/");
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(400);
            conn.setReadTimeout(400);
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
