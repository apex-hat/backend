package com.meridian.dashboard;

import com.meridian.auth.AuthenticationException;
import com.meridian.common.exception.DomainException;
import com.meridian.opinion.Opinion;
import com.meridian.opinion.OpinionRepository;
import com.meridian.opinion.OpinionStance;
import com.meridian.proposal.Proposal;
import com.meridian.proposal.ProposalService;
import com.meridian.proposal.ProposalStatus;
import com.meridian.team.Team;
import com.meridian.team.TeamMember;
import com.meridian.team.TeamMemberId;
import com.meridian.team.TeamMemberRepository;
import com.meridian.team.TeamRepository;
import com.meridian.user.User;
import com.meridian.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    private static final String AUTH_HEADER = "Bearer id-token";

    @Mock
    private UserService userService;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private ProposalService proposalService;
    @Mock
    private OpinionRepository opinionRepository;

    private DashboardService dashboardService;

    private User author;
    private User member;
    private Team team;
    private Proposal proposal;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(userService, teamRepository, teamMemberRepository, proposalService, opinionRepository);

        author = User.builder().id(1L).firebaseUid("author-uid").country("KR").timeZone("Asia/Seoul").location("Seoul HQ").build();
        member = User.builder().id(2L).firebaseUid("member-uid").country("US").timeZone("America/New_York").location(null).build();
        team = Team.builder().id(10L).name("Design Team").build();
        proposal = Proposal.builder().id(100L).title("Title").content("Content")
                .author(author).targetTeam(team).status(ProposalStatus.IN_PROGRESS).build();

        lenient().when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(author);
    }

    // ---- timezones ----

    @Test
    void timezonesRejectsMissingBearerToken() {
        when(userService.getCurrentUserEntity(null))
                .thenThrow(new AuthenticationException("Bearer token is required."));

        assertThatThrownBy(() -> dashboardService.timezones(null, 10L))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void timezonesRejectsMissingTeam() {
        when(teamRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.timezones(AUTH_HEADER, 999L))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("TEAM_NOT_FOUND");
    }

    @Test
    void timezonesRejectsNonTeamMember() {
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> dashboardService.timezones(AUTH_HEADER, 10L))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("TEAM_ACCESS_DENIED");
    }

    @Test
    void timezonesReturnsMembersWithComputedLocalTime() {
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(true);
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(List.of(
                teamMember(team, author, "PM"),
                teamMember(team, member, "MEMBER")));

        DashboardTimezonesResponse response = dashboardService.timezones(AUTH_HEADER, 10L);

        assertThat(response.members()).hasSize(2);

        DashboardTimezoneMemberResponse authorEntry = response.members().stream()
                .filter(m -> m.userId().equals(1L)).findFirst().orElseThrow();
        assertThat(authorEntry.country()).isEqualTo("KR");
        assertThat(authorEntry.timeZone()).isEqualTo("Asia/Seoul");
        assertThat(authorEntry.location()).isEqualTo("Seoul HQ");
        String expectedSeoulTime = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Asia/Seoul")).format(Instant.now());
        assertThat(authorEntry.localTime()).isEqualTo(expectedSeoulTime);

        DashboardTimezoneMemberResponse memberEntry = response.members().stream()
                .filter(m -> m.userId().equals(2L)).findFirst().orElseThrow();
        assertThat(memberEntry.country()).isEqualTo("US");
        assertThat(memberEntry.location()).isNull();
    }

    // ---- status ----

    @Test
    void statusRejectsMissingBearerToken() {
        when(userService.getCurrentUserEntity(null))
                .thenThrow(new AuthenticationException("Bearer token is required."));

        assertThatThrownBy(() -> dashboardService.status(null, 100L))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void statusPropagatesProposalVisibilityFailure() {
        when(proposalService.getVisibleProposal(999L, author))
                .thenThrow(DomainException.notFound("PROPOSAL_NOT_FOUND", "Proposal not found."));

        assertThatThrownBy(() -> dashboardService.status(AUTH_HEADER, 999L))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo("PROPOSAL_NOT_FOUND");
    }

    @Test
    void statusReturnsZeroRespondedWhenNoOpinions() {
        when(proposalService.getVisibleProposal(100L, author)).thenReturn(proposal);
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(List.of(
                teamMember(team, author, "PM"), teamMember(team, member, "MEMBER")));
        when(opinionRepository.findAllByProposal_Id(100L)).thenReturn(List.of());

        DashboardStatusResponse response = dashboardService.status(AUTH_HEADER, 100L);

        assertThat(response.totalMembers()).isEqualTo(2);
        assertThat(response.respondedMembers()).isEqualTo(0);
        assertThat(response.responseRate()).isEqualTo(0);
    }

    @Test
    void statusReturnsPartialResponseRate() {
        when(proposalService.getVisibleProposal(100L, author)).thenReturn(proposal);
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(List.of(
                teamMember(team, author, "PM"), teamMember(team, member, "MEMBER")));
        when(opinionRepository.findAllByProposal_Id(100L)).thenReturn(List.of(opinionOf(member)));

        DashboardStatusResponse response = dashboardService.status(AUTH_HEADER, 100L);

        assertThat(response.totalMembers()).isEqualTo(2);
        assertThat(response.respondedMembers()).isEqualTo(1);
        assertThat(response.responseRate()).isEqualTo(50);
    }

    @Test
    void statusReturnsFullResponseRate() {
        when(proposalService.getVisibleProposal(100L, author)).thenReturn(proposal);
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(List.of(
                teamMember(team, author, "PM"), teamMember(team, member, "MEMBER")));
        when(opinionRepository.findAllByProposal_Id(100L)).thenReturn(List.of(opinionOf(author), opinionOf(member)));

        DashboardStatusResponse response = dashboardService.status(AUTH_HEADER, 100L);

        assertThat(response.responseRate()).isEqualTo(100);
    }

    @Test
    void statusReturnsZeroRateWhenNoTeamMembers() {
        when(proposalService.getVisibleProposal(100L, author)).thenReturn(proposal);
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(List.of());
        when(opinionRepository.findAllByProposal_Id(100L)).thenReturn(List.of());

        DashboardStatusResponse response = dashboardService.status(AUTH_HEADER, 100L);

        assertThat(response.totalMembers()).isEqualTo(0);
        assertThat(response.responseRate()).isEqualTo(0);
    }

    @Test
    void statusReturnsProposalStatusValue() {
        proposal.setStatus(ProposalStatus.CONSENSUS_READY);
        when(proposalService.getVisibleProposal(100L, author)).thenReturn(proposal);
        when(teamMemberRepository.findAllByTeam_Id(10L)).thenReturn(List.of(teamMember(team, author, "PM")));
        when(opinionRepository.findAllByProposal_Id(100L)).thenReturn(List.of());

        DashboardStatusResponse response = dashboardService.status(AUTH_HEADER, 100L);

        assertThat(response.status()).isEqualTo(ProposalStatus.CONSENSUS_READY);
        assertThat(response.proposalId()).isEqualTo(100L);
    }

    private TeamMember teamMember(Team team, User user, String role) {
        return TeamMember.builder().id(new TeamMemberId(team.getId(), user.getId())).team(team).user(user).role(role).build();
    }

    private Opinion opinionOf(User user) {
        return Opinion.builder().id(1L).proposal(proposal).user(user).stance(OpinionStance.AGREE).comment("의견").build();
    }
}
