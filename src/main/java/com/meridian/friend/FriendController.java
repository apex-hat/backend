package com.meridian.friend;

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
 * 친구 요청 보내기/받은 요청 조회/수락·거절/친구 목록 조회.
 */
@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/requests")
    public ResponseEntity<FriendRequestResponse> sendRequest(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody FriendRequestCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(friendService.sendRequest(authorizationHeader, request.friendCode()));
    }

    @GetMapping("/requests")
    public ResponseEntity<List<FriendRequestResponse>> incomingRequests(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(friendService.listIncomingRequests(authorizationHeader));
    }

    @PatchMapping("/requests/{requestId}")
    public ResponseEntity<FriendRequestResponse> respond(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @PathVariable Long requestId,
            @RequestBody FriendRequestRespondRequest request
    ) {
        return ResponseEntity.ok(friendService.respond(authorizationHeader, requestId, request.accept()));
    }

    @GetMapping
    public ResponseEntity<List<FriendResponse>> friends(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(friendService.listFriends(authorizationHeader));
    }
}
