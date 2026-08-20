package com.meridian.realtime;

import com.meridian.auth.FirebaseTokenVerifier;
import com.meridian.auth.FirebaseUserClaims;
import com.meridian.team.TeamMemberRepository;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

/**
 * 팀 실시간 이벤트 WebSocket 연결을 맺기 전에, REST API와 동일한 방식(Firebase ID Token)으로
 * 인증하고 요청한 teamId에 실제로 소속된 팀원인지 검증한다. 브라우저 WebSocket API는 커스텀
 * 헤더를 못 보내므로 Authorization 헤더 대신 쿼리 파라미터(token)로 받는다.
 */
@Component
@RequiredArgsConstructor
public class TeamEventHandshakeInterceptor implements HandshakeInterceptor {

    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final UserRepository userRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                    WebSocketHandler wsHandler, Map<String, Object> attributes) {
        UriComponents uri = UriComponentsBuilder.fromUri(request.getURI()).build();
        String token = uri.getQueryParams().getFirst("token");
        String teamIdParam = uri.getQueryParams().getFirst("teamId");

        if (!StringUtils.hasText(token) || !StringUtils.hasText(teamIdParam)) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        Long teamId;
        try {
            teamId = Long.valueOf(teamIdParam);
        } catch (NumberFormatException ex) {
            response.setStatusCode(HttpStatus.BAD_REQUEST);
            return false;
        }

        try {
            FirebaseUserClaims claims = firebaseTokenVerifier.verify(token);
            User user = userRepository.findByFirebaseUid(claims.uid()).orElse(null);
            if (user == null || !teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, user.getId())) {
                response.setStatusCode(HttpStatus.FORBIDDEN);
                return false;
            }
            attributes.put(TeamEventWebSocketHandler.TEAM_ID_ATTRIBUTE, teamId);
            return true;
        } catch (RuntimeException ex) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
