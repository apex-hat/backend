package com.meridian.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 팀별로 연결된 WebSocket 세션을 들고 있다가, {@link TeamEventPublisher}가 브로드캐스트를
 * 요청하면 해당 팀에 연결된 세션에만 이벤트를 보낸다. 인증/팀 소속 검증은
 * {@link TeamEventHandshakeInterceptor}가 핸드셰이크 단계에서 이미 끝낸다.
 */
@Component
public class TeamEventWebSocketHandler extends TextWebSocketHandler {

    static final String TEAM_ID_ATTRIBUTE = "teamId";

    private final Map<Long, Set<WebSocketSession>> sessionsByTeam = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        teamIdOf(session).ifPresent(teamId ->
                sessionsByTeam.computeIfAbsent(teamId, id -> ConcurrentHashMap.newKeySet()).add(session));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        teamIdOf(session).ifPresent(teamId -> {
            Set<WebSocketSession> sessions = sessionsByTeam.get(teamId);
            if (sessions != null) {
                sessions.remove(session);
            }
        });
    }

    void broadcast(Long teamId, String payload) {
        Set<WebSocketSession> sessions = sessionsByTeam.get(teamId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException ignored) {
                    // 끊긴 세션은 afterConnectionClosed에서 정리된다.
                }
            }
        }
    }

    private java.util.Optional<Long> teamIdOf(WebSocketSession session) {
        return java.util.Optional.ofNullable((Long) session.getAttributes().get(TEAM_ID_ATTRIBUTE));
    }
}
