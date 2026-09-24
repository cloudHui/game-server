package com.cloud.hub.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.cloud.hub.web.handler.GameWebSocketHandler;
import com.cloud.hub.web.handler.MiniGameWebSocketHandler;

/**
 * Hub 一体化服务 WebSocket 路由配置中心。
 * <p>
 * 注册棋牌游戏长连接通道（{@code /ws/game}）与休闲对战小游戏长连接通道（{@code /ws/mini}）。
 *
 * @author cloud
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;
    private final MiniGameWebSocketHandler miniGameWebSocketHandler;

    /**
     * 注入游戏与小游戏 WebSocket 处理器。
     *
     * @param gameWebSocketHandler     棋牌游戏处理器
     * @param miniGameWebSocketHandler 休闲小游戏处理器
     */
    public WebSocketConfig(GameWebSocketHandler gameWebSocketHandler,
                           MiniGameWebSocketHandler miniGameWebSocketHandler) {
        this.gameWebSocketHandler = gameWebSocketHandler;
        this.miniGameWebSocketHandler = miniGameWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(gameWebSocketHandler, "/ws/game")
                .setAllowedOrigins("*");
        registry.addHandler(miniGameWebSocketHandler, "/ws/mini")
                .setAllowedOrigins("*");
    }
}
