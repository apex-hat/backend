package com.meridian.team;

import com.meridian.user.User;

import java.time.Instant;

public record TeamMemberResponse(
        Long userId,
        String firebaseUid,
        String name,
        String email,
        String country,
        String timeZone,
        String location,
        String cultureTag,
        String role,
        Instant joinedAt
) {

    public static TeamMemberResponse from(TeamMember member) {
        User user = member.getUser();
        return new TeamMemberResponse(
                user.getId(),
                user.getFirebaseUid(),
                user.getName(),
                user.getEmail(),
                user.getCountry(),
                user.getTimeZone(),
                user.getLocation(),
                user.getCultureTag(),
                member.getRole(),
                member.getJoinedAt()
        );
    }
}
