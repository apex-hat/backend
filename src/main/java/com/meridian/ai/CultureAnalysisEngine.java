package com.meridian.ai;

import java.util.List;

/**
 * 문화 맥락 분석을 수행하는 엔진 추상화. 실제 AI provider가 정해지면 이 인터페이스의
 * 새 구현체(@Component)를 추가하기만 하면 되고, {@link AiService}/Controller/DTO/entity는
 * 변경할 필요가 없다.
 */
public interface CultureAnalysisEngine {

    CultureAnalysisResult analyze(String originalText, List<String> targetCultures, String responseLanguage);
}
