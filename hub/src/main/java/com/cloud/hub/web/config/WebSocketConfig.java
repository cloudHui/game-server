package com.cloud.hub.web.config;

import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.cloud.hub.web.handler.GameWebSocketHandler;
import com.cloud.hub.web.handler.MiniGameWebSocketHandler;

/**
 * WebSocket配置
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler gameWebSocketHandler;
    private final MiniGameWebSocketHandler miniGameWebSocketHandler;

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
