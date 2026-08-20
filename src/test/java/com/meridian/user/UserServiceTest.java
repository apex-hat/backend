package com.meridian.user;

import com.meridian.auth.AuthenticationException;
import com.meridian.auth.FirebaseTokenVerifier;
import com.meridian.auth.FirebaseUserClaims;
import com.meridian.common.exception.DomainException;
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

    @Test
    void updatesOnlyProvidedFields() {
        User existing = User.builder()
                .id(1L)
                .firebaseUid("firebase-uid")
                .name("Old Name")
                .country("KR")
                .timeZone("UTC")
                .location(null)
                .cultureTag(null)
                .build();

        when(firebaseTokenVerifier.verify("id-token")).thenReturn(claims(null));
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(existing));

        UserUpdateRequest request = new UserUpdateRequest(null, null, "Asia/Seoul", "Seoul", "high-context");
        UserResponse response = userService.updateCurrentUser("Bearer id-token", request);

        assertThat(response.name()).isEqualTo("Old Name");
        assertThat(response.country()).isEqualTo("KR");
        assertThat(response.timeZone()).isEqualTo("Asia/Seoul");
        assertThat(response.location()).isEqualTo("Seoul");
        assertThat(response.cultureTag()).isEqualTo("high-context");
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateCurrentUserRejectsMissingBearerToken() {
        UserUpdateRequest request = new UserUpdateRequest("Name", null, null, null, null);

        assertThatThrownBy(() -> userService.updateCurrentUser(null, request))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void returnsUserSummaryWhenEmailFound() {
        when(firebaseTokenVerifier.verify("id-token")).thenReturn(claims(null));
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(
                User.builder().id(1L).firebaseUid("firebase-uid").build()));
        when(userRepository.findByEmail("teammate@example.com")).thenReturn(Optional.of(
                User.builder().id(2L).name("Teammate").email("teammate@example.com").build()));

        UserSummaryResponse response = userService.searchByEmail("Bearer id-token", "teammate@example.com");

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.name()).isEqualTo("Teammate");
        assertThat(response.email()).isEqualTo("teammate@example.com");
    }

    @Test
    void throwsNotFoundWhenEmailDoesNotMatchAnyUser() {
        when(firebaseTokenVerifier.verify("id-token")).thenReturn(claims(null));
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(
                User.builder().id(1L).firebaseUid("firebase-uid").build()));
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.searchByEmail("Bearer id-token", "nobody@example.com"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void returnsUserSummaryWhenFriendCodeFoundAndNormalizesInput() {
        when(firebaseTokenVerifier.verify("id-token")).thenReturn(claims(null));
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(
                User.builder().id(1L).firebaseUid("firebase-uid").build()));
        when(userRepository.findByFriendCode("MER-ABCD")).thenReturn(Optional.of(
                User.builder().id(2L).name("Teammate").email("teammate@example.com").friendCode("MER-ABCD").build()));

        UserSummaryResponse response = userService.searchByFriendCode("Bearer id-token", "  #mer-abcd  ");

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.name()).isEqualTo("Teammate");
    }

    @Test
    void throwsNotFoundWhenFriendCodeDoesNotMatchAnyUser() {
        when(firebaseTokenVerifier.verify("id-token")).thenReturn(claims(null));
        when(userRepository.findByFirebaseUid("firebase-uid")).thenReturn(Optional.of(
                User.builder().id(1L).firebaseUid("firebase-uid").build()));
        when(userRepository.findByFriendCode("MER-ZZZZ")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.searchByFriendCode("Bearer id-token", "MER-ZZZZ"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("USER_NOT_FOUND");
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
