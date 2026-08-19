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

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String FRIEND_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int FRIEND_CODE_LENGTH = 4;
    private static final int FRIEND_CODE_MAX_ATTEMPTS = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

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
        User user = userRepository.findByFirebaseUid(claims.uid())
                .orElseGet(() -> userRepository.save(newUser(claims)));

        // friendCode는 이 필드가 추가되기 전에 가입한 기존 사용자에게는 없을 수 있어, 처음 만나는 시점에 채워준다.
        if (!StringUtils.hasText(user.getFriendCode())) {
            user.setFriendCode(generateUniqueFriendCode());
        }
        return user;
    }

    /** PATCH /api/users/me — 전달된 필드만 반영하는 부분 수정. null은 변경하지 않음을 뜻한다. */
    @Transactional
    public UserResponse updateCurrentUser(String authorizationHeader, UserUpdateRequest request) {
        User user = getCurrentUserEntity(authorizationHeader);

        if (StringUtils.hasText(request.name())) user.setName(request.name());
        if (StringUtils.hasText(request.country())) user.setCountry(request.country());
        if (StringUtils.hasText(request.timeZone())) user.setTimeZone(request.timeZone());
        if (StringUtils.hasText(request.location())) user.setLocation(request.location());
        if (StringUtils.hasText(request.cultureTag())) user.setCultureTag(request.cultureTag());

        return UserResponse.from(user);
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

    private String generateUniqueFriendCode() {
        for (int attempt = 0; attempt < FRIEND_CODE_MAX_ATTEMPTS; attempt++) {
            StringBuilder suffix = new StringBuilder(FRIEND_CODE_LENGTH);
            for (int i = 0; i < FRIEND_CODE_LENGTH; i++) {
                suffix.append(FRIEND_CODE_ALPHABET.charAt(RANDOM.nextInt(FRIEND_CODE_ALPHABET.length())));
            }
            String candidate = "MER-" + suffix;
            if (!userRepository.existsByFriendCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique friend code.");
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
