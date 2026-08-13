package com.meridian.proposal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * README §7 제안 생성 요청 Body.
 */
public record ProposalCreateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content,
        @NotNull Long teamId,
        List<@NotBlank String> targetCultures,
        List<@NotNull Long> cultureAnalysisIds,
        Instant deadline
) {
}
