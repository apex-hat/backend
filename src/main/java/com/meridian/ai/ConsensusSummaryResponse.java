package com.meridian.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

/**
 * README §9.2 예상 응답. entity의 keyIssues/culturalAnalysis/hiddenOpposition(TEXT에 JSON 문자열로
 * 저장)을 다시 배열로 파싱해 내려준다 — {@link ContextAnalysisResponse}와 동일한 패턴.
 */
public record ConsensusSummaryResponse(
        Long id,
        Long proposalId,
        ConsensusStatus consensusStatus,
        String summary,
        List<String> keyIssues,
        List<String> culturalAnalysis,
        List<String> hiddenOpposition,
        String recommendedActions,
        Instant createdAt
) {

    public static ConsensusSummaryResponse from(ConsensusSummary summary, ObjectMapper objectMapper) {
        return new ConsensusSummaryResponse(
                summary.getId(),
                summary.getProposal().getId(),
                summary.getConsensusStatus(),
                summary.getSummary(),
                readJsonList(objectMapper, summary.getKeyIssues()),
                readJsonList(objectMapper, summary.getCulturalAnalysis()),
                readJsonList(objectMapper, summary.getHiddenOpposition()),
                summary.getRecommendedActions(),
                summary.getCreatedAt()
        );
    }

    private static List<String> readJsonList(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse stored AI analysis result.", e);
        }
    }
}
