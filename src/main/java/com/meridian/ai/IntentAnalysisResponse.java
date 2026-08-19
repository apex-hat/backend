package com.meridian.ai;

/**
 * README §9.3 예상 응답. DB에 저장하지 않으므로 id/createdAt이 없다.
 */
public record IntentAnalysisResponse(
        String content,
        String surfaceOpinion,
        String potentialOpinion
) {

    public static IntentAnalysisResponse from(String content, IntentAnalysisResult result) {
        return new IntentAnalysisResponse(content, result.surfaceOpinion(), result.potentialOpinion());
    }
}
