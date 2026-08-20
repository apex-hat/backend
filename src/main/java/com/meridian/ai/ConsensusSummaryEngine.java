package com.meridian.ai;

import java.util.List;

/**
 * README §9.2 AI 합의 요약을 수행하는 엔진 추상화. {@link CultureAnalysisEngine}과 동일하게
 * 구현체를 교체해도 {@link AiService}/Controller/DTO/entity는 변경할 필요가 없다.
 */
public interface ConsensusSummaryEngine {

    ConsensusAnalysisResult analyze(String proposalTitle, String proposalContent, List<ConsensusOpinionInput> opinions, String responseLanguage);
}
