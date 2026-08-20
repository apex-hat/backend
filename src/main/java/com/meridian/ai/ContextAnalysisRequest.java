package com.meridian.ai;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * README §9.1 문화 맥락 분석 요청 Body. proposalId는 받지 않는다 —
 * 등록 전 분석 후 필요하면 §7 POST /api/proposals의 cultureAnalysisIds로 연결한다.
 */
public record ContextAnalysisRequest(
        @NotBlank String originalText,
        List<@NotBlank String> targetCultures
) {
}
