package com.meridian.teammessage;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamMessageControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    private TeamMessageService teamMessageService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        teamMessageService = mock(TeamMessageService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new TeamMessageController(teamMessageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendsMessage() throws Exception {
        TeamMessageResponse response = new TeamMessageResponse(1L, 10L, 2L, "황성민", "안녕하세요", NOW);
        when(teamMessageService.sendMessage(eq("Bearer id-token"), eq(10L), eq("안녕하세요"))).thenReturn(response);

        mockMvc.perform(post("/api/teams/10/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new TeamMessageCreateRequest("안녕하세요"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("안녕하세요"))
                .andExpect(jsonPath("$.senderName").value("황성민"));
    }

    @Test
    void mapsAccessDeniedTo403() throws Exception {
        when(teamMessageService.sendMessage(eq("Bearer id-token"), eq(10L), eq("안녕하세요")))
                .thenThrow(DomainException.forbidden("TEAM_ACCESS_DENIED", "해당 팀에 소속된 사용자만 메시지를 보낼 수 있습니다."));

        mockMvc.perform(post("/api/teams/10/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new TeamMessageCreateRequest("안녕하세요"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("TEAM_ACCESS_DENIED"));
    }

    @Test
    void returnsTeamConversation() throws Exception {
        TeamMessageResponse response = new TeamMessageResponse(1L, 10L, 2L, "황성민", "반갑습니다", NOW);
        when(teamMessageService.getMessages("Bearer id-token", 10L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/teams/10/messages").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("반갑습니다"));
    }
}
