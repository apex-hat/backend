package com.meridian.ai;

/**
 * {@link IntentAnalysisEngine}의 분석 결과. README §9.3 "표면적 의견"/"잠재적 의견"을 그대로 담는다.
 */
public record IntentAnalysisResult(
        String surfaceOpinion,
        String potentialOpinion
) {
}
