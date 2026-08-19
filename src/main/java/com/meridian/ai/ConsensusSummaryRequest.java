package com.meridian.ai;

import jakarta.validation.constraints.NotNull;

/**
 * README §9.2 AI 합의 요약 요청 Body. 이미 등록된 제안을 대상으로 하므로 proposalId가 필수다
 * (§9.1 문화 맥락 분석과 달리 등록 전 호출을 지원하지 않는다).
 */
public record ConsensusSummaryRequest(
        @NotNull Long proposalId
) {
}
