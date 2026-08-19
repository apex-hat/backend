package com.meridian.dashboard;

import com.meridian.auth.AuthenticationException;
import com.meridian.common.exception.DomainException;
import com.meridian.common.exception.GlobalExceptionHandler;
import com.meridian.proposal.ProposalStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {

    private DashboardService dashboardService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        dashboardService = mock(DashboardService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new DashboardController(dashboardService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void timezonesReturns200WithBody() throws Exception {
        DashboardTimezonesResponse response = new DashboardTimezonesResponse(List.of(
                new DashboardTimezoneMemberResponse(1L, "KR", "Asia/Seoul", "21:30", "Seoul HQ")));
        when(dashboardService.timezones(eq("Bearer id-token"), eq(10L))).thenReturn(response);

        mockMvc.perform(get("/api/dashboard/timezones").param("teamId", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[0].userId").value(1))
                .andExpect(jsonPath("$.members[0].country").value("KR"));
    }

    @Test
    void timezonesMissingTeamIdReturns400() throws Exception {
        mockMvc.perform(get("/api/dashboard/timezones").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void timezonesMapsMissingBearerTokenTo401() throws Exception {
        when(dashboardService.timezones(eq((String) null), eq(10L)))
                .thenThrow(new AuthenticationException("Bearer token is required."));

        mockMvc.perform(get("/api/dashboard/timezones").param("teamId", "10"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void timezonesMapsTeamNotFoundTo404() throws Exception {
        when(dashboardService.timezones(eq("Bearer id-token"), eq(999L)))
                .thenThrow(DomainException.notFound("TEAM_NOT_FOUND", "Team not found."));

        mockMvc.perform(get("/api/dashboard/timezones").param("teamId", "999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    void timezonesMapsNonMemberTo403() throws Exception {
        when(dashboardService.timezones(eq("Bearer id-token"), eq(10L)))
                .thenThrow(DomainException.forbidden("TEAM_ACCESS_DENIED", "Only team members can access this team."));

        mockMvc.perform(get("/api/dashboard/timezones").param("teamId", "10")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TEAM_ACCESS_DENIED"));
    }

    @Test
    void statusReturns200WithBody() throws Exception {
        DashboardStatusResponse response = new DashboardStatusResponse(100L, 8, 6, 75, ProposalStatus.IN_PROGRESS);
        when(dashboardService.status(eq("Bearer id-token"), eq(100L))).thenReturn(response);

        mockMvc.perform(get("/api/dashboard/status").param("proposalId", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMembers").value(8))
                .andExpect(jsonPath("$.respondedMembers").value(6))
                .andExpect(jsonPath("$.responseRate").value(75))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }

    @Test
    void statusMissingProposalIdReturns400() throws Exception {
        mockMvc.perform(get("/api/dashboard/status").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void statusMapsProposalNotFoundTo404() throws Exception {
        when(dashboardService.status(eq("Bearer id-token"), eq(404L)))
                .thenThrow(DomainException.notFound("PROPOSAL_NOT_FOUND", "Proposal not found."));

        mockMvc.perform(get("/api/dashboard/status").param("proposalId", "404")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PROPOSAL_NOT_FOUND"));
    }

    @Test
    void statusMapsMissingBearerTokenTo401() throws Exception {
        when(dashboardService.status(eq((String) null), eq(100L)))
                .thenThrow(new AuthenticationException("Bearer token is required."));

        mockMvc.perform(get("/api/dashboard/status").param("proposalId", "100"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
