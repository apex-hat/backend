package com.meridian.message;

import java.time.Instant;

public record MessageResponse(
        Long id,
        Long senderId,
        Long receiverId,
        String content,
        Boolean isRead,
        Instant createdAt
) {

    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getSender().getId(),
                message.getReceiver().getId(),
                message.getContent(),
                message.getIsRead(),
                message.getCreatedAt()
        );
    }
}
