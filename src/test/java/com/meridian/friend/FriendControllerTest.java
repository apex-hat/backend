package com.meridian.friend;

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

class FriendControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-19T00:00:00Z");

    private FriendService friendService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        friendService = mock(FriendService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FriendController(friendService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendsFriendRequest() throws Exception {
        FriendRequestResponse response = new FriendRequestResponse(1L, 1L, "황성민", 2L, "팀원", FriendRequestStatus.PENDING, NOW, null);
        when(friendService.sendRequest(eq("Bearer id-token"), eq("MER-BBBB"))).thenReturn(response);

        mockMvc.perform(post("/api/friends/requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FriendRequestCreateRequest("MER-BBBB"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.addresseeId").value(2));
    }

    @Test
    void mapsUnknownFriendCodeTo404() throws Exception {
        when(friendService.sendRequest(eq("Bearer id-token"), eq("MER-ZZZZ")))
                .thenThrow(DomainException.notFound("USER_NOT_FOUND", "해당 고유 ID의 사용자를 찾을 수 없습니다."));

        mockMvc.perform(post("/api/friends/requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FriendRequestCreateRequest("MER-ZZZZ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));
    }

    @Test
    void returnsIncomingRequests() throws Exception {
        FriendRequestResponse response = new FriendRequestResponse(1L, 2L, "팀원", 1L, "황성민", FriendRequestStatus.PENDING, NOW, null);
        when(friendService.listIncomingRequests("Bearer id-token")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/friends/requests").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requesterName").value("팀원"));
    }

    @Test
    void respondsToRequest() throws Exception {
        FriendRequestResponse response = new FriendRequestResponse(1L, 2L, "팀원", 1L, "황성민", FriendRequestStatus.ACCEPTED, NOW, NOW);
        when(friendService.respond(eq("Bearer id-token"), eq(1L), eq(true))).thenReturn(response);

        mockMvc.perform(patch("/api/friends/requests/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new FriendRequestRespondRequest(true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void returnsFriendList() throws Exception {
        FriendResponse response = new FriendResponse(2L, "팀원", "teammate@example.com", "MER-BBBB", NOW);
        when(friendService.listFriends("Bearer id-token")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/friends").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].friendCode").value("MER-BBBB"));
    }
}
