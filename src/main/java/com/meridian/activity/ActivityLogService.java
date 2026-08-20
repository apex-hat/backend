package com.meridian.activity;

import com.meridian.common.exception.DomainException;
import com.meridian.team.Team;
import com.meridian.team.TeamMemberRepository;
import com.meridian.user.User;
import com.meridian.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final UserService userService;
    private final TeamMemberRepository teamMemberRepository;
    private final ActivityLogRepository activityLogRepository;

    /** 다른 도메인 서비스(Team/Opinion 등)가 모더레이션 행위 직후 호출해 기록을 남긴다. */
    @Transactional
    public void record(Team team, User actor, User targetUser, ActivityAction action, String description) {
        activityLogRepository.save(ActivityLog.builder()
                .team(team)
                .actor(actor)
                .targetUser(targetUser)
                .action(action)
                .description(description)
                .build());
    }

    /** 팀원이면 누구나 팀의 활동 로그를 볼 수 있다(모더레이션 투명성 목적). */
    @Transactional(readOnly = true)
    public List<ActivityLogResponse> listForTeam(String authorizationHeader, Long teamId) {
        User currentUser = userService.getCurrentUserEntity(authorizationHeader);
        if (!teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, currentUser.getId())) {
            throw DomainException.forbidden("TEAM_ACCESS_DENIED", "Only team members can access this team.");
        }
        return activityLogRepository.findAllByTeam_IdOrderByCreatedAtDesc(teamId).stream()
                .map(ActivityLogResponse::from)
                .toList();
    }
}
