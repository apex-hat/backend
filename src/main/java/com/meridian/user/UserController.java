package com.meridian.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
