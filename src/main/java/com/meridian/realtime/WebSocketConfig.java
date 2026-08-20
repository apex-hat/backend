package com.meridian.realtime;

import com.meridian.config.CorsAllowedOrigins;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 일반 API CORS 설정과 동일한 프론트엔드 Origin 목록을 허용한다.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TeamEventWebSocketHandler teamEventWebSocketHandler;
    private final TeamEventHandshakeInterceptor teamEventHandshakeInterceptor;
    private final CorsAllowedOrigins corsAllowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(teamEventWebSocketHandler, "/ws/team-events")
                .addInterceptors(teamEventHandshakeInterceptor)
                .setAllowedOrigins(corsAllowedOrigins.asArray());
    }
}
