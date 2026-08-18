package com.meridian.ai;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputItem;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * {@link CultureAnalysisEngine}의 실제 구현체. OpenAI Responses API를 Structured Outputs로 호출해
 * {@link CultureAnalysisResult}를 곧바로 스키마 검증된 형태로 받는다. OpenAI SDK 관련 코드는 이 클래스
 * 안에만 존재하고, {@link AiService}/컨트롤러/DTO/entity는 이 구현체 교체와 무관하다.
 * {@link OpenAIClient}는 {@link OpenAiConfiguration}이 만든 애플리케이션 전역 싱글톤 빈을 주입받아 쓴다.
 * 생성자 파라미터에 {@code @Lazy}를 직접 붙여야 이 엔진이 즉시(eager) 생성되어도 실제
 * {@link OpenAIClient} 생성(및 API 키 검증)은 첫 {@link #analyze} 호출 시점까지 미뤄진다 —
 * {@code @Bean} 메서드의 {@code @Lazy}만으로는 생성자 주입 시점에 즉시 resolve된다.
 */
@Component
public class OpenAiCultureAnalysisEngine implements CultureAnalysisEngine {

    private final OpenAiProperties properties;
    private final OpenAIClient client;

    public OpenAiCultureAnalysisEngine(OpenAiProperties properties, @Lazy OpenAIClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public CultureAnalysisResult analyze(String originalText, List<String> targetCultures) {
        String model = requireConfig(properties.model(), "OPENAI_MODEL");

        StructuredResponseCreateParams<CultureAnalysisResult> params =
                StructuredResponseCreateParams.<CultureAnalysisResult>builder()
                        .input(prompt(originalText, targetCultures))
                        .model(model)
                        .text(CultureAnalysisResult.class)
                        .build();

        StructuredResponse<CultureAnalysisResult> response = client.responses().create(params);

        return response.output().stream()
                .filter(StructuredResponseOutputItem::isMessage)
                .map(StructuredResponseOutputItem::asMessage)
                .flatMap(message -> message.content().stream())
                .flatMap(content -> content.outputText().stream())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("OpenAI response did not contain structured output text."));
    }

    private String prompt(String originalText, List<String> targetCultures) {
        String cultures = (targetCultures == null || targetCultures.isEmpty())
                ? "a general international audience"
                : String.join(", ", targetCultures);
        return """
                Analyze the following message for cross-cultural miscommunication risk when read \
                by someone from each of these cultures: %s.

                Message:
                %s

                Return an overall riskLevel (LOW, MEDIUM, or HIGH), one interpretation per listed \
                culture, any phrases that could be misread across cultures, and a suggested rewrite \
                that reduces the risk.
                """.formatted(cultures, originalText);
    }

    private String requireConfig(String value, String envVarName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(envVarName + " is not configured.");
        }
        return value;
    }
}
