package com.meridian.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firebase")
public record FirebaseProperties(
        String projectId,
        String clientEmail,
        String privateKey
) {
}
