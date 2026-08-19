package com.meridian.message;

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

class MessageControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    private MessageService messageService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        messageService = mock(MessageService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new MessageController(messageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void sendsMessage() throws Exception {
        MessageResponse response = new MessageResponse(1L, 1L, 2L, "안녕하세요", false, NOW);
        when(messageService.sendMessage(eq("Bearer id-token"), eq(2L), eq("안녕하세요"))).thenReturn(response);

        mockMvc.perform(post("/api/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MessageCreateRequest(2L, "안녕하세요"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("안녕하세요"))
                .andExpect(jsonPath("$.receiverId").value(2));
    }

    @Test
    void mapsNotFriendsTo403() throws Exception {
        when(messageService.sendMessage(eq("Bearer id-token"), eq(2L), eq("안녕하세요")))
                .thenThrow(DomainException.forbidden("NOT_FRIENDS", "친구 사이에만 메시지를 보낼 수 있습니다."));

        mockMvc.perform(post("/api/messages")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer id-token")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new MessageCreateRequest(2L, "안녕하세요"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_FRIENDS"));
    }

    @Test
    void returnsConversation() throws Exception {
        MessageResponse response = new MessageResponse(1L, 2L, 1L, "반갑습니다", true, NOW);
        when(messageService.getConversation("Bearer id-token", 2L)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/messages/2").header(HttpHeaders.AUTHORIZATION, "Bearer id-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("반갑습니다"));
    }
}
