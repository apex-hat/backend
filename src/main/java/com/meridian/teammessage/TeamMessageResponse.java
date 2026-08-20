package com.meridian.teammessage;

import java.time.Instant;

public record TeamMessageResponse(
        Long id,
        Long teamId,
        Long senderId,
        String senderName,
        String content,
        Instant createdAt
) {

    public static TeamMessageResponse from(TeamMessage message) {
        return new TeamMessageResponse(
                message.getId(),
                message.getTeam().getId(),
                message.getSender().getId(),
                message.getSender().getName(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
