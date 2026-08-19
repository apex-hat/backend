package com.meridian.user;

import com.meridian.auth.AuthenticationException;
import com.meridian.auth.FirebaseTokenVerifier;
import com.meridian.auth.FirebaseUserClaims;
import com.meridian.common.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final UserRepository userRepository;

    @Transactional
    public UserResponse getCurrentUser(String authorizationHeader) {
        return UserResponse.from(getCurrentUserEntity(authorizationHeader));
    }

    @Transactional
    public User getCurrentUserEntity(String authorizationHeader) {
        FirebaseUserClaims claims = firebaseTokenVerifier.verify(extractBearerToken(authorizationHeader));
        if (!StringUtils.hasText(claims.uid())) {
            throw new AuthenticationException("Invalid Firebase ID token.");
        }
        return userRepository.findByFirebaseUid(claims.uid())
                .orElseGet(() -> userRepository.save(newUser(claims)));
    }

    /** 팀원 초대 UI에서 이메일로 사용자를 찾을 때 사용. 검색 자체도 인증된 사용자만 가능하다. */
    @Transactional
    public UserSummaryResponse searchByEmail(String authorizationHeader, String email) {
        getCurrentUserEntity(authorizationHeader);
        User found = userRepository.findByEmail(email)
                .orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND", "해당 이메일의 사용자를 찾을 수 없습니다."));
        return UserSummaryResponse.from(found);
    }

    private String extractBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)
                || !authorizationHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new AuthenticationException(HttpHeaders.AUTHORIZATION + " Bearer token is required.");
        }

        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            throw new AuthenticationException(HttpHeaders.AUTHORIZATION + " Bearer token is required.");
        }
        return token;
    }

    private User newUser(FirebaseUserClaims claims) {
        return User.builder()
                .firebaseUid(claims.uid())
                .email(claims.email())
                .name(claims.name())
                .country(claims.country())
                .timeZone(claims.effectiveTimeZone())
                .location(claims.location())
                .cultureTag(claims.cultureTag())
                .build();
    }
}
