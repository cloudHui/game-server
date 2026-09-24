package com.cloud.hub.web.command;

import org.springframework.web.socket.WebSocketSession;
import java.util.Map;

/**
 * WebSocket 命令处理器统一接口。
 * <p>
 * 每个实现类对应一个前端请求动作 action（如 auth、enterTable、op 等），
 * Spring 容器启动时自动收集所有带有 {@code @Component} 的处理器并完成路由映射。
 *
 * @author cloud
 */
public interface WsCommandHandler {

    /**
     * 返回此处理器对应的 action 动作标识，与前端 JSON 请求的 action 字段匹配。
     *
     * @return 动作标识名称
     */
    String action();

    /**
     * 处理客户端发送的单条 WebSocket 命令请求。
     *
     * @param ctx  WebSocket 会话全局上下文
     * @param ws   当前连接物理会话
     * @param seq  请求流水号
     * @param data 请求参数数据字典
     */
    void handle(WsContext ctx, WebSocketSession ws, int seq, Map<String, Object> data);
}