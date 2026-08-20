package com.meridian.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/** 환경별로 허용할 프론트엔드 Origin 목록을 한 곳에서 관리한다. */
@Component
public class CorsAllowedOrigins {

    private final String[] values;

    public CorsAllowedOrigins(@Value("${app.cors.allowed-origins}") String configuredOrigins) {
        this.values = Arrays.stream(configuredOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .distinct()
                .toArray(String[]::new);

        if (values.length == 0) {
            throw new IllegalArgumentException("허용할 CORS Origin을 하나 이상 설정해야 합니다.");
        }
    }

    public String[] asArray() {
        return values.clone();
    }
}
