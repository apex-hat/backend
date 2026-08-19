package com.meridian.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * README §9.1 예상 응답. entity의 flaggedPhrase(단수, DB 컬럼명)를
 * flaggedPhrases(복수, API 응답)로 매핑한다.
 */
public record ContextAnalysisResponse(
        Long id,
        String originalText,
        RiskLevel riskLevel,
        List<CultureInterpretation> interpretations,
        List<String> flaggedPhrases,
        String suggestion
) {

    public static ContextAnalysisResponse from(CultureAnalysis analysis, ObjectMapper objectMapper) {
        return new ContextAnalysisResponse(
                analysis.getId(),
                analysis.getOriginalText(),
                analysis.getRiskLevel(),
                readJsonList(objectMapper, analysis.getInterpretation(), new TypeReference<List<CultureInterpretation>>() {
                }),
                readJsonList(objectMapper, analysis.getFlaggedPhrase(), new TypeReference<List<String>>() {
                }),
                analysis.getSuggestedRewrite()
        );
    }

    private static <T> List<T> readJsonList(ObjectMapper objectMapper, String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse stored AI analysis result.", e);
        }
    }
}
