package com.meridian.ai;

/**
 * README §9.3 숨은 의도 분석을 수행하는 엔진 추상화. {@link CultureAnalysisEngine}과 동일하게
 * 구현체를 교체해도 {@link AiService}/Controller/DTO는 변경할 필요가 없다.
 */
public interface IntentAnalysisEngine {

    IntentAnalysisResult analyze(String content);
}
