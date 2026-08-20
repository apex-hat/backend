package com.meridian.user;

import java.time.Instant;

public record UserResponse(
        Long id,
        String firebaseUid,
        String name,
        String email,
        String country,
        String timeZone,
        String location,
        String cultureTag,
        String friendCode,
        Instant createdAt,
        Instant updatedAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFirebaseUid(),
                user.getName(),
                user.getEmail(),
                user.getCountry(),
                user.getTimeZone(),
                user.getLocation(),
                user.getCultureTag(),
                user.getFriendCode(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
