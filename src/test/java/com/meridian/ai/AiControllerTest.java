package com.meridian.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.auth.AuthenticationException;
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
}
