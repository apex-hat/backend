package com.meridian.ai;

import java.util.List;

/**
 * {@link ConsensusSummaryEngine}의 분석 결과. README §9.2 "분석 결과" 5항목
 * (합의 여부/핵심 쟁점/문화적 표현 분석/숨겨진 반대 의견/권장 후속 조치)을 그대로 담는다.
 */
public record ConsensusAnalysisResult(
        ConsensusStatus consensusStatus,
        String summary,
        List<String> keyIssues,
        List<String> culturalAnalysis,
        List<String> hiddenOpposition,
        String recommendedActions
) {
}
