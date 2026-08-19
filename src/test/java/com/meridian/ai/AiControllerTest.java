package com.meridian.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.auth.AuthenticationException;
import com.meridian.common.exception.DomainException;
import com.meridian.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiControllerTest {

    private AiService aiService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        aiService = mock(AiService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AiController(aiService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void contextAnalysisReturns201WithBody() throws Exception {
        ContextAnalysisRequest request = new ContextAnalysisRequest("원문", List.of("KR"));
        ContextAnalysisResponse response = new ContextAnalysisResponse(
                1L, "원문", RiskLevel.LOW,
                List.of(new CultureInterpretation("KR", "분석 보류")), List.of(), "원문");

        when(aiService.contextAnalysis(eq("Bearer id-token"), any(ContextAnalysisRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/ai/context-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.riskLevel").value("LOW"))
                .andExpect(jsonPath("$.interpretations[0].culture").value("KR"));
    }

    @Test
    void rejectsBlankOriginalTextWith400() throws Exception {
        ContextAnalysisRequest request = new ContextAnalysisRequest("  ", List.of());

        mockMvc.perform(post("/api/ai/context-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void mapsMissingBearerTokenTo401() throws Exception {
        when(aiService.contextAnalysis(eq((String) null), any(ContextAnalysisRequest.class)))
                .thenThrow(new AuthenticationException("Bearer token is required."));

        mockMvc.perform(post("/api/ai/context-analysis")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ContextAnalysisRequest("원문", List.of()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void intentAnalysisReturns200WithBody() throws Exception {
        IntentAnalysisRequest request = new IntentAnalysisRequest("괜찮은 것 같아요. 다만 일정이 조금 걱정되네요.");
        IntentAnalysisResponse response = new IntentAnalysisResponse(request.content(), "긍정", "조건부 반대 또는 우려 가능성");

        when(aiService.intentAnalysis(eq("Bearer id-token"), any(IntentAnalysisRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/ai/intent-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.surfaceOpinion").value("긍정"))
                .andExpect(jsonPath("$.potentialOpinion").value("조건부 반대 또는 우려 가능성"));
    }

    @Test
    void intentAnalysisRejectsBlankContentWith400() throws Exception {
        IntentAnalysisRequest request = new IntentAnalysisRequest("  ");

        mockMvc.perform(post("/api/ai/intent-analysis")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void intentAnalysisMapsMissingBearerTokenTo401() throws Exception {
        when(aiService.intentAnalysis(eq((String) null), any(IntentAnalysisRequest.class)))
                .thenThrow(new AuthenticationException("Bearer token is required."));

        mockMvc.perform(post("/api/ai/intent-analysis")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new IntentAnalysisRequest("원문"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void consensusSummaryReturns201WithBody() throws Exception {
        ConsensusSummaryRequest request = new ConsensusSummaryRequest(100L);
        ConsensusSummaryResponse response = new ConsensusSummaryResponse(
                1L, 100L, ConsensusStatus.PARTIAL, "부분 합의", List.of("일정"), List.of("문화 차이"),
                List.of("완곡한 반대"), "추가 논의 필요", java.time.Instant.parse("2026-08-19T00:00:00Z"));

        when(aiService.consensusSummary(eq("Bearer id-token"), any(ConsensusSummaryRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/ai/consensus-summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consensusStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.keyIssues[0]").value("일정"));
    }

    @Test
    void consensusSummaryRejectsMissingProposalIdWith400() throws Exception {
        mockMvc.perform(post("/api/ai/consensus-summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void consensusSummaryMapsInsufficientResponsesTo409() throws Exception {
        when(aiService.consensusSummary(eq("Bearer id-token"), any(ConsensusSummaryRequest.class)))
                .thenThrow(DomainException.conflict("INSUFFICIENT_RESPONSES", "아직 응답 인원이 부족합니다."));

        mockMvc.perform(post("/api/ai/consensus-summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new ConsensusSummaryRequest(100L))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_RESPONSES"));
    }
}
