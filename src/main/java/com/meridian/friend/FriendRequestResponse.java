package com.meridian.friend;

import java.time.Instant;

public record FriendRequestResponse(
        Long id,
        Long requesterId,
        String requesterName,
        Long addresseeId,
        String addresseeName,
        FriendRequestStatus status,
        Instant createdAt,
        Instant respondedAt
) {

    public static FriendRequestResponse from(FriendRequest request) {
        return new FriendRequestResponse(
                request.getId(),
                request.getRequester().getId(),
                request.getRequester().getName(),
                request.getAddressee().getId(),
                request.getAddressee().getName(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getRespondedAt()
        );
    }
}
