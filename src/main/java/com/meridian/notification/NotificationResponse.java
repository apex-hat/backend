package com.meridian.notification;

import java.time.Instant;

/**
 * README §11 알림 응답 Body.
 */
public record NotificationResponse(
        Long id,
        Long proposalId,
        NotificationType type,
        String title,
        String content,
        Boolean isRead,
        Instant createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getProposal() != null ? notification.getProposal().getId() : null,
                notification.getType(),
                notification.getTitle(),
                notification.getContent(),
                notification.getIsRead(),
                notification.getCreatedAt()
        );
    }
}
