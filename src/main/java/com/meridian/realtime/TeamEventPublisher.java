package com.meridian.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Proposal/Opinion 도메인 서비스가 상태 변경 후 팀 전체에 실시간으로 알리기 위해 호출하는 진입점. */
@Component
@RequiredArgsConstructor
public class TeamEventPublisher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TeamEventWebSocketHandler handler;

    public void publish(TeamEventType type, Long teamId, Long proposalId) {
        try {
            String payload = OBJECT_MAPPER.writeValueAsString(new TeamEvent(type, teamId, proposalId));
            handler.broadcast(teamId, payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize team event.", e);
        }
    }
}
