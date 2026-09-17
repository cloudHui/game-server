package com.gamer.data.mpcserver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.CommandDispatcher;
import com.gamer.data.mpcserver.log.DailyFileLog;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** 仅监听本机的单进程 Streamable HTTP MCP 服务。 */
public final class McpServerMain {
    private static final int MAX_REQUEST = 8 * 1024 * 1024;
    /** HTTP 工作线程数，/mcp 与 /health 共用。 */
    private static final int HTTP_WORKER_COUNT = 8;
    /** /mcp 最多占用的 HTTP 线程，预留 1 条给 /health。 */
    private static final int MCP_MAX_INFLIGHT = HTTP_WORKER_COUNT - 1;
    /** /mcp 并发准入，满员时立即 503，给 /health 留一条 HTTP 线程。 */
    private static final Semaphore MCP_PERMITS = new Semaphore(MCP_MAX_INFLIGHT);

    private McpServerMain() {
    }

    public static void main(String[] args) throws Exception {
        McpConfig config = new McpConfig();
        DailyFileLog log = new DailyFileLog(config.logDir);
        ObjectMapper mapper = new ObjectMapper();
        CommandContext context = config.context(mapper, log);
        CommandDispatcher dispatcher = new CommandDispatcher();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", McpConfig.PORT), 0);
        ExecutorService httpWorkers = Executors.newFixedThreadPool(HTTP_WORKER_COUNT);
        ExecutorService toolWorkers = Executors.newFixedThreadPool(4);
        ExecutorService excelWorkers = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.SECONDS,
                new SynchronousQueue<>(), new ThreadPoolExecutor.AbortPolicy());
        McpServer mcp = new McpServer(dispatcher, context, toolWorkers, excelWorkers,
            McpConfig.TOOL_TIMEOUT_SECONDS, McpConfig.EXCEL_TIMEOUT_SECONDS);
        server.setExecutor(httpWorkers);
        server.createContext("/mcp", exchange -> handleMcp(exchange, mcp, context));
        server.createContext("/health", exchange -> send(exchange, 200, "text/plain; charset=utf-8", "OK"));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop(0);
            httpWorkers.shutdownNow();
            toolWorkers.shutdownNow();
            excelWorkers.shutdownNow();
            log.close();
        }, "mcp-shutdown"));
        server.start();
        log.logMessage("[mcp] listening http://127.0.0.1:" + McpConfig.PORT + "/mcp workspace="
            + config.workDir.getPath() + " allowed=" + context.fileSandbox().allowedRootPaths()
            + " toolTimeout=" + McpConfig.TOOL_TIMEOUT_SECONDS + "s excelTimeout="
            + McpConfig.EXCEL_TIMEOUT_SECONDS + "s");
    }

    /**
     * 处理一个 MCP 请求。
     * 
     * @param exchange
     *            HTTP 交换
     * @param mcp
     *            MCP 服务器
     * @param context
     *           命令上下文
     * @throws IOException
     *             异常
     */
    private static void handleMcp(HttpExchange exchange, McpServer mcp, CommandContext context) throws IOException {
        // 无许可立刻 503，避免 8 条 HTTP 线程都被 /mcp 的 future.get 占满导致 /health 堵死
        if (!MCP_PERMITS.tryAcquire()) {
            send(exchange, 503, "text/plain; charset=utf-8", "MCP busy");
            return;
        }
        try {
            if (!localOrigin(exchange)) {
                send(exchange, 403, "text/plain; charset=utf-8", "Forbidden origin");
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                send(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed");
                return;
            }
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.regionMatches(true, 0, "application/json", 0, 16)) {
                send(exchange, 415, "text/plain; charset=utf-8", "Content-Type must be application/json");
                return;
            }
            try {
                String response = mcp.handleHttp(read(exchange.getRequestBody()));
                send(exchange, response == null ? 202 : 200, "application/json; charset=utf-8", response);
            } catch (Exception e) {
                boolean tooLarge = "Request too large".equals(e.getMessage());
                context.log().logMessage("[mcp] http error=" + e);
                send(exchange, tooLarge ? 413 : 500, "text/plain; charset=utf-8",
                    tooLarge ? e.getMessage() : "Internal Server Error");
            }
        } finally {
            MCP_PERMITS.release();
        }
    }

    /**
     * 处理一个本地来源请求。
     * 
     * @param exchange
     *            HTTP 交换
     * @return 是否本地来源
     */
    private static boolean localOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isEmpty() || "null".equals(origin)) {
            return true;
        }
        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            return "http".equalsIgnoreCase(uri.getScheme())
                && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 读取输入流。
     * 
     * @param input
     *            输入流
     * @return 字符串
     * @throws IOException
     *             异常
     */
    private static String read(InputStream input) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = in.read(buffer)) >= 0) {
                total += count;
                if (total > MAX_REQUEST) {
                    throw new IOException("Request too large");
                }
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 发送响应。
     * 
     * @param exchange
     *            HTTP 交换
     * @param status
     *            状态
     * @param contentType
     *            Content-Type
     * @param text
     *            文本
     * @throws IOException
     *             异常
     */
    private static void send(HttpExchange exchange, int status, String contentType, String text) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (text == null) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}
