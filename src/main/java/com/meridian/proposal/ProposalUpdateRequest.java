package com.meridian.proposal;

import java.time.Instant;
import java.util.List;

/**
 * README §7 제안 수정 요청 Body. DRAFT 상태의 제안만 수정 가능.
 */
public record ProposalUpdateRequest(
        String title,
        String content,
        List<String> targetCultures,
        Instant deadline
) {
}
