package com.meridian.user;

import com.meridian.auth.AuthenticationException;
import com.meridian.auth.FirebaseTokenVerifier;
import com.meridian.auth.FirebaseUserClaims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private FirebaseTokenVerifier firebaseTokenVerifier;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void returnsExistingUserForVerifiedFirebaseUid() {
        FirebaseUserClaims claims = claims(null);
        User existing = User.builder()
                .id(1L)
                .firebaseUid("firebase-uid")
                .email("old@example.com")
                .name("Old Name")
                .timeZone("Asia/Seoul")
                .build();

        when(firebaseTokenVerifier.verify("id-token")).thenReturn(claims);
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(existing));

        UserResponse response = userService.getCurrentUser("Bearer id-token");

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.firebaseUid()).isEqualTo("firebase-uid");
        assertThat(response.email()).isEqualTo("old@example.com");
        verify(userRepository, never()).save(any());
    }

    @Test
    void createsUserFromFirebaseClaimsWhenMissing() {
        FirebaseUserClaims claims = claims(null);
        User saved = User.builder()
                .id(2L)
                .firebaseUid("firebase-uid")
                .email("user@example.com")
                .name("User Name")
                .country("KR")
                .timeZone("UTC")
                .location("Seoul")
                .cultureTag("ko-KR")
                .build();

        when(firebaseTokenVerifier.verify("id-token")).thenReturn(claims);
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(saved);

        UserResponse response = userService.getCurrentUser("Bearer id-token");

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.timeZone()).isEqualTo("UTC");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getFirebaseUid()).isEqualTo("firebase-uid");
        assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
        assertThat(captor.getValue().getTimeZone()).isEqualTo("UTC");
    }

    @Test
    void rejectsMissingBearerToken() {
        assertThatThrownBy(() -> userService.getCurrentUser(null))
                .isInstanceOf(AuthenticationException.class);

        verify(firebaseTokenVerifier, never()).verify(any());
    }

    @Test
    void preservesTimezoneClaimWhenPresent() {
        FirebaseUserClaims claims = claims("Asia/Seoul");
        User saved = User.builder()
                .id(2L)
                .firebaseUid("firebase-uid")
                .timeZone("Asia/Seoul")
                .build();

        when(firebaseTokenVerifier.verify("id-token")).thenReturn(claims);
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(saved);

        userService.getCurrentUser("bearer id-token");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getTimeZone()).isEqualTo("Asia/Seoul");
    }

    private FirebaseUserClaims claims(String timeZone) {
        return new FirebaseUserClaims(
                "firebase-uid",
                "user@example.com",
                "User Name",
                "KR",
                timeZone,
                "Seoul",
                "ko-KR"
        );
    }
}
