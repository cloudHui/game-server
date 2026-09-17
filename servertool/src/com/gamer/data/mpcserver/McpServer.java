package com.gamer.data.mpcserver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamer.data.mpcserver.commands.CommandHandler;
import com.gamer.data.mpcserver.commands.db.PlayerQuery;
import com.gamer.data.mpcserver.core.CommandContext;
import com.gamer.data.mpcserver.core.CommandDispatcher;
import com.gamer.data.mpcserver.core.CommandResult;
import com.gamer.data.mpcserver.core.McpUtils;

/** 统一 MCP JSON-RPC 处理器。 */
public final class McpServer {
    private static final String PROTOCOL_VERSION = "2025-11-25";
    private static final String EXCEL_BUSY = "Excel busy";
    private static final String TOOL_BUSY = "Tool busy";
    private static final String EXCEL_STUCK =
        "Excel timed out and is still running; open McpServer.bat and choose restart";
    private static final AtomicLong REQUEST_IDS = new AtomicLong();
    /** Excel 任务世代，从 1 起；超时标记只对应当前世代。 */
    private final AtomicLong excelTaskGen = new AtomicLong();
    /** 非 0 表示该世代超时后仍在跑，需拒绝新的 Excel 请求。 */
    private final AtomicLong excelStuckGen = new AtomicLong();

    private final CommandDispatcher dispatcher;// 命令分发器
    private final CommandContext context;// 命令上下文
    private final ExecutorService toolWorkers;// 工具线程池
    private final ExecutorService excelWorkers;// Excel线程池
    private final int toolTimeoutSeconds;// 工具超时时间
    private final int excelTimeoutSeconds;// Excel超时时间

    /**
     * 构造函数
     * 
     * @param dispatcher
     *            命令分发器
     * @param context
     *            命令上下文
     * @param toolWorkers
     *            工具线程池
     * @param excelWorkers
     *            Excel线程池
     * @param toolTimeoutSeconds
     *            工具超时时间
     * @param excelTimeoutSeconds
     *            Excel超时时间
     */
    McpServer(CommandDispatcher dispatcher, CommandContext context, ExecutorService toolWorkers,
        ExecutorService excelWorkers, int toolTimeoutSeconds, int excelTimeoutSeconds) {
        this.dispatcher = dispatcher;
        this.context = context;
        this.toolWorkers = toolWorkers;
        this.excelWorkers = excelWorkers;
        this.toolTimeoutSeconds = toolTimeoutSeconds;
        this.excelTimeoutSeconds = excelTimeoutSeconds;
    }

    /**
     * 处理一个 Streamable HTTP POST；notification 返回 null。
     * 
     * @param json
     *            JSON-RPC 请求
     * @return JSON-RPC 响应
     * @throws Exception
     *             异常
     */
    public String handleHttp(String json) throws Exception {
        JsonNode request;
        try {
            request = context.mapper().readTree(json);
        } catch (Exception e) {
            context.log().logMessage("[mcp] parse error=" + e.getMessage());
            return context.mapper().writeValueAsString(error(null, -32700, "Parse error"));
        }

        long requestId = REQUEST_IDS.incrementAndGet();
        long start = System.nanoTime();
        String tool = toolName(request);
        String label = "request=" + requestId + " method=" + text(request, "method")
            + (tool == null ? "" : " tool=" + tool);
        String status = "completed";
        context.log().logMessage("[mcp] request start " + label);
        try {
            Map<String, Object> response = execute(request, tool);
            if (failed(response)) {
                status = "error";
            }
            return response == null ? null : context.mapper().writeValueAsString(response);
        } finally {
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            context.log().logMessage("[mcp] request end " + label + " status=" + status + " elapsedMs=" + elapsedMs);
        }
    }

