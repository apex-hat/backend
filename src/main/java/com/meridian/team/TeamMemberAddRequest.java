package com.meridian.team;

public record TeamMemberAddRequest(
        Long userId,
        String role
) {
}
