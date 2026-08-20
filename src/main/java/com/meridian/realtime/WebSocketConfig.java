package com.meridian.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * README §16 CORS 설정(CorsConfig)과 동일하게 로컬 Vite 프론트만 허용한다.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TeamEventWebSocketHandler teamEventWebSocketHandler;
    private final TeamEventHandshakeInterceptor teamEventHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(teamEventWebSocketHandler, "/ws/team-events")
                .addInterceptors(teamEventHandshakeInterceptor)
                .setAllowedOrigins("http://localhost:5173");
    }
}
