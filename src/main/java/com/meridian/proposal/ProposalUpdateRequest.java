package com.meridian.proposal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * README §7 제안 수정 요청 Body. DRAFT 상태의 제안만 수정 가능.
 */
public record ProposalUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content,
        List<@NotBlank String> targetCultures,
        Instant deadline
) {
}
