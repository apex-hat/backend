package com.meridian.user;

import com.meridian.auth.AuthenticationException;
import com.meridian.auth.FirebaseTokenVerifier;
import com.meridian.auth.FirebaseUserClaims;
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
        FirebaseUserClaims claims = firebaseTokenVerifier.verify(extractBearerToken(authorizationHeader));
        if (!StringUtils.hasText(claims.uid())) {
            throw new AuthenticationException("Invalid Firebase ID token.");
        }
        User user = userRepository.findByFirebaseUid(claims.uid())
                .orElseGet(() -> userRepository.save(newUser(claims)));
        return UserResponse.from(user);
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
