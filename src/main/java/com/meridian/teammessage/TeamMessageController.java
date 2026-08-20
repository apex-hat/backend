package com.meridian.teammessage;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 팀 소속 사용자끼리 주고받는 그룹 채팅. 팀에 속하지 않은 사용자와는 주고받을 수 없다.
 */
@RestController
@RequiredArgsConstructor
public class TeamMessageController {

    private final TeamMessageService teamMessageService;

    @PostMapping("/api/teams/{teamId}/messages")
    public ResponseEntity<TeamMessageResponse> send(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long teamId,
            @RequestBody TeamMessageCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamMessageService.sendMessage(authorizationHeader, teamId, request.content()));
    }

    @GetMapping("/api/teams/{teamId}/messages")
    public ResponseEntity<List<TeamMessageResponse>> list(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long teamId
    ) {
        return ResponseEntity.ok(teamMessageService.getMessages(authorizationHeader, teamId));
    }
}
