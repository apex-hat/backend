package com.apex.meridian.notification.dto;

import com.apex.meridian.notification.domain.Notification;
import com.apex.meridian.notification.domain.NotificationType;

import java.time.Instant;

public record NotificationResponse(
        Long id,
        Long proposalId,
        NotificationType type,
        String message,
        boolean read,
        Instant createdAt
) {

    public static NotificationResponse from(
            Notification notification
    ) {
        return new NotificationResponse(
                notification.getId(),
                notification.getProposalId(),
                notification.getType(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}