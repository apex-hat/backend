package com.meridian.notification;

import com.meridian.auth.AuthenticationException;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest {

    private NotificationService notificationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsNotificationList() throws Exception {
        NotificationResponse response = new NotificationResponse(
                1L, 5L, NotificationType.PROPOSAL_CREATED, "새 제안", "새로운 제안이 등록되었습니다.",
                false, Instant.parse("2026-08-19T00:00:00Z"));
        when(notificationService.listNotifications("Bearer id-token")).thenReturn(List.of(response));

        mockMvc.perform(get("/api/notifications").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("PROPOSAL_CREATED"))
                .andExpect(jsonPath("$[0].isRead").value(false));
    }

    @Test
    void mapsMissingBearerTokenTo401() throws Exception {
        when(notificationService.listNotifications(null))
                .thenThrow(new AuthenticationException("Bearer token is required."));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void marksNotificationAsRead() throws Exception {
        NotificationResponse response = new NotificationResponse(
                1L, 5L, NotificationType.PROPOSAL_CREATED, "새 제안", "새로운 제안이 등록되었습니다.",
                true, Instant.parse("2026-08-19T00:00:00Z"));
        when(notificationService.markRead(eq("Bearer id-token"), eq(1L))).thenReturn(response);

        mockMvc.perform(patch("/api/notifications/1").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));
    }
}
