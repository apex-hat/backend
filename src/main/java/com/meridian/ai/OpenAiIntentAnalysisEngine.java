package com.meridian.ai;

import com.openai.client.OpenAIClient;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputItem;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * {@link IntentAnalysisEngine}의 실제 구현체. README §9.3 숨은 의도 분석을 OpenAI Responses API
 * Structured Outputs로 호출한다. {@link OpenAIClient}는 {@link OpenAiConfiguration}이 만든
 * 애플리케이션 전역 싱글톤 빈을 그대로 주입받아 쓴다 — 새 client를 만들지 않는다.
 * 생성자 파라미터의 {@code @Lazy}는 {@link OpenAiCultureAnalysisEngine}과 같은 이유로 필요하다:
 * {@code @Bean} 쪽 {@code @Lazy}만으로는 이 엔진이 즉시(eager) 생성될 때 client도 같이 즉시 만들어진다.
 */
@Component
public class OpenAiIntentAnalysisEngine implements IntentAnalysisEngine {

    private final OpenAiProperties properties;
    private final OpenAIClient client;

    public OpenAiIntentAnalysisEngine(OpenAiProperties properties, @Lazy OpenAIClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public IntentAnalysisResult analyze(String content) {
        String model = requireConfig(properties.model(), "OPENAI_MODEL");

        StructuredResponseCreateParams<IntentAnalysisResult> params =
                StructuredResponseCreateParams.<IntentAnalysisResult>builder()
                        .input(prompt(content))
                        .model(model)
                        .text(IntentAnalysisResult.class)
                        .build();

        StructuredResponse<IntentAnalysisResult> response = client.responses().create(params);

        return response.output().stream()
                .filter(StructuredResponseOutputItem::isMessage)
                .map(StructuredResponseOutputItem::asMessage)
                .flatMap(message -> message.content().stream())
                .flatMap(item -> item.outputText().stream())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("OpenAI response did not contain structured output text."));
    }

    private String prompt(String content) {
        return """
                The following message may be indirect or euphemistic. Identify the surface-level \
                opinion it appears to express, and the potential underlying opinion it may actually \
                be conveying (for example, a soft disagreement or concern expressed politely).

                Message:
                %s

                Return surfaceOpinion (the literal, face-value reading) and potentialOpinion \
                (the likely real intent behind the message).
                """.formatted(content);
    }

    private String requireConfig(String value, String envVarName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(envVarName + " is not configured.");
        }
        return value;
    }
}
