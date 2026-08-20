package com.meridian.teaminvite;

import com.meridian.common.exception.DomainException;
import com.meridian.notification.Notification;
import com.meridian.notification.NotificationRepository;
import com.meridian.notification.NotificationType;
import com.meridian.team.Team;
import com.meridian.team.TeamMember;
import com.meridian.team.TeamMemberId;
import com.meridian.team.TeamMemberRepository;
import com.meridian.team.TeamRepository;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import com.meridian.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TeamInviteService {

    private static final String ROLE_PM = "PM";
    private static final String ROLE_MEMBER = "MEMBER";

    private final UserService userService;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamInviteRepository teamInviteRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public TeamInviteResponse sendInvite(String authorizationHeader, Long teamId, String friendCode) {
        User inviter = userService.getCurrentUserEntity(authorizationHeader);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> DomainException.notFound("TEAM_NOT_FOUND", "팀을 찾을 수 없습니다."));

        if (!teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(teamId, inviter.getId(), ROLE_PM)) {
            throw DomainException.forbidden("TEAM_PM_REQUIRED", "팀 PM만 초대를 보낼 수 있습니다.");
        }

        if (!StringUtils.hasText(friendCode)) {
            throw DomainException.badRequest("FRIEND_CODE_REQUIRED", "고유 ID를 입력해주세요.");
        }
        String normalized = friendCode.trim().toUpperCase().replaceFirst("^#", "");
        User invitee = userRepository.findByFriendCode(normalized)
                .orElseThrow(() -> DomainException.notFound("USER_NOT_FOUND", "해당 고유 ID의 사용자를 찾을 수 없습니다."));

        if (teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, invitee.getId())) {
            throw DomainException.conflict("TEAM_MEMBER_ALREADY_EXISTS", "이미 이 팀에 속한 사용자입니다.");
        }
        Optional<TeamInvite> existingInvite = teamInviteRepository.findByTeam_IdAndInvitedUser_Id(teamId, invitee.getId());
        TeamInvite invite;
        if (existingInvite.isPresent()) {
            invite = existingInvite.get();
            if (invite.getStatus() != TeamInviteStatus.REJECTED) {
                throw DomainException.conflict("TEAM_INVITE_EXISTS", "이미 초대를 보냈거나 처리된 사용자입니다.");
            }
            invite.setInvitedBy(inviter);
            invite.setStatus(TeamInviteStatus.PENDING);
            invite.setRespondedAt(null);
        } else {
            invite = TeamInvite.builder()
                    .team(team)
                    .invitedUser(invitee)
                    .invitedBy(inviter)
                    .status(TeamInviteStatus.PENDING)
                    .build();
        }
        invite = teamInviteRepository.save(invite);

        notificationRepository.save(Notification.builder()
                .user(invitee)
                .type(NotificationType.TEAM_INVITE)
                .title("팀 초대")
                .content(inviter.getName() + "님이 '" + team.getName() + "' 팀에 초대했습니다.")
                .isRead(false)
                .build());

        return TeamInviteResponse.from(invite);
    }

    @Transactional(readOnly = true)
    public List<TeamInviteResponse> listIncoming(String authorizationHeader) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        return teamInviteRepository.findAllByInvitedUser_IdAndStatusOrderByCreatedAtDesc(user.getId(), TeamInviteStatus.PENDING)
                .stream()
                .map(TeamInviteResponse::from)
                .toList();
    }

    /** PM이 본인 팀에서 보낸 초대 현황(대기/수락/거절 포함)을 확인할 때 사용한다. */
    @Transactional(readOnly = true)
    public List<TeamInviteResponse> listForTeam(String authorizationHeader, Long teamId) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        if (!teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(teamId, user.getId(), ROLE_PM)) {
            throw DomainException.forbidden("TEAM_PM_REQUIRED", "팀 PM만 초대 현황을 볼 수 있습니다.");
        }
        return teamInviteRepository.findAllByTeam_IdOrderByCreatedAtDesc(teamId).stream()
                .map(TeamInviteResponse::from)
                .toList();
    }

    /** 아직 응답하지 않은 초대를 PM이 취소한다. 수락/거절된 초대는 취소 대상이 아니다. */
    @Transactional
    public void cancelInvite(String authorizationHeader, Long teamId, Long inviteId) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        if (!teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(teamId, user.getId(), ROLE_PM)) {
            throw DomainException.forbidden("TEAM_PM_REQUIRED", "팀 PM만 초대를 취소할 수 있습니다.");
        }
        TeamInvite invite = findPendingInviteInTeam(teamId, inviteId);
        teamInviteRepository.delete(invite);
    }

    /** 대기 중인 초대를 받은 사람에게 알림을 한 번 더 보낸다(리마인더). 초대 자체는 그대로 유지된다. */
    @Transactional
    public TeamInviteResponse resendInvite(String authorizationHeader, Long teamId, Long inviteId) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        if (!teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(teamId, user.getId(), ROLE_PM)) {
            throw DomainException.forbidden("TEAM_PM_REQUIRED", "팀 PM만 초대를 다시 보낼 수 있습니다.");
        }
        TeamInvite invite = findPendingInviteInTeam(teamId, inviteId);

        notificationRepository.save(Notification.builder()
                .user(invite.getInvitedUser())
                .type(NotificationType.TEAM_INVITE)
                .title("팀 초대")
                .content(user.getName() + "님이 '" + invite.getTeam().getName() + "' 팀 초대를 다시 보냈습니다.")
                .isRead(false)
                .build());

        return TeamInviteResponse.from(invite);
    }

    private TeamInvite findPendingInviteInTeam(Long teamId, Long inviteId) {
        TeamInvite invite = teamInviteRepository.findById(inviteId)
                .orElseThrow(() -> DomainException.notFound("TEAM_INVITE_NOT_FOUND", "초대를 찾을 수 없습니다."));
        if (!invite.getTeam().getId().equals(teamId)) {
            throw DomainException.notFound("TEAM_INVITE_NOT_FOUND", "초대를 찾을 수 없습니다.");
        }
        if (invite.getStatus() != TeamInviteStatus.PENDING) {
            throw DomainException.conflict("TEAM_INVITE_ALREADY_RESOLVED", "이미 처리된 초대입니다.");
        }
        return invite;
    }

    @Transactional
    public TeamInviteResponse respond(String authorizationHeader, Long inviteId, boolean accept) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        TeamInvite invite = teamInviteRepository.findById(inviteId)
                .orElseThrow(() -> DomainException.notFound("TEAM_INVITE_NOT_FOUND", "초대를 찾을 수 없습니다."));

        if (!invite.getInvitedUser().getId().equals(user.getId())) {
            throw DomainException.forbidden("TEAM_INVITE_ACCESS_DENIED", "본인에게 온 초대만 응답할 수 있습니다.");
        }
        if (invite.getStatus() != TeamInviteStatus.PENDING) {
            throw DomainException.conflict("TEAM_INVITE_ALREADY_RESOLVED", "이미 처리된 초대입니다.");
        }

        invite.setStatus(accept ? TeamInviteStatus.ACCEPTED : TeamInviteStatus.REJECTED);
        invite.setRespondedAt(Instant.now());

        if (accept && !teamMemberRepository.existsByTeam_IdAndUser_Id(invite.getTeam().getId(), user.getId())) {
            teamMemberRepository.save(TeamMember.builder()
                    .id(new TeamMemberId(invite.getTeam().getId(), user.getId()))
                    .team(invite.getTeam())
                    .user(user)
                    .role(ROLE_MEMBER)
                    .build());
        }

        notificationRepository.save(Notification.builder()
                .user(invite.getInvitedBy())
                .type(NotificationType.TEAM_INVITE)
                .title("팀 초대 응답")
                .content(accept
                        ? user.getName() + "님이 '" + invite.getTeam().getName() + "' 팀 초대를 수락했습니다."
                        : user.getName() + "님이 '" + invite.getTeam().getName() + "' 팀 초대를 거절했습니다.")
                .isRead(false)
                .build());

        return TeamInviteResponse.from(invite);
    }
}
