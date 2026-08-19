package com.meridian.team;

import java.time.Instant;

public record TeamResponse(
        Long id,
        String name,
        String country,
        String cultureTag,
        Instant createdAt,
        Instant updatedAt
) {

    public static TeamResponse from(Team team) {
        return new TeamResponse(
                team.getId(),
                team.getName(),
                team.getCountry(),
                team.getCultureTag(),
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }
}
