package com.meridian.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * README §6.2 User
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader
    ) {
        return ResponseEntity.ok(userService.getCurrentUser(authorizationHeader));
    }

    /** country/timeZone/location/cultureTag 등 JIT 동기화가 못 채운 프로필 정보를 회원가입 이후 채울 수 있게 한다. */
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMe(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestBody UserUpdateRequest request
    ) {
        return ResponseEntity.ok(userService.updateCurrentUser(authorizationHeader, request));
    }

    /** 팀원 초대 UI에서 이메일로 상대를 찾아 userId를 얻기 위한 검색. 정확히 일치하는 이메일만 조회한다. */
    @GetMapping(value = "/search", params = "email")
    public ResponseEntity<UserSummaryResponse> searchByEmail(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam String email
    ) {
        return ResponseEntity.ok(userService.searchByEmail(authorizationHeader, email));
    }

    /** 친구/팀 초대 UI에서 고유 ID(friendCode)로 상대를 찾아 userId를 얻기 위한 검색. */
    @GetMapping(value = "/search", params = "friendCode")
    public ResponseEntity<UserSummaryResponse> searchByFriendCode(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam String friendCode
    ) {
        return ResponseEntity.ok(userService.searchByFriendCode(authorizationHeader, friendCode));
    }
}
