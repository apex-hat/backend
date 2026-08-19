package com.meridian.proposal;

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

class ProposalControllerTest {

    private ProposalService proposalService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        proposalService = mock(ProposalService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ProposalController(proposalService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsProposalAndReturns201() throws Exception {
        ProposalCreateRequest request = new ProposalCreateRequest("Title", "Content", 10L, List.of("KR"), List.of(), null);
        ProposalResponse response = sampleResponse();

        when(proposalService.createProposal(eq("Bearer id-token"), any(ProposalCreateRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/proposals")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void listsProposals() throws Exception {
        when(proposalService.listProposals("Bearer id-token")).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/proposals").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(100));
    }

    @Test
    void getsProposalDetail() throws Exception {
        when(proposalService.getProposal("Bearer id-token", 100L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/proposals/100").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Title"));
    }

    @Test
    void updatesProposal() throws Exception {
        ProposalUpdateRequest request = new ProposalUpdateRequest("New", "New content", List.of("US"), null);
        when(proposalService.updateProposal(eq("Bearer id-token"), eq(100L), any(ProposalUpdateRequest.class)))
                .thenReturn(sampleResponse());

        mockMvc.perform(put("/api/proposals/100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(proposalService).updateProposal(eq("Bearer id-token"), eq(100L), any(ProposalUpdateRequest.class));
    }

    @Test
    void deletesProposalAndReturns204() throws Exception {
        mockMvc.perform(delete("/api/proposals/100").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isNoContent());

        verify(proposalService).deleteProposal("Bearer id-token", 100L);
    }

    @Test
    void publishesProposalAndReturns200() throws Exception {
        ProposalResponse published = new ProposalResponse(100L, "Title", "Content", 1L, 10L, ProposalStatus.OPEN,
                List.of("KR"), null, null, null, null, Instant.parse("2026-08-12T00:00:00Z"), Instant.parse("2026-08-12T00:00:00Z"));
        when(proposalService.publishProposal("Bearer id-token", 100L)).thenReturn(published);

        mockMvc.perform(post("/api/proposals/100/publish").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void publishRejectsNonDraftProposalWith409() throws Exception {
        when(proposalService.publishProposal("Bearer id-token", 100L))
                .thenThrow(DomainException.conflict("PROPOSAL_NOT_DRAFT", "DRAFT 상태의 제안만 게시할 수 있습니다."));

        mockMvc.perform(post("/api/proposals/100/publish").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_NOT_DRAFT"));
    }

    @Test
    void completesProposalAndReturns200() throws Exception {
        ProposalCompleteRequest request = new ProposalCompleteRequest("B안을 채택합니다.");
        ProposalResponse completed = new ProposalResponse(100L, "Title", "Content", 1L, 10L, ProposalStatus.COMPLETED,
                List.of("KR"), null, "B안을 채택합니다.", 1L, Instant.parse("2026-08-12T00:00:00Z"),
                Instant.parse("2026-08-12T00:00:00Z"), Instant.parse("2026-08-12T00:00:00Z"));
        when(proposalService.completeProposal(eq("Bearer id-token"), eq(100L), any(ProposalCompleteRequest.class)))
                .thenReturn(completed);

        mockMvc.perform(post("/api/proposals/100/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.decision").value("B안을 채택합니다."));
    }

    @Test
    void completeRejectsNonConsensusReadyProposalWith409() throws Exception {
        ProposalCompleteRequest request = new ProposalCompleteRequest("B안을 채택합니다.");
        when(proposalService.completeProposal(eq("Bearer id-token"), eq(100L), any(ProposalCompleteRequest.class)))
                .thenThrow(DomainException.conflict("PROPOSAL_NOT_CONSENSUS_READY", "CONSENSUS_READY 상태의 제안만 완료 처리할 수 있습니다."));

        mockMvc.perform(post("/api/proposals/100/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_NOT_CONSENSUS_READY"));
    }

    @Test
    void completeRejectsBlankDecisionWith400() throws Exception {
        ProposalCompleteRequest request = new ProposalCompleteRequest("  ");

        mockMvc.perform(post("/api/proposals/100/complete")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void mapsDomainExceptionToErrorResponse() throws Exception {
        when(proposalService.getProposal("Bearer id-token", 404L))
                .thenThrow(DomainException.notFound("PROPOSAL_NOT_FOUND", "Proposal not found."));

        mockMvc.perform(get("/api/proposals/404").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_NOT_FOUND"));
    }

    private ProposalResponse sampleResponse() {
        return new ProposalResponse(100L, "Title", "Content", 1L, 10L, ProposalStatus.DRAFT,
                List.of("KR"), null, null, null, null, Instant.parse("2026-08-12T00:00:00Z"), Instant.parse("2026-08-12T00:00:00Z"));
    }
}