    /**
     * 将工具调用提交到普通线程池或 Excel 单线程池，并按超时处理。
     * Excel 超时用世代 CAS 标记，避免迟到的超时把已结束任务标成永久卡死。
     *
     * @param request
     *            JSON-RPC 请求
     * @param tool
     *            工具名，协议方法为 null
     * @return JSON-RPC 响应，notification 为 null
     */
    private Map<String, Object> execute(JsonNode request, String tool) {
        if (tool == null) {
            return handle(request);
        }
        boolean excel = tool.startsWith("excel_");
        int timeoutSeconds = "player_db_query".equals(tool) ? (int)(PlayerQuery.TIMEOUT_MS / 1000)
            : excel ? excelTimeoutSeconds : toolTimeoutSeconds;
        if (excel && isExcelStuck()) {
            return failure(request, EXCEL_STUCK);
        }
        final long excelGen = excel ? excelTaskGen.incrementAndGet() : 0L;
        final AtomicBoolean finished = new AtomicBoolean(false);
        Future<Map<String, Object>> future;
        try {
            future = (excel ? excelWorkers : toolWorkers).submit(() -> {
                try {
                    return handle(request);
                } finally {
                    finished.set(true);
                    if (excel) {
                        // 只清本世代的超时标记，避免误清后续任务或被迟到的超时再置位
                        excelStuckGen.compareAndSet(excelGen, 0L);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            if (excel) {
                return failure(request, isExcelStuck() ? EXCEL_STUCK : EXCEL_BUSY);
            }
            return failure(request, TOOL_BUSY);
        }
        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            if (excel) {
                markExcelTimedOut(excelGen, finished);
                return failure(request, isExcelStuck() ? EXCEL_STUCK
                    : "Excel timeout after " + excelTimeoutSeconds + " seconds");
            }
            return failure(request, "Tool timeout after " + timeoutSeconds + " seconds");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return failure(request, "Request interrupted");
        } catch (ExecutionException e) {
            return failure(request, message(e.getCause()));
        }
    }

    /**
     * 处理一个 JSON-RPC 请求。
     * 
     * @param request
     *            JSON-RPC 请求
     * @return JSON-RPC 响应
     */
    private Map<String, Object> handle(JsonNode request) {
        if (request == null || !request.isObject() || !"2.0".equals(text(request, "jsonrpc"))) {
            return error(null, -32600, "Invalid Request");
        }

        JsonNode idNode = request.get("id");
        if (idNode == null) {
            return null;
        }
        Object id = idNode.isNull() ? null : context.mapper().convertValue(idNode, Object.class);
        String method = text(request, "method");
        JsonNode params = request.get("params");
        if (method == null || method.trim().isEmpty()) {
            return error(id, -32600, "method required");
        }

        try {
            switch (method) {
                case "initialize":
                    return result(id, initialize());
                case "ping":
                case "shutdown":
                    return result(id, map());
                case "tools/list":
                    return result(id, map("tools", dispatcher.toolDefinitions()));
                case "tools/call":
                    return result(id, call(params));
            }

            CommandHandler handler = dispatcher.get(method);
            if (handler == null) {
                return error(id, -32601, "Method not found: " + method);
            }
            CommandResult value = handler.handle(context, params);
            return result(id, map("text", value == null ? "" : value.text));
        } catch (IllegalArgumentException e) {
            return error(id, -32602, e.getMessage());
        } catch (Exception e) {
            context.log().logMessage("[mcp] method=" + method + " error=" + e);
            return error(id, -32603, message(e));
        }
    }

    /**
     * 处理一个工具调用请求。
     * 
     * @param params
     *            工具参数
     * @return JSON-RPC 响应
     */
    private Map<String, Object> call(JsonNode params) {
        String name = text(params, "name");
        CommandHandler handler = name == null ? null : dispatcher.get(name);
        if (handler == null) {
            return toolError(name == null || name.trim().isEmpty() ? "params.name不能为空" : "Tool not found: " + name);
        }
        try {
            CommandResult value = handler.handle(context, params.get("arguments"));
            return toolResult(value == null ? "" : value.text, false);
        } catch (Exception e) {
            context.log().logMessage("[mcp] tool=" + name + " error=" + e);
            return toolResult("Error: " + message(e), true);
        }
    }

    /**
     * 当前是否有超时后仍在运行的 Excel 任务。
     *
     * @return true 表示应拒绝新的 Excel 请求并提示重启
     */
    private boolean isExcelStuck() {
        return excelStuckGen.get() != 0L;
    }

    /**
     * 将超时标记到指定世代；若任务已经结束则立刻清掉，避免迟到的超时把空闲服务标成永久卡死。
     *
     * @param excelGen
     *            本次提交的 Excel 世代
     * @param finished
     *            工作线程是否已进入 finally
     */
    private void markExcelTimedOut(long excelGen, AtomicBoolean finished) {
        excelStuckGen.compareAndSet(0L, excelGen);
        if (finished.get()) {
            excelStuckGen.compareAndSet(excelGen, 0L);
        }
    }

    /**
     * 获取工具名称。
     * 
     * @param request
     *            JSON-RPC 请求
     * @return 工具名称
     */
    private String toolName(JsonNode request) {
        String method = text(request, "method");
        if (isProtocolMethod(method)) {
            return null;
        }
        if ("tools/call".equals(method)) {
            return text(request.get("params"), "name");
        }
        return dispatcher.get(method) == null ? null : method;
    }

    /**
     * initialize / ping / shutdown / tools/list 走协议线程，不进工具池。
     * tools/call 的 ping 工具仍走 {@code Ping} 命令。
     *
     * @param method
     *            JSON-RPC method
     * @return true 表示协议方法
     */
    private static boolean isProtocolMethod(String method) {
        return "initialize".equals(method) || "ping".equals(method) || "shutdown".equals(method)
            || "tools/list".equals(method);
    }

    /**
     * 处理一个失败请求。
     * 
     * @param request
     *            JSON-RPC 请求
     * @param message
     *            错误消息
     * @return JSON-RPC 响应
     */ 
    private Map<String, Object> failure(JsonNode request, String message) {
        JsonNode idNode = request == null ? null : request.get("id");
        Object id = idNode == null || idNode.isNull() ? null : context.mapper().convertValue(idNode, Object.class);
        return "tools/call".equals(text(request, "method"))
            ? result(id, toolError(message)) : error(id, -32001, message);
    }

    /**
     * 初始化响应。
     * 
     * @return JSON-RPC 响应
     */
    private static Map<String, Object> initialize() {
        return map(
            "protocolVersion", PROTOCOL_VERSION,
            "capabilities", map("tools", map("listChanged", Boolean.FALSE)),
            "serverInfo", map("name", "kingdom-mcp", "title", "Kingdom MCP Server", "version", "2.0.0"),
            "instructions", "Unified local Excel, database, filesystem, Redis and Git tools.");
    }

    /**
     * 处理一个工具错误请求。
     * 
     * @param message
     *            错误消息
     * @return JSON-RPC 响应
     */
    private static Map<String, Object> toolError(String message) {
        return toolResult("Error: " + message, true);
    }

    /**
     * 处理一个工具结果请求。
     * 
     * @param text
     *            文本
     * @param failed
     *            是否失败
     * @return JSON-RPC 响应
     */
    private static Map<String, Object> toolResult(String text, boolean failed) {
        Map<String, Object> value = map("content",
            Collections.singletonList(map("type", "text", "text", text == null ? "" : text)));
        if (failed) {
            value.put("isError", Boolean.TRUE);
        }
        return value;
    }

    /**
     * 处理一个结果请求。
     * 
     * @param id
     *            ID
     * @param value
     *            值
     * @return JSON-RPC 响应
     */
    private static Map<String, Object> result(Object id, Object value) {
        return map("jsonrpc", "2.0", "id", id, "result", value);
    }

    /**
     * 处理一个错误请求。
     * 
     * @param id
     *            ID
     * @param code
     *            错误码
     * @param message
     *            错误消息
     * @return JSON-RPC 响应
     */
    private static Map<String, Object> error(Object id, int code, String message) {
        return map("jsonrpc", "2.0", "id", id, "error", map("code", code, "message", message));
    }

    /**
     * 处理一个映射请求。
     * 
     * @param values
     *            值
     * @return JSON-RPC 响应
     */
    private static Map<String, Object> map(Object... values) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    /**
     * 处理一个文本请求。
     * 
     * @param node
     *            JSON-RPC 请求
     * @param field
     *            字段
     * @return 文本
     */
    private static String text(JsonNode node, String field) {
        return node == null ? null : McpUtils.text(node, field);
    }

    /**
     * 处理一个失败请求。
     * 
     * @param response
     *            JSON-RPC 响应
     * @return 是否失败
     */
    private static boolean failed(Map<String, Object> response) {
        if (response == null) {
            return false;
        }
        if (response.get("error") != null) {
            return true;
        }
        Object value = response.get("result");
        return value instanceof Map && Boolean.TRUE.equals(((Map<?, ?>)value).get("isError"));
    }

    /**
     * 处理一个异常消息。
     * 
     * @param e
     *            异常
     * @return 异常消息
     */
    private static String message(Throwable e) {
        return e == null || e.getMessage() == null || e.getMessage().trim().isEmpty()
            ? e == null ? "Unknown error" : e.getClass().getSimpleName() : e.getMessage();
    }
}
