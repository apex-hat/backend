package com.meridian.ai;

import jakarta.validation.constraints.NotBlank;

/**
 * README §9.3 숨은 의도 분석 요청 Body.
 */
public record IntentAnalysisRequest(
        @NotBlank String content
) {
}
