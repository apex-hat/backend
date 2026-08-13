package com.meridian.proposal;

import java.time.Instant;
import java.util.List;

/**
 * README §7 제안 생성 요청 Body.
 */
public record ProposalCreateRequest(
        String title,
        String content,
        Long teamId,
        List<String> targetCultures,
        List<Long> cultureAnalysisIds,
        Instant deadline
) {
}
