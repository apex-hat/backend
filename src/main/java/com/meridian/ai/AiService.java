package com.meridian.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * README §9.1 문화 맥락 분석. 인증은 UserService를 그대로 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class AiService {

    // ObjectMapper는 상태 없는(stateless) 유틸리티라 DI 없이 직접 소유한다
    // (Spring Boot의 자동 구성 ObjectMapper 빈 유무에 의존하지 않기 위함).
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final UserService userService;
    private final CultureAnalysisEngine cultureAnalysisEngine;
    private final CultureAnalysisRepository cultureAnalysisRepository;

    @Transactional
    public ContextAnalysisResponse contextAnalysis(String authorizationHeader, ContextAnalysisRequest request) {
        userService.getCurrentUserEntity(authorizationHeader);

        CultureAnalysisResult result = cultureAnalysisEngine.analyze(request.originalText(), request.targetCultures());

        CultureAnalysis analysis = cultureAnalysisRepository.save(CultureAnalysis.builder()
                .originalText(request.originalText())
                .riskLevel(result.riskLevel())
                .interpretation(writeJson(result.interpretations()))
                .flaggedPhrase(writeJson(result.flaggedPhrases()))
                .suggestedRewrite(result.suggestion())
                .build());

        return ContextAnalysisResponse.from(analysis, OBJECT_MAPPER);
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize AI analysis result.", e);
        }
    }
}
