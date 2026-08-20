package com.meridian.dashboard;

import com.meridian.common.exception.DomainException;
import com.meridian.opinion.OpinionRepository;
import com.meridian.proposal.Proposal;
import com.meridian.proposal.ProposalService;
import com.meridian.team.Team;
import com.meridian.team.TeamMemberRepository;
import com.meridian.team.TeamRepository;
import com.meridian.user.User;
import com.meridian.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * README §10 Dashboard API. 자체 Entity 없이 Team/Proposal/Opinion의 기존 구현을 조합해 조회만 한다.
 * 인증(UserService)과 Proposal 접근 권한(ProposalService.getVisibleProposal)은 기존 구현을 그대로 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final DateTimeFormatter LOCAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final UserService userService;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ProposalService proposalService;
    private final OpinionRepository opinionRepository;

    /**
     * team_members.user는 지연 로딩(LAZY)이라, 트랜잭션 밖에서 User 필드에 접근하면
     * LazyInitializationException("no session")이 난다. 응답 DTO로 변환하기 전까지
     * 세션을 열어두기 위해 조회 전용 트랜잭션으로 감싼다.
     */
    @Transactional(readOnly = true)
    public DashboardTimezonesResponse timezones(String authorizationHeader, Long teamId) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        Team team = findTeam(teamId);
        requireTeamMember(team.getId(), user.getId());

        List<DashboardTimezoneMemberResponse> members = teamMemberRepository.findAllByTeam_Id(team.getId()).stream()
                .map(member -> toMemberResponse(member.getUser()))
                .toList();

        return new DashboardTimezonesResponse(members);
    }

    @Transactional(readOnly = true)
    public DashboardStatusResponse status(String authorizationHeader, Long proposalId) {
        User user = userService.getCurrentUserEntity(authorizationHeader);
        Proposal proposal = proposalService.getVisibleProposal(proposalId, user);

        int totalMembers = teamMemberRepository.findAllByTeam_Id(proposal.getTargetTeam().getId()).size();
        int respondedMembers = opinionRepository.findAllByProposal_Id(proposal.getId()).size();
        int responseRate = totalMembers == 0 ? 0 : Math.round(respondedMembers * 100f / totalMembers);

        return new DashboardStatusResponse(proposal.getId(), totalMembers, respondedMembers, responseRate, proposal.getStatus());
    }

    private Team findTeam(Long teamId) {
        return teamRepository.findById(teamId)
                .orElseThrow(() -> DomainException.notFound("TEAM_NOT_FOUND", "Team not found."));
    }

    private void requireTeamMember(Long teamId, Long userId) {
        if (!teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)) {
            throw DomainException.forbidden("TEAM_ACCESS_DENIED", "Only team members can access this team.");
        }
    }

    /**
     * README §10 "현지 시간" 계산. timeZone이 없는 사용자는(현재 데이터상 발생하지 않아야 하지만 방어적으로)
     * localTime을 null로 둔다 — 임의의 기본 시간대를 지어내지 않는다.
     */
    private DashboardTimezoneMemberResponse toMemberResponse(User member) {
        String localTime = member.getTimeZone() == null
                ? null
                : LOCAL_TIME_FORMATTER.withZone(ZoneId.of(member.getTimeZone())).format(Instant.now());

        return new DashboardTimezoneMemberResponse(
                member.getId(), member.getCountry(), member.getTimeZone(), localTime, member.getLocation());
    }
}
