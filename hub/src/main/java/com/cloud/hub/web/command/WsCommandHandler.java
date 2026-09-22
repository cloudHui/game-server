package com.cloud.hub.web.command;

import org.springframework.web.socket.WebSocketSession;
import java.util.Map;

/**
 * WebSocket 命令处理器接口。
 *
 * <p>每个实现类对应一个前端 action（如 auth、enterTable），
 * 用 {@link WsCommand} 声明自身名称，
 * 由 Spring 自动扫描注入到 {@code GameWebSocketHandler}，无需手动注册。
 */
public interface WsCommandHandler {

    /**
     * 返回此处理器对应的 action 名称，与前端 JSON 中的 action 字段匹配。
     */
    String action();

    /**
     * 处理一条 WebSocket 命令请求。
     *
     * @param ctx  WebSocket 会话上下文（session 映射 + 发送工具）
     * @param ws   当前 WebSocket 会话
     * @param seq  请求序号（回包必须携带）
     * @param data 请求数据体，可能为 null
     */
    void handle(WsContext ctx, WebSocketSession ws, int seq, Map<String, Object> data);
}