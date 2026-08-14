package com.meridian.team;

public record TeamCreateRequest(
        String name,
        String country,
        String cultureTag
) {
}
