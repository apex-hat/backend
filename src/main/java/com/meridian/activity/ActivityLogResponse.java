package com.meridian.activity;

import java.time.Instant;

public record ActivityLogResponse(
        Long id,
        Long actorId,
        String actorName,
        Long targetUserId,
        String targetUserName,
        ActivityAction action,
        String description,
        Instant createdAt
) {

    public static ActivityLogResponse from(ActivityLog log) {
        return new ActivityLogResponse(
                log.getId(),
                log.getActor().getId(),
                log.getActor().getName(),
                log.getTargetUser() != null ? log.getTargetUser().getId() : null,
                log.getTargetUser() != null ? log.getTargetUser().getName() : null,
                log.getAction(),
                log.getDescription(),
                log.getCreatedAt()
        );
    }
}
