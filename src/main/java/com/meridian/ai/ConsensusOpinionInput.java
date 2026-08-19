package com.meridian.ai;

/**
 * {@link ConsensusSummaryEngine}에 전달하는 의견 1건. 작성자 식별 정보는 분석에 필요 없어 제외한다.
 */
public record ConsensusOpinionInput(
        String stance,
        String comment
) {
}
