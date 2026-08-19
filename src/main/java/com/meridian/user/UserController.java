package com.meridian.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

    /** 팀원 초대 UI에서 이메일로 상대를 찾아 userId를 얻기 위한 검색. 정확히 일치하는 이메일만 조회한다. */
    @GetMapping("/search")
    public ResponseEntity<UserSummaryResponse> search(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            @RequestParam String email
    ) {
        return ResponseEntity.ok(userService.searchByEmail(authorizationHeader, email));
    }
}
