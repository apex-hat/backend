package com.meridian.teaminvite;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 팀 초대 보내기(PM 전용)/받은 초대 조회/수락·거절. 수락 전까지는 실제 팀원이 아니다.
 */
@RestController
@RequiredArgsConstructor
public class TeamInviteController {

    private final TeamInviteService teamInviteService;

    @PostMapping("/api/teams/{teamId}/invites")
    public ResponseEntity<TeamInviteResponse> sendInvite(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long teamId,
            @RequestBody TeamInviteCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(teamInviteService.sendInvite(authorizationHeader, teamId, request.friendCode()));
    }

    @GetMapping("/api/team-invites")
    public ResponseEntity<List<TeamInviteResponse>> incomingInvites(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(teamInviteService.listIncoming(authorizationHeader));
    }

    @PatchMapping("/api/team-invites/{inviteId}")
    public ResponseEntity<TeamInviteResponse> respond(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long inviteId,
            @RequestBody TeamInviteRespondRequest request
    ) {
        return ResponseEntity.ok(teamInviteService.respond(authorizationHeader, inviteId, request.accept()));
    }
}
