package com.meridian.ai;

import java.util.List;

/**
 * {@link CultureAnalysisEngine}의 분석 결과. README §9.1 "분석 결과" 4항목(문화권별 해석/
 * 오해 가능 표현/위험도/수정 문장 제안)을 그대로 담는다.
 */
public record CultureAnalysisResult(
        RiskLevel riskLevel,
        List<CultureInterpretation> interpretations,
        List<String> flaggedPhrases,
        String suggestion
) {
}
