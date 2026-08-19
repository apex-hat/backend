package com.meridian.ai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.util.StringUtils;

/**
 * OpenAI 관련 설정의 단일 진입점. {@link OpenAIClient}는 애플리케이션 전체에서 하나만 존재하며,
 * {@code @Lazy}로 실제 사용 시점(엔진의 첫 API 호출)까지 생성을 미룬다.
 */
@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfiguration {

    @Lazy
    @Bean
    public OpenAIClient openAIClient(OpenAiProperties properties) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new IllegalStateException("OPENAI_API_KEY is not configured.");
        }
        return OpenAIOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .build();
    }
}
