package com.meridian.message;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 친구 사이의 1:1 메시지. 친구가 아닌 사용자와는 주고받을 수 없다.
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<MessageResponse> send(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody MessageCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(messageService.sendMessage(authorizationHeader, request.receiverId(), request.content()));
    }

    @GetMapping("/{friendUserId}")
    public ResponseEntity<List<MessageResponse>> conversation(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long friendUserId
    ) {
        return ResponseEntity.ok(messageService.getConversation(authorizationHeader, friendUserId));
    }
}
