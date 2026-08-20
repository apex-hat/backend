package com.meridian.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.auth.AuthenticationException;
import com.meridian.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerTest {

    private UserService userService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsCurrentUser() throws Exception {
        UserResponse response = new UserResponse(
                1L,
                "firebase-uid",
                "User Name",
                "user@example.com",
                "KR",
                "UTC",
                "Seoul",
                "ko-KR",
                "MER-AAAA",
                Instant.parse("2026-08-12T00:00:00Z"),
                Instant.parse("2026-08-12T00:00:01Z")
        );
        when(userService.getCurrentUser("Bearer id-token")).thenReturn(response);

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.firebaseUid").value("firebase-uid"))
                .andExpect(jsonPath("$.timeZone").value("UTC"));

        verify(userService).getCurrentUser("Bearer id-token");
    }

    @Test
    void mapsAuthenticationFailuresTo401ErrorResponse() throws Exception {
        when(userService.getCurrentUser(null))
                .thenThrow(new AuthenticationException("Authorization Bearer token is required."));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void updatesCurrentUserProfile() throws Exception {
        UserUpdateRequest request = new UserUpdateRequest(null, null, "Asia/Seoul", "Seoul", "high-context");
        UserResponse response = new UserResponse(
                1L, "firebase-uid", "User Name", "user@example.com", "KR", "Asia/Seoul", "Seoul", "high-context", "MER-AAAA",
                Instant.parse("2026-08-12T00:00:00Z"), Instant.parse("2026-08-12T00:00:01Z"));

        when(userService.updateCurrentUser(eq("Bearer id-token"), any(UserUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(patch("/api/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timeZone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.location").value("Seoul"));
    }

    @Test
    void searchByEmailQueryParamRoutesToEmailSearch() throws Exception {
        UserSummaryResponse response = new UserSummaryResponse(2L, "Teammate", "teammate@example.com");
        when(userService.searchByEmail("Bearer id-token", "teammate@example.com")).thenReturn(response);

        mockMvc.perform(get("/api/users/search")
                        .param("email", "teammate@example.com")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.name").value("Teammate"));
    }

    @Test
    void searchByFriendCodeQueryParamRoutesToFriendCodeSearch() throws Exception {
        UserSummaryResponse response = new UserSummaryResponse(3L, "Friend", "friend@example.com");
        when(userService.searchByFriendCode("Bearer id-token", "MER-ABCD")).thenReturn(response);

        mockMvc.perform(get("/api/users/search")
                        .param("friendCode", "MER-ABCD")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Friend"));
    }
}
