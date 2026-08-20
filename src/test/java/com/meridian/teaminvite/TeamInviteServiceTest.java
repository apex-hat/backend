package com.meridian.teaminvite;

import com.meridian.common.exception.DomainException;
import com.meridian.notification.Notification;
import com.meridian.notification.NotificationRepository;
import com.meridian.notification.NotificationType;
import com.meridian.team.Team;
import com.meridian.team.TeamMemberRepository;
import com.meridian.team.TeamRepository;
import com.meridian.user.User;
import com.meridian.user.UserRepository;
import com.meridian.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamInviteServiceTest {

    private static final String AUTH_HEADER = "Bearer id-token";

    @Mock
    private UserService userService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private TeamInviteRepository teamInviteRepository;
    @Mock
    private NotificationRepository notificationRepository;

    private TeamInviteService teamInviteService;
    private User pm;
    private User invitee;
    private Team team;

    @BeforeEach
    void setUp() {
        teamInviteService = new TeamInviteService(userService, userRepository, teamRepository, teamMemberRepository, teamInviteRepository, notificationRepository);
        pm = User.builder().id(1L).name("PM").build();
        invitee = User.builder().id(2L).name("팀원").friendCode("MER-BBBB").build();
        team = Team.builder().id(10L).name("우리 팀").build();
        lenient().when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(pm);
        lenient().when(teamInviteRepository.save(any(TeamInvite.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void pmSendsInviteAndCreatesNotification() {
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(true);
        when(userRepository.findByFriendCode("MER-BBBB")).thenReturn(Optional.of(invitee));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 2L)).thenReturn(false);
        when(teamInviteRepository.findByTeam_IdAndInvitedUser_Id(10L, 2L)).thenReturn(Optional.empty());

        TeamInviteResponse response = teamInviteService.sendInvite(AUTH_HEADER, 10L, "#mer-bbbb");

        assertThat(response.status()).isEqualTo(TeamInviteStatus.PENDING);
        assertThat(response.invitedUserId()).isEqualTo(2L);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.TEAM_INVITE);
        assertThat(notificationCaptor.getValue().getUser()).isEqualTo(invitee);
    }

    @Test
    void rejectsInviteFromNonPm() {
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(false);

        assertThatThrownBy(() -> teamInviteService.sendInvite(AUTH_HEADER, 10L, "MER-BBBB"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("PM만");
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void rejectsInviteForExistingTeamMember() {
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(true);
        when(userRepository.findByFriendCode("MER-BBBB")).thenReturn(Optional.of(invitee));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 2L)).thenReturn(true);

        assertThatThrownBy(() -> teamInviteService.sendInvite(AUTH_HEADER, 10L, "MER-BBBB"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("이미 이 팀에");
    }

    @Test
    void rejectsDuplicateInvite() {
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(true);
        when(userRepository.findByFriendCode("MER-BBBB")).thenReturn(Optional.of(invitee));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 2L)).thenReturn(false);
        when(teamInviteRepository.findByTeam_IdAndInvitedUser_Id(10L, 2L))
                .thenReturn(Optional.of(TeamInvite.builder().team(team).invitedUser(invitee).invitedBy(pm).status(TeamInviteStatus.PENDING).build()));

        assertThatThrownBy(() -> teamInviteService.sendInvite(AUTH_HEADER, 10L, "MER-BBBB"))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("이미 초대");
    }

    @Test
    void resendsRejectedInviteAndCreatesNotification() {
        when(teamRepository.findById(10L)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(true);
        when(userRepository.findByFriendCode("MER-BBBB")).thenReturn(Optional.of(invitee));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 2L)).thenReturn(false);
        TeamInvite rejected = TeamInvite.builder()
                .id(5L)
                .team(team)
                .invitedUser(invitee)
                .invitedBy(User.builder().id(3L).name("이전 PM").build())
                .status(TeamInviteStatus.REJECTED)
                .respondedAt(Instant.now())
                .build();
        when(teamInviteRepository.findByTeam_IdAndInvitedUser_Id(10L, 2L)).thenReturn(Optional.of(rejected));

        TeamInviteResponse response = teamInviteService.sendInvite(AUTH_HEADER, 10L, "MER-BBBB");

        assertThat(response.status()).isEqualTo(TeamInviteStatus.PENDING);
        assertThat(rejected.getInvitedBy()).isEqualTo(pm);
        assertThat(rejected.getRespondedAt()).isNull();
        verify(teamInviteRepository).save(rejected);

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUser()).isEqualTo(invitee);
        assertThat(notificationCaptor.getValue().getContent()).contains("초대했습니다");
    }

    @Test
    void listsOnlyPendingIncomingInvites() {
        when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(invitee);
        TeamInvite pending = TeamInvite.builder().team(team).invitedUser(invitee).invitedBy(pm).status(TeamInviteStatus.PENDING).build();
        when(teamInviteRepository.findAllByInvitedUser_IdAndStatusOrderByCreatedAtDesc(2L, TeamInviteStatus.PENDING))
                .thenReturn(List.of(pending));

        List<TeamInviteResponse> responses = teamInviteService.listIncoming(AUTH_HEADER);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).teamName()).isEqualTo("우리 팀");
    }

    @Test
    void acceptingInviteCreatesTeamMembershipAndNotifiesInviter() {
        when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(invitee);
        TeamInvite pending = TeamInvite.builder().id(5L).team(team).invitedUser(invitee).invitedBy(pm).status(TeamInviteStatus.PENDING).build();
        when(teamInviteRepository.findById(5L)).thenReturn(Optional.of(pending));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(10L, 2L)).thenReturn(false);

        TeamInviteResponse response = teamInviteService.respond(AUTH_HEADER, 5L, true);

        assertThat(response.status()).isEqualTo(TeamInviteStatus.ACCEPTED);
        assertThat(pending.getRespondedAt()).isNotNull();
        verify(teamMemberRepository).save(any());

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUser()).isEqualTo(pm);
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.TEAM_INVITE);
        assertThat(notificationCaptor.getValue().getContent()).contains("수락");
    }

    @Test
    void rejectingInviteDoesNotCreateTeamMembershipButNotifiesInviter() {
        when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(invitee);
        TeamInvite pending = TeamInvite.builder().id(5L).team(team).invitedUser(invitee).invitedBy(pm).status(TeamInviteStatus.PENDING).build();
        when(teamInviteRepository.findById(5L)).thenReturn(Optional.of(pending));

        TeamInviteResponse response = teamInviteService.respond(AUTH_HEADER, 5L, false);

        assertThat(response.status()).isEqualTo(TeamInviteStatus.REJECTED);
        verify(teamMemberRepository, never()).save(any());

        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUser()).isEqualTo(pm);
        assertThat(notificationCaptor.getValue().getContent()).contains("거절");
    }

    @Test
    void onlyInvitedUserCanRespond() {
        when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(pm);
        TeamInvite pending = TeamInvite.builder().id(5L).team(team).invitedUser(invitee).invitedBy(pm).status(TeamInviteStatus.PENDING).build();
        when(teamInviteRepository.findById(5L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> teamInviteService.respond(AUTH_HEADER, 5L, true))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("본인에게 온 초대");
    }

    @Test
    void listsAllInvitesForTeamWhenCallerIsPm() {
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(true);
        TeamInvite pending = TeamInvite.builder().id(5L).team(team).invitedUser(invitee).invitedBy(pm).status(TeamInviteStatus.PENDING).build();
        when(teamInviteRepository.findAllByTeam_IdOrderByCreatedAtDesc(10L)).thenReturn(List.of(pending));

        List<TeamInviteResponse> responses = teamInviteService.listForTeam(AUTH_HEADER, 10L);

        assertThat(responses).hasSize(1);
    }

    @Test
    void rejectsListForTeamWhenCallerIsNotPm() {
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(false);

        assertThatThrownBy(() -> teamInviteService.listForTeam(AUTH_HEADER, 10L))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("PM만");
    }

    @Test
    void pmCancelsPendingInvite() {
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(true);
        TeamInvite pending = TeamInvite.builder().id(5L).team(team).invitedUser(invitee).invitedBy(pm).status(TeamInviteStatus.PENDING).build();
        when(teamInviteRepository.findById(5L)).thenReturn(Optional.of(pending));

        teamInviteService.cancelInvite(AUTH_HEADER, 10L, 5L);

        verify(teamInviteRepository).delete(pending);
    }

    @Test
    void rejectsCancelForAlreadyResolvedInvite() {
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(true);
        TeamInvite accepted = TeamInvite.builder().id(5L).team(team).invitedUser(invitee).invitedBy(pm).status(TeamInviteStatus.ACCEPTED).build();
        when(teamInviteRepository.findById(5L)).thenReturn(Optional.of(accepted));

        assertThatThrownBy(() -> teamInviteService.cancelInvite(AUTH_HEADER, 10L, 5L))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("이미 처리된");
    }

    @Test
    void pmResendsPendingInviteNotification() {
        when(teamMemberRepository.existsByTeam_IdAndUser_IdAndRole(10L, 1L, "PM")).thenReturn(true);
        TeamInvite pending = TeamInvite.builder().id(5L).team(team).invitedUser(invitee).invitedBy(pm).status(TeamInviteStatus.PENDING).build();
        when(teamInviteRepository.findById(5L)).thenReturn(Optional.of(pending));

        TeamInviteResponse response = teamInviteService.resendInvite(AUTH_HEADER, 10L, 5L);

        assertThat(response.id()).isEqualTo(5L);
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notificationCaptor.capture());
        assertThat(notificationCaptor.getValue().getUser()).isEqualTo(invitee);
        assertThat(notificationCaptor.getValue().getType()).isEqualTo(NotificationType.TEAM_INVITE);
    }

    @Test
    void cannotRespondToAlreadyResolvedInvite() {
        when(userService.getCurrentUserEntity(AUTH_HEADER)).thenReturn(invitee);
        TeamInvite resolved = TeamInvite.builder().id(5L).team(team).invitedUser(invitee).invitedBy(pm).status(TeamInviteStatus.ACCEPTED).build();
        when(teamInviteRepository.findById(5L)).thenReturn(Optional.of(resolved));

        assertThatThrownBy(() -> teamInviteService.respond(AUTH_HEADER, 5L, false))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("이미 처리된");
    }
}
