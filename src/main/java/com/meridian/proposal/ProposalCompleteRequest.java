package com.meridian.proposal;

import jakarta.validation.constraints.NotBlank;

/**
 * README §7 제안 완료 처리 요청 Body. decidedBy는 요청 본문으로 받지 않고
 * 인증된 호출자 본인으로 고정한다(다른 사용자가 결정한 것처럼 위조할 수 없도록).
 */
public record ProposalCompleteRequest(
        @NotBlank String decision
) {
}
