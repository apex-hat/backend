package com.meridian.teaminvite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.common.exception.DomainException;
import com.meridian.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamInviteControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    private TeamInviteService teamInviteService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        teamInviteService = mock(TeamInviteService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TeamInviteController(teamInviteService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendsTeamInvite() throws Exception {
        TeamInviteResponse response = new TeamInviteResponse(1L, 10L, "우리 팀", 2L, "팀원", 1L, "PM", TeamInviteStatus.PENDING, NOW, null);
        when(teamInviteService.sendInvite(eq("Bearer id-token"), eq(10L), eq("MER-BBBB"))).thenReturn(response);

        mockMvc.perform(post("/api/teams/10/invites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new TeamInviteCreateRequest("MER-BBBB"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.invitedUserId").value(2));
    }

    @Test
    void mapsNonPmSenderTo403() throws Exception {
        when(teamInviteService.sendInvite(eq("Bearer id-token"), eq(10L), eq("MER-BBBB")))
                .thenThrow(DomainException.forbidden("TEAM_PM_REQUIRED", "팀 PM만 초대를 보낼 수 있습니다."));

        mockMvc.perform(post("/api/teams/10/invites")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new TeamInviteCreateRequest("MER-BBBB"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TEAM_PM_REQUIRED"));
    }

    @Test
    void returnsIncomingInvites() throws Exception {
        TeamInviteResponse response = new TeamInviteResponse(1L, 10L, "우리 팀", 2L, "팀원", 1L, "PM", TeamInviteStatus.PENDING, NOW, null);
        when(teamInviteService.listIncoming("Bearer id-token")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/team-invites").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teamName").value("우리 팀"));
    }

    @Test
    void respondsToInvite() throws Exception {
        TeamInviteResponse response = new TeamInviteResponse(1L, 10L, "우리 팀", 2L, "팀원", 1L, "PM", TeamInviteStatus.ACCEPTED, NOW, NOW);
        when(teamInviteService.respond(eq("Bearer id-token"), eq(1L), eq(true))).thenReturn(response);

        mockMvc.perform(patch("/api/team-invites/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new TeamInviteRespondRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }
}
