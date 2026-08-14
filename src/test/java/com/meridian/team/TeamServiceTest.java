package com.meridian.team;

import com.meridian.common.exception.BusinessException;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import com.meridian.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    private static final String AUTHORIZATION = "Bearer id-token";

    @Mock
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @InjectMocks
    private TeamService teamService;

    @Test
    void createsTeamAndRegistersCreatorAsPm() {
        User creator = user(1L, "Creator");
        when(userService.getCurrentUserEntity(AUTHORIZATION)).thenReturn(creator);
        when(teamRepository.save(any(Team.class))).thenAnswer(invocation -> {
            Team team = invocation.getArgument(0);
            team.setId(10L);
            return team;
        });
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamResponse response = teamService.createTeam(AUTHORIZATION,
                new TeamCreateRequest(" Meridian ", " KR ", " ko-KR "));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.name()).isEqualTo("Meridian");
        assertThat(response.country()).isEqualTo("KR");

        ArgumentCaptor<TeamMember> memberCaptor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(memberCaptor.capture());
        TeamMember savedMember = memberCaptor.getValue();
        assertThat(savedMember.getId()).isEqualTo(new TeamMemberId(10L, 1L));
        assertThat(savedMember.getRole()).isEqualTo("PM");
    }

    @Test
    void listsOnlyTeamsJoinedByCurrentUser() {
        User currentUser = user(1L, "Current");
        Team team = team(10L, "Team A");
        when(userService.getCurrentUserEntity(AUTHORIZATION)).thenReturn(currentUser);
        when(teamMemberRepository.findAllByUser_Id(1L))
                .thenReturn(List.of(member(team, currentUser, "MEMBER")));

        List<TeamResponse> response = teamService.listTeams(AUTHORIZATION);

        assertThat(response).extracting(TeamResponse::id).containsExactly(10L);
    }

    @Test
    void rejectsTeamDetailForNonMemberWith403() {
        User currentUser = user(1L, "Current");
        Team team = team(10L, "Team A");
        when(userService.getCurrentUserEntity(AUTHORIZATION)).thenReturn(currentUser);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 1L)).thenReturn(false);

        assertThatThrownBy(() -> teamService.getTeam(AUTHORIZATION, 10L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo("TEAM_ACCESS_DENIED");
                });
    }

    @Test
    void returns404WhenTeamDoesNotExist() {
        User currentUser = user(1L, "Current");
        when(userService.getCurrentUserEntity(AUTHORIZATION)).thenReturn(currentUser);
        when(teamRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeam(AUTHORIZATION, 404L))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(ex.getCode()).isEqualTo("TEAM_NOT_FOUND");
                });
    }

    @Test
    void addsMemberWhenCurrentUserIsPm() {
        User pm = user(1L, "PM");
        User target = user(2L, "Member");
        Team team = team(10L, "Team A");
        when(userService.getCurrentUserEntity(AUTHORIZATION)).thenReturn(pm);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 2L)).thenReturn(false);
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TeamMemberResponse response = teamService.addMember(AUTHORIZATION, 10L,
                new TeamMemberAddRequest(2L, "member"));

        assertThat(response.userId()).isEqualTo(2L);
        assertThat(response.role()).isEqualTo("MEMBER");
    }

    @Test
    void rejectsMemberAddWhenCurrentUserIsNotPmWith403() {
        User currentUser = user(1L, "Current");
        Team team = team(10L, "Team A");
        when(userService.getCurrentUserEntity(AUTHORIZATION)).thenReturn(currentUser);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(false);

        assertThatThrownBy(() -> teamService.addMember(AUTHORIZATION, 10L,
                new TeamMemberAddRequest(2L, "MEMBER")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getCode()).isEqualTo("TEAM_PM_REQUIRED");
                });
    }

    @Test
    void rejectsDuplicateMemberAddWith409() {
        User pm = user(1L, "PM");
        User target = user(2L, "Member");
        Team team = team(10L, "Team A");
        when(userService.getCurrentUserEntity(AUTHORIZATION)).thenReturn(pm);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> teamService.addMember(AUTHORIZATION, 10L,
                new TeamMemberAddRequest(2L, "MEMBER")))
                .isInstanceOfSatisfying(BusinessException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo("TEAM_MEMBER_ALREADY_EXISTS");
                });
    }

    @Test
    void removesMemberWhenCurrentUserIsPm() {
        User pm = user(1L, "PM");
        User target = user(2L, "Member");
        Team team = team(10L, "Team A");
        TeamMember member = member(team, target, "MEMBER");
        when(userService.getCurrentUserEntity(AUTHORIZATION)).thenReturn(pm);
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(true);
        when(teamMemberRepository.findByTeam_IdAndUser_Id(10L, 2L)).thenReturn(Optional.of(member));

        teamService.removeMember(AUTHORIZATION, 10L, 2L);

        verify(teamMemberRepository).delete(member);
    }

    private User user(Long id, String name) {
        return User.builder()
                .id(id)
                .firebaseUid("firebase-" + id)
                .name(name)
                .email("user" + id + "@example.com")
                .timeZone("UTC")
                .build();
    }

    private Team team(Long id, String name) {
        return Team.builder()
                .id(id)
                .name(name)
                .country("KR")
                .cultureTag("ko-KR")
                .build();
    }

    private TeamMember member(Team team, User user, String role) {
        return TeamMember.builder()
                .id(new TeamMemberId(team.getId(), user.getId()))
                .team(team)
                .user(user)
                .role(role)
                .build();
    }
}
