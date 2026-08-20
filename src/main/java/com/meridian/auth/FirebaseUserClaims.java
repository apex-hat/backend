package com.meridian.auth;

import org.springframework.util.StringUtils;

public record FirebaseUserClaims(
        String uid,
        String email,
        String name,
        String country,
        String timeZone,
        String location,
        String cultureTag
) {

    public String effectiveTimeZone() {
        return StringUtils.hasText(timeZone) ? timeZone : "UTC";
    }
}
