package com.meridian.team;

import com.meridian.common.exception.BusinessException;
import com.meridian.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamControllerTest {

    private TeamService teamService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        teamService = mock(TeamService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TeamController(teamService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsTeam() throws Exception {
        when(teamService.createTeam(eq("Bearer id-token"), any(TeamCreateRequest.class)))
                .thenReturn(new TeamResponse(10L, "Meridian", "KR", "ko-KR", null, null));

        mockMvc.perform(post("/api/teams")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Meridian",
                                  "country": "KR",
                                  "cultureTag": "ko-KR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Meridian"));
    }

    @Test
    void listsTeams() throws Exception {
        when(teamService.listTeams("Bearer id-token"))
                .thenReturn(List.of(new TeamResponse(10L, "Meridian", "KR", "ko-KR", null, null)));

        mockMvc.perform(get("/api/teams").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(10));
    }

    @Test
    void mapsForbiddenTo403() throws Exception {
        when(teamService.getTeam("Bearer id-token", 10L))
                .thenThrow(new BusinessException(HttpStatus.FORBIDDEN, "TEAM_ACCESS_DENIED",
                        "Only team members can access this team."));

        mockMvc.perform(get("/api/teams/10").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TEAM_ACCESS_DENIED"));
    }

    @Test
    void mapsNotFoundTo404() throws Exception {
        when(teamService.listMembers("Bearer id-token", 404L))
                .thenThrow(new BusinessException(HttpStatus.NOT_FOUND, "TEAM_NOT_FOUND", "Team not found."));

        mockMvc.perform(get("/api/teams/404/members").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("TEAM_NOT_FOUND"));
    }

    @Test
    void mapsConflictTo409() throws Exception {
        when(teamService.addMember(eq("Bearer id-token"), eq(10L), any(TeamMemberAddRequest.class)))
                .thenThrow(new BusinessException(HttpStatus.CONFLICT, "TEAM_MEMBER_ALREADY_EXISTS",
                        "User already belongs to this team."));

        mockMvc.perform(post("/api/teams/10/members")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 2,
                                  "role": "MEMBER"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TEAM_MEMBER_ALREADY_EXISTS"));
    }

    @Test
    void removesMember() throws Exception {
        mockMvc.perform(delete("/api/teams/10/members/2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isNoContent());

        verify(teamService).removeMember("Bearer id-token", 10L, 2L);
    }
}
