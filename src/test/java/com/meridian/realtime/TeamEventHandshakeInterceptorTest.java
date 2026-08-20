package com.meridian.realtime;

import com.meridian.auth.AuthenticationException;
import com.meridian.auth.FirebaseTokenVerifier;
import com.meridian.auth.FirebaseUserClaims;
import com.meridian.team.TeamMemberRepository;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamEventHandshakeInterceptorTest {

    @Mock
    private FirebaseTokenVerifier firebaseTokenVerifier;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private ServerHttpRequest request;
    @Mock
    private ServerHttpResponse response;
    @Mock
    private WebSocketHandler wsHandler;

    private TeamEventHandshakeInterceptor interceptor;
    private final Map<String, Object> attributes = new HashMap<>();

    @BeforeEach
    void setUp() {
        interceptor = new TeamEventHandshakeInterceptor(firebaseTokenVerifier, userRepository, teamMemberRepository);
    }

    @Test
    void rejectsHandshakeWhenTokenOrTeamIdMissing() {
        when(request.getURI()).thenReturn(URI.create("http://localhost/ws/team-events?teamId=10"));

        boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsHandshakeWhenUserIsNotTeamMember() {
        when(request.getURI()).thenReturn(URI.create("http://localhost/ws/team-events?token=id-token&teamId=10"));
        User user = User.builder().id(1L).firebaseUid("uid-1").build();
        when(firebaseTokenVerifier.verify("id-token")).thenReturn(
                new FirebaseUserClaims("uid-1", "user@example.com", "User", null, null, null, null));
        when(userRepository.findByFirebaseUid("uid-1")).thenReturn(Optional.of(user));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(false);

        boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsHandshakeWhenTokenIsInvalid() {
        when(request.getURI()).thenReturn(URI.create("http://localhost/ws/team-events?token=bad-token&teamId=10"));
        when(firebaseTokenVerifier.verify("bad-token")).thenThrow(new AuthenticationException("Invalid Firebase ID token."));

        boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(accepted).isFalse();
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void acceptsHandshakeAndStoresTeamIdWhenUserIsTeamMember() {
        when(request.getURI()).thenReturn(URI.create("http://localhost/ws/team-events?token=id-token&teamId=10"));
        User user = User.builder().id(1L).firebaseUid("uid-1").build();
        when(firebaseTokenVerifier.verify("id-token")).thenReturn(
                new FirebaseUserClaims("uid-1", "user@example.com", "User", null, null, null, null));
        when(userRepository.findByFirebaseUid("uid-1")).thenReturn(Optional.of(user));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(true);

        boolean accepted = interceptor.beforeHandshake(request, response, wsHandler, attributes);

        assertThat(accepted).isTrue();
        assertThat(attributes.get(TeamEventWebSocketHandler.TEAM_ID_ATTRIBUTE)).isEqualTo(10L);
    }
}
