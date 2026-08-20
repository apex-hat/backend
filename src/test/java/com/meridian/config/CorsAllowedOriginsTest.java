package com.meridian.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsAllowedOriginsTest {

    @Test
    void parsesTrimsAndDeduplicatesConfiguredOrigins() {
        CorsAllowedOrigins origins = new CorsAllowedOrigins(
                "http://localhost:5173, http://1.201.118.205, http://localhost:5173"
        );

        assertThat(origins.asArray()).containsExactly(
                "http://localhost:5173",
                "http://1.201.118.205"
        );
    }

    @Test
    void rejectsEmptyConfiguration() {
        assertThatThrownBy(() -> new CorsAllowedOrigins(" , "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Origin");
    }
}
