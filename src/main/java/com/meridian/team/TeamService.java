package com.meridian.team;

import com.meridian.common.exception.DomainException;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import com.meridian.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TeamService {

    private static final String ROLE_PM = "PM";
    private static final String ROLE_MEMBER = "MEMBER";

    private final UserService userService;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;

    @Transactional
    public TeamResponse createTeam(String authorizationHeader, TeamCreateRequest request) {
        User currentUser = userService.getCurrentUserEntity(authorizationHeader);
        validateTeamCreateRequest(request);

        Team team = teamRepository.save(Team.builder()
                .name(request.name().trim())
                .country(blankToNull(request.country()))
                .cultureTag(blankToNull(request.cultureTag()))
                .build());

        teamMemberRepository.save(TeamMember.builder()
                .id(new TeamMemberId(team.getId(), currentUser.getId()))
                .team(team)
                .user(currentUser)
                .role(ROLE_PM)
                .build());

        return TeamResponse.from(team);
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> listTeams(String authorizationHeader) {
        User currentUser = userService.getCurrentUserEntity(authorizationHeader);
        return teamMemberRepository.findAllByUser_Id(currentUser.getId()).stream()
                .map(TeamMember::getTeam)
                .map(TeamResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(String authorizationHeader, Long teamId) {
        User currentUser = userService.getCurrentUserEntity(authorizationHeader);
        Team team = findTeam(teamId);
        requireTeamMember(team.getId(), currentUser.getId());
        return TeamResponse.from(team);
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> listMembers(String authorizationHeader, Long teamId) {
        User currentUser = userService.getCurrentUserEntity(authorizationHeader);
        Team team = findTeam(teamId);
        requireTeamMember(team.getId(), currentUser.getId());
        return teamMemberRepository.findAllByTeam_Id(team.getId()).stream()
                .map(TeamMemberResponse::from)
                .toList();
    }

    @Transactional
    public TeamMemberResponse addMember(String authorizationHeader, Long teamId, TeamMemberAddRequest request) {
        User currentUser = userService.getCurrentUserEntity(authorizationHeader);
        Team team = findTeam(teamId);
        requirePm(team.getId(), currentUser.getId());
        validateMemberAddRequest(request);

        User targetUser = userRepository.findById(request.userId())
                .orElseThrow(() -> notFound("USER_NOT_FOUND", "User not found."));
        if (teamMemberRepository.existsByTeam_IdAndUser_Id(team.getId(), targetUser.getId())) {
            throw conflict("TEAM_MEMBER_ALREADY_EXISTS", "User already belongs to this team.");
        }

        TeamMember member = teamMemberRepository.save(TeamMember.builder()
                .id(new TeamMemberId(team.getId(), targetUser.getId()))
                .team(team)
                .user(targetUser)
                .role(normalizeRole(request.role()))
                .build());

        return TeamMemberResponse.from(member);
    }

    @Transactional
    public void removeMember(String authorizationHeader, Long teamId, Long userId) {
        User currentUser = userService.getCurrentUserEntity(authorizationHeader);
        Team team = findTeam(teamId);
        requirePm(team.getId(), currentUser.getId());

        TeamMember member = teamMemberRepository.findByTeam_IdAndUser_Id(team.getId(), userId)
                .orElseThrow(() -> notFound("TEAM_MEMBER_NOT_FOUND", "Team member not found."));
        teamMemberRepository.delete(member);
    }

    private Team findTeam(Long teamId) {
        if (teamId == null) {
            throw badRequest("TEAM_ID_REQUIRED", "Team id is required.");
        }
        return teamRepository.findById(teamId)
                .orElseThrow(() -> notFound("TEAM_NOT_FOUND", "Team not found."));
    }

    private void requireTeamMember(Long teamId, Long userId) {
        if (!teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)) {
            throw forbidden("TEAM_ACCESS_DENIED", "Only team members can access this team.");
        }
    }

    private void requirePm(Long teamId, Long userId) {
        if (!teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(teamId, userId, ROLE_PM)) {
            throw forbidden("TEAM_PM_REQUIRED", "Only team PM can manage team members.");
        }
    }

    private void validateTeamCreateRequest(TeamCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.name())) {
            throw badRequest("TEAM_NAME_REQUIRED", "Team name is required.");
        }
    }

    private void validateMemberAddRequest(TeamMemberAddRequest request) {
        if (request == null || request.userId() == null) {
            throw badRequest("USER_ID_REQUIRED", "User id is required.");
        }
        normalizeRole(request.role());
    }

    private String normalizeRole(String role) {
        if (!StringUtils.hasText(role)) {
            throw badRequest("TEAM_MEMBER_ROLE_REQUIRED", "Team member role is required.");
        }

        String normalized = role.trim().toUpperCase();
        if (!ROLE_PM.equals(normalized) && !ROLE_MEMBER.equals(normalized)) {
            throw badRequest("TEAM_MEMBER_ROLE_INVALID", "Team member role must be PM or MEMBER.");
        }
        return normalized;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private DomainException badRequest(String code, String message) {
        return DomainException.badRequest(code, message);
    }

    private DomainException forbidden(String code, String message) {
        return DomainException.forbidden(code, message);
    }

    private DomainException notFound(String code, String message) {
        return DomainException.notFound(code, message);
    }

    private DomainException conflict(String code, String message) {
        return DomainException.conflict(code, message);
    }
}
