package com.meridian.opinion;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.meridian.common.exception.DomainException;
import com.meridian.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpinionControllerTest {

    private OpinionService opinionService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        opinionService = mock(OpinionService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new OpinionController(opinionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsOpinionAndReturns201() throws Exception {
        OpinionRequest request = new OpinionRequest(OpinionStance.AGREE, "동의합니다.", null);
        OpinionResponse response = sampleResponse();

        when(opinionService.createOpinion(eq("Bearer id-token"), eq(100L), any(OpinionRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/proposals/100/opinions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.content").value("동의합니다."));
    }

    @Test
    void listsOpinions() throws Exception {
        when(opinionService.listOpinions("Bearer id-token", 100L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/proposals/100/opinions").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void updatesOpinion() throws Exception {
        OpinionRequest request = new OpinionRequest(OpinionStance.DISAGREE, "생각이 바뀌었습니다.", null);
        when(opinionService.updateOpinion(eq("Bearer id-token"), eq(1L), any(OpinionRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/opinions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(opinionService).updateOpinion(eq("Bearer id-token"), eq(1L), any(OpinionRequest.class));
    }

    @Test
    void deletesOpinionAndReturns204() throws Exception {
        mockMvc.perform(delete("/api/opinions/1").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isNoContent());

        verify(opinionService).deleteOpinion("Bearer id-token", 1L);
    }

    @Test
    void mapsDuplicateOpinionTo409() throws Exception {
        when(opinionService.createOpinion(eq("Bearer id-token"), eq(100L), any(OpinionRequest.class)))
                .thenThrow(DomainException.conflict("OPINION_ALREADY_EXISTS", "이미 이 제안에 의견을 등록했습니다."));

        mockMvc.perform(post("/api/proposals/100/opinions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new OpinionRequest(OpinionStance.AGREE, "내용", null))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("OPINION_ALREADY_EXISTS"));
    }

    @Test
    void mapsNonOwnerUpdateTo403() throws Exception {
        when(opinionService.updateOpinion(eq("Bearer id-token"), eq(1L), any(OpinionRequest.class)))
                .thenThrow(DomainException.forbidden("OPINION_ACCESS_DENIED", "본인의 의견만 수정/삭제할 수 있습니다."));

        mockMvc.perform(put("/api/opinions/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new OpinionRequest(OpinionStance.AGREE, "내용", null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("OPINION_ACCESS_DENIED"));
    }

    private OpinionResponse sampleResponse() {
        return new OpinionResponse(1L, 100L, 2L, OpinionStance.AGREE, "동의합니다.", null,
                Instant.parse("2026-08-12T00:00:00Z"), Instant.parse("2026-08-12T00:00:00Z"));
    }
}
