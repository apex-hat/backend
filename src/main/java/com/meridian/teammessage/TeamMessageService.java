package com.meridian.teammessage;

import com.meridian.common.exception.DomainException;
import com.meridian.realtime.TeamEventPublisher;
import com.meridian.realtime.TeamEventType;
import com.meridian.team.Team;
import com.meridian.team.TeamMemberRepository;
import com.meridian.team.TeamRepository;
import com.meridian.user.User;
import com.meridian.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamMessageService {

    private final UserService userService;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamMessageRepository teamMessageRepository;
    private final TeamEventPublisher teamEventPublisher;

    @Transactional
    public TeamMessageResponse sendMessage(String authorizationHeader, Long teamId, String content) {
        User sender = userService.getCurrentUserEntity(authorizationHeader);

        if (!StringUtils.hasText(content)) {
            throw DomainException.badRequest("MESSAGE_CONTENT_REQUIRED", "메시지 내용을 입력해주세요.");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> DomainException.notFound("TEAM_NOT_FOUND", "Team not found."));

        if (!teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, sender.getId())) {
            throw DomainException.forbidden("TEAM_ACCESS_DENIED", "해당 팀에 소속된 사용자만 메시지를 보낼 수 있습니다.");
        }

        TeamMessage saved = teamMessageRepository.save(TeamMessage.builder()
                .team(team)
                .sender(sender)
                .content(content.trim())
                .build());

        teamEventPublisher.publish(TeamEventType.MESSAGE_CREATED, teamId, null);

        return TeamMessageResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<TeamMessageResponse> getMessages(String authorizationHeader, Long teamId) {
        User user = userService.getCurrentUserEntity(authorizationHeader);

        if (!teamRepository.existsById(teamId)) {
            throw DomainException.notFound("TEAM_NOT_FOUND", "Team not found.");
        }
        if (!teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, user.getId())) {
            throw DomainException.forbidden("TEAM_ACCESS_DENIED", "해당 팀에 소속된 사용자만 대화를 볼 수 있습니다.");
        }

        return teamMessageRepository.findAllByTeam_IdOrderByCreatedAtAsc(teamId).stream()
                .map(TeamMessageResponse::from)
                .toList();
    }
}
