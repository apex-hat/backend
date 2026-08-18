package com.meridian.ai;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.responses.StructuredResponse;
import com.openai.models.responses.StructuredResponseCreateParams;
import com.openai.models.responses.StructuredResponseOutputItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * {@link CultureAnalysisEngine}의 실제 구현체. OpenAI Responses API를 Structured Outputs로 호출해
 * {@link CultureAnalysisResult}를 곧바로 스키마 검증된 형태로 받는다. OpenAI SDK 관련 코드는 이 클래스
 * 안에만 존재하고, {@link AiService}/컨트롤러/DTO/entity는 이 구현체 교체와 무관하다.
 */
@Component
@RequiredArgsConstructor
public class OpenAiCultureAnalysisEngine implements CultureAnalysisEngine {

    private final OpenAiProperties properties;
    private volatile OpenAIClient client;

    @Override
    public CultureAnalysisResult analyze(String originalText, List<String> targetCultures) {
        String model = requireConfig(properties.model(), "OPENAI_MODEL");

        StructuredResponseCreateParams<CultureAnalysisResult> params =
                StructuredResponseCreateParams.<CultureAnalysisResult>builder()
                        .input(prompt(originalText, targetCultures))
                        .model(model)
                        .text(CultureAnalysisResult.class)
                        .build();

        StructuredResponse<CultureAnalysisResult> response = client().responses().create(params);

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

    private OpenAIClient client() {
        OpenAIClient current = client;
        if (current == null) {
            synchronized (this) {
                current = client;
                if (current == null) {
                    current = OpenAIOkHttpClient.builder()
                            .apiKey(requireConfig(properties.apiKey(), "OPENAI_API_KEY"))
                            .build();
                    client = current;
                }
            }
        }
        return current;
    }

    private String requireConfig(String value, String envVarName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(envVarName + " is not configured.");
        }
        return value;
    }
}
